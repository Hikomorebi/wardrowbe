package site.hikomorebi.wardrobe.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

private class MemoryStorage(override var server: String) : SessionStorage {
    var stored: StoredSession? = null

    override fun read() = stored

    override fun write(session: StoredSession?) {
        stored = session
    }
}

class SessionManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: MemoryStorage
    private lateinit var manager: SessionManager
    private var now = 1_800_000_000_000L
    private val user = """{"id":"user-1","email":"test@example.com","display_name":"测试"}"""

    private fun tokens(
        access: String = "access-1",
        refresh: String = "a".repeat(64),
        lifetime: Long = 3600,
    ) =
        """{"access_token":"$access","refresh_token":"$refresh","expires_in":$lifetime,"session_expires_at":"2030-01-01T00:00:00Z","user":$user}"""

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        storage = MemoryStorage(server.url("/").toString())
        manager = SessionManager(storage, allowDebugHttp = true, clock = { now })
    }

    @After
    fun cleanup() {
        server.shutdown()
    }

    @Test
    fun `login persists tokens and cold start restores session`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens()))
        manager.login("test@example.com", "test-password")
        assertEquals("access-1", storage.stored!!.accessToken)
        assertEquals(storage.server, storage.stored!!.server)
        val restored = SessionManager(storage, true, clock = { now })
        assertEquals("access-1", restored.accessToken())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `concurrent rejected requests share one rotation`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens()))
        manager.login("test@example.com", "test-password")
        server.enqueue(MockResponse().setBody(tokens("access-2", "b".repeat(64))))
        val values =
            withTimeout(10_000) {
                (1..10)
                    .map { async(Dispatchers.Default) { manager.accessToken("access-1") } }
                    .awaitAll()
            }
        assertTrue(values.all { it == "access-2" })
        assertEquals(2, server.requestCount)
        assertEquals("b".repeat(64), storage.stored!!.refreshToken)
    }

    @Test
    fun `expiring token proactively refreshes without extending deadline`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens(lifetime = 61)))
        manager.login("test@example.com", "test-password")
        val deadline = manager.session.value!!.sessionExpiresAt
        now += 2000
        server.enqueue(MockResponse().setBody(tokens("access-2", "b".repeat(64))))
        assertEquals("access-2", manager.accessToken())
        assertEquals(deadline, manager.session.value!!.sessionExpiresAt)
    }

    @Test
    fun `refresh rejection clears persisted session`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens(lifetime = 1)))
        manager.login("test@example.com", "test-password")
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            manager.accessToken()
            fail("Expected expiry")
        } catch (_: SessionExpiredException) {}
        assertNull(manager.session.value)
        assertNull(storage.stored)
    }

    @Test
    fun `network failure preserves refresh token and never retries POST`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens(lifetime = 1)))
        manager.login("test@example.com", "test-password")
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            manager.accessToken()
            fail("Expected I/O failure")
        } catch (_: IOException) {}
        assertNotNull(manager.session.value)
        assertEquals("a".repeat(64), storage.stored!!.refreshToken)
        assertTrue(server.requestCount <= 2)
    }

    @Test
    fun `temporary server failure retains session`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens(lifetime = 1)))
        manager.login("test@example.com", "test-password")
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            manager.accessToken()
            fail("Expected 503")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
        assertNotNull(storage.stored)
    }

    @Test
    fun `logout revokes before clearing local credentials`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens()))
        manager.login("test@example.com", "test-password")
        server.enqueue(MockResponse().setResponseCode(204))
        manager.logout()
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/mobile/logout", request.path)
        assertTrue(request.body.readUtf8().contains("refresh_token"))
        assertNull(storage.stored)
        assertNull(manager.session.value)
    }

    @Test
    fun `server change clears credentials before new origin is used`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens()))
        manager.login("test@example.com", "test-password")
        manager.setServer("https://other.example.com")
        assertNull(manager.session.value)
        assertNull(storage.stored)
        assertEquals("https://other.example.com/", manager.server.value)
        try {
            WardrobeRepository(manager).profile()
            fail("Expected login requirement")
        } catch (_: SessionExpiredException) {}
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `stored credentials cannot be restored under another origin`() = runBlocking {
        server.enqueue(MockResponse().setBody(tokens()))
        manager.login("test@example.com", "test-password")
        storage.server = "https://other.example.com/"
        assertNull(SessionManager(storage).session.value)
    }

    @Test
    fun `protected concurrent 401s rotate once without dispatcher deadlock`() = runBlocking {
        val refreshes = AtomicInteger()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/api/v1/auth/mobile/login" -> MockResponse().setBody(tokens())
                        "/api/v1/auth/mobile/refresh" -> {
                            refreshes.incrementAndGet()
                            MockResponse().setBody(tokens("access-2", "b".repeat(64)))
                        }
                        "/api/v1/users/me" ->
                            if (request.getHeader("Authorization") == "Bearer access-2")
                                MockResponse().setBody(user)
                            else MockResponse().setResponseCode(401)
                        else -> MockResponse().setResponseCode(404)
                    }
            }
        manager.login("test@example.com", "test-password")
        val repo = WardrobeRepository(manager)
        val profiles =
            withTimeout(15_000) {
                (1..6).map { async(Dispatchers.IO) { repo.profile() } }.awaitAll()
            }
        assertTrue(profiles.all { it.id == "user-1" })
        assertEquals(1, refreshes.get())
    }

    @Test
    fun `login redirect cannot forward password to another origin`() = runBlocking {
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(
                MockResponse().setResponseCode(307).addHeader("Location", other.url("/steal")),
            )
            try {
                manager.login("test@example.com", "test-password")
                fail("Expected redirect refusal")
            } catch (error: HttpException) {
                assertEquals(307, error.code())
            }
            assertEquals(0, other.requestCount)
            assertNull(storage.stored)
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun `malformed login response cannot persist broken session`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"access_token":"access","expires_in":3600}"""))
        try {
            manager.login("test@example.com", "test-password")
            fail("Expected incomplete response")
        } catch (_: IllegalArgumentException) {}
        assertNull(storage.stored)
        assertNull(manager.session.value)
    }
}
