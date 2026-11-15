package site.hikomorebi.wardrobe.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import site.hikomorebi.wardrobe.WardrobeApplication
import site.hikomorebi.wardrobe.data.*

data class WardrobeState(
    val items: List<ClothingItem> = emptyList(),
    val filters: ItemFilters = ItemFilters(),
    val page: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as WardrobeApplication).repository
    val auth = repo.auth
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val messages = _messages.asSharedFlow()
    private val _categories = MutableStateFlow<List<NamedEntry>>(emptyList())
    val categories = _categories.asStateFlow()
    private val _locations = MutableStateFlow<List<NamedEntry>>(emptyList())
    val locations = _locations.asStateFlow()
    private val _profile = MutableStateFlow<UserInfo?>(null)
    val profileInfo = _profile.asStateFlow()
    private val _wardrobe = MutableStateFlow(WardrobeState())
    val wardrobe = _wardrobe.asStateFlow()
    private var itemsJob: Job? = null

    init {
        viewModelScope.launch {
            auth.session
                .map { it?.user?.id }
                .distinctUntilChanged()
                .collectLatest { id ->
                    if (id == null) {
                        itemsJob?.cancel()
                        _wardrobe.value = WardrobeState()
                        _categories.value = emptyList()
                        _locations.value = emptyList()
                        _profile.value = null
                    } else refreshCatalogs()
                }
        }
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    fun refresh() {
        _revision.update { it + 1 }
        if (auth.session.value != null) viewModelScope.launch { refreshCatalogs() }
    }

    private suspend fun refreshCatalogs() {
        try {
            coroutineScope {
                val c = async { repo.categories() }
                val l = async { repo.locations() }
                val p = async { repo.profile() }
                _categories.value = c.await()
                _locations.value = l.await()
                _profile.value = p.await()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            message(errorText(error))
        }
    }

    fun setFilters(filters: ItemFilters, debounce: Boolean = false) {
        _wardrobe.update { it.copy(filters = filters) }
        loadItems(reset = true, debounce = debounce)
    }

    fun loadItems(reset: Boolean = true, debounce: Boolean = false) {
        if (!reset && (_wardrobe.value.loading || !_wardrobe.value.hasMore)) return
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            if (debounce) delay(300)
            val state = _wardrobe.value
            val page = if (reset) 1 else state.page + 1
            _wardrobe.update { it.copy(loading = true, error = null) }
            try {
                val result = repo.items(state.filters, page)
                _wardrobe.update {
                    it.copy(
                        items =
                            if (reset) result.items
                            else (state.items + result.items).distinctBy { row -> row.id },
                        page = page,
                        total = result.total,
                        hasMore = result.hasMore,
                        loading = false,
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _wardrobe.update { it.copy(loading = false, error = errorText(error)) }
            }
        }
    }

    fun <T> submit(
        onSuccess: (T) -> Unit = {},
        onError: ((String) -> Unit)? = null,
        work: suspend () -> T,
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val result = work()
                _revision.update { it + 1 }
                _busy.value = false
                onSuccess(result)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val text = errorText(error)
                if (onError != null) onError(text) else message(text)
            } finally {
                _busy.value = false
            }
        }
    }

    fun imageUrl(path: String?) = repo.imageUrl(path)

    suspend fun item(id: String) = repo.item(id)

    suspend fun outfit(id: String) = repo.outfit(id)

    suspend fun items(filters: ItemFilters, page: Int = 1) = repo.items(filters, page)

    suspend fun pickerItems(search: String, page: Int = 1) = repo.pickerItems(search, page)

    suspend fun entries(category: Boolean) = if (category) repo.categories() else repo.locations()

    suspend fun profile() = repo.profile()

    suspend fun outfits(page: Int = 1, sourceId: String? = null) = repo.outfits(page, sourceId)

    suspend fun saveItem(id: String?, data: ItemWrite, photo: File?) =
        repo.saveItem(id, data, photo)

    suspend fun deleteItem(id: String) = repo.deleteItem(id)

    suspend fun archive(id: String, restore: Boolean) = repo.archive(id, restore)

    suspend fun saveEntry(category: Boolean, id: String?, name: String): NamedEntry {
        val result = repo.saveEntry(category, id, name)
        refreshCatalogs()
        return result
    }

    suspend fun deleteEntry(category: Boolean, id: String) {
        repo.deleteEntry(category, id)
        refreshCatalogs()
    }

    suspend fun saveOutfit(id: String?, name: String, ids: List<String>) =
        repo.saveOutfit(id, name, ids)

    suspend fun deleteOutfit(id: String) = repo.deleteOutfit(id)

    suspend fun changePassword(old: String, new: String) = repo.changePassword(old, new)
}

fun errorText(error: Throwable): String =
    when (error) {
        is SessionExpiredException -> error.message!!
        is PartialSaveException -> error.message!!
        is SSLException -> "HTTPS 证书校验失败，请检查服务器地址与证书"
        is HttpException -> {
            val path = error.response()?.raw()?.request?.url?.encodedPath.orEmpty()
            val method = error.response()?.raw()?.request?.method.orEmpty()
            when (error.code()) {
                401 ->
                    if (path.endsWith("/password")) "当前密码不正确"
                    else if (path.endsWith("/login")) "邮箱或密码不正确" else "登录已失效，请重新登录"
                403 -> "当前账号无法执行此操作"
                404 ->
                    if (path.contains("/auth/mobile")) "服务器尚未启用手机登录，请先更新后端"
                    else "记录不存在，可能已在其他设备删除，请刷新页面"
                409 ->
                    when {
                        path.contains("/categories") ->
                            if (method == "DELETE") "该类别仍被衣物使用（包括归档衣物），请先调整衣物类别" else "已存在同名类别"
                        path.contains("/locations") ->
                            if (method == "DELETE") "该位置仍有衣物（包括归档衣物），请先移动衣物" else "已存在同名位置"
                        path.contains("/items") && method == "DELETE" -> "该衣物已用于保存的搭配，请先移出搭配，或选择归档"
                        path.endsWith("/items") && method == "POST" -> "这张图片对应的衣物已存在，请先检查衣橱"
                        else -> "数据已发生变化，请刷新后重试"
                    }
                413 -> "图片过大，请选择较小的图片"
                422 -> "填写的信息不符合要求，请检查后重试"
                429 -> "操作过于频繁，请稍后重试"
                in 500..599 -> "服务器暂时不可用，请稍后重试；备案期间公开入口尚未开放"
                else -> "操作未完成，请检查填写内容后重试"
            }
        }
        is IllegalArgumentException -> error.message ?: "填写的信息不正确"
        is IOException -> "无法连接服务器，请检查网络和服务器地址后重试"
        else -> "操作未完成，请稍后重试"
    }
