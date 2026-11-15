package site.hikomorebi.wardrobe.data

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

class SessionExpiredException : IOException("登录已过期，请重新登录")

class SessionManager(
    private val storage: SessionStorage,
    private val allowDebugHttp: Boolean = false,
    private val apiProvider: (String) -> WardrobeApi = { createApi(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _session = MutableStateFlow(storage.read()?.takeIf { it.server == storage.server })
    val session = _session.asStateFlow()
    private val _server = MutableStateFlow(storage.server)
    val server = _server.asStateFlow()

    private fun persist(tokens: MobileTokens): StoredSession {
        val malformed = "服务器返回的登录信息不完整，请检查服务器地址"
        val access = requireNotNull(tokens.accessToken) { malformed }
        val refresh = requireNotNull(tokens.refreshToken) { malformed }
        val deadline = requireNotNull(tokens.sessionExpiresAt) { malformed }
        val user = requireNotNull(tokens.user) { malformed }
        require(
            access.isNotBlank() &&
                refresh.length in 32..128 &&
                tokens.expiresIn in 1..3600 &&
                deadline.isNotBlank() &&
                !user.id.isNullOrBlank() &&
                !user.email.isNullOrBlank(),
        ) {
            malformed
        }
        val stored =
            StoredSession(
                access,
                refresh,
                clock() + tokens.expiresIn * 1000,
                deadline,
                user,
                _server.value,
            )
        // The rotated secret must reach durable storage before it is published to other requests.
        storage.write(stored)
        _session.value = stored
        return stored
    }

    suspend fun setServer(input: String) = mutex.withLock {
        val value = normalizedServer(input, allowDebugHttp)
        if (value != _server.value) {
            storage.write(null)
            _session.value = null
            storage.server = value
            _server.value = value
        }
    }

    suspend fun login(email: String, password: String) = mutex.withLock {
        persist(apiProvider(_server.value).login(LoginBody(email.trim(), password)))
    }

    suspend fun accessToken(rejectedToken: String? = null): String = mutex.withLock {
        val current = _session.value ?: throw SessionExpiredException()
        if (rejectedToken != null && rejectedToken != current.accessToken)
            return@withLock current.accessToken
        if (rejectedToken == null && current.accessExpiresAt - clock() > 60_000)
            return@withLock current.accessToken
        try {
            persist(apiProvider(_server.value).refresh(RefreshBody(current.refreshToken)))
                .accessToken
        } catch (error: HttpException) {
            if (error.code() == 401 || error.code() == 404) {
                storage.write(null)
                _session.value = null
                throw SessionExpiredException()
            }
            throw error
        }
        // Network errors leave the encrypted secret intact. Never retry a rotating POST blindly.
    }

    suspend fun logout() = mutex.withLock {
        _session.value?.let { apiProvider(_server.value).logout(RefreshBody(it.refreshToken)) }
        storage.write(null)
        _session.value = null
    }

    suspend fun clear() = mutex.withLock {
        storage.write(null)
        _session.value = null
    }
}
