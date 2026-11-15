package site.hikomorebi.wardrobe.data

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class WardrobeRepository(val auth: SessionManager) {
    private var cachedServer: String? = null
    private var cachedApi: WardrobeApi? = null

    private fun api(): WardrobeApi =
        synchronized(this) {
            val server = auth.server.value
            if (cachedServer != server) {
                // Separate dispatcher from the refresh client: simultaneous 401s cannot starve
                // refresh.
                val client =
                    baseHttpClient()
                        .addInterceptor { chain ->
                            if (auth.server.value != server)
                                throw java.io.IOException("服务器地址已更改，请重新操作")
                            val token =
                                auth.session.value?.takeIf { it.server == server }?.accessToken
                            val request = chain.request().newBuilder()
                            if (token != null) request.header("Authorization", "Bearer $token")
                            chain.proceed(request.build())
                        }
                        .authenticator(
                            Authenticator { _, response ->
                                if (response.priorResponse != null || auth.server.value != server)
                                    return@Authenticator null
                                val old =
                                    response.request
                                        .header("Authorization")
                                        ?.removePrefix("Bearer ") ?: return@Authenticator null
                                try {
                                    val renewed = runBlocking { auth.accessToken(old) }
                                    if (auth.server.value != server) return@Authenticator null
                                    response.request
                                        .newBuilder()
                                        .header("Authorization", "Bearer $renewed")
                                        .build()
                                } catch (_: SessionExpiredException) {
                                    null
                                }
                            },
                        )
                        .build()
                cachedApi = createApi(server, client)
                cachedServer = server
            }
            cachedApi!!
        }

    private suspend fun <T> authorized(block: suspend (WardrobeApi) -> T): T {
        auth.accessToken()
        return block(api())
    }

    fun imageUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val base = auth.server.value.toHttpUrl()
        val target = base.resolve(path) ?: return null
        // Never attach credentials to an image served by an unexpected origin.
        if (target.scheme != base.scheme || target.host != base.host || target.port != base.port)
            return null
        return target.toString()
    }

    suspend fun profile() = authorized { it.profile() }

    suspend fun items(filters: ItemFilters, page: Int = 1) = authorized {
        it.items(filters.query(page))
    }

    suspend fun pickerItems(search: String, page: Int = 1) = authorized {
        it.items(ItemFilters(search = search).query(page) + ("status" to "ready"))
    }

    suspend fun item(id: String) = authorized { it.item(id) }

    suspend fun categories() = authorized { it.categories() }

    suspend fun locations() = authorized { it.locations() }

    suspend fun saveItem(id: String?, data: ItemWrite, photo: File?): ClothingItem =
        authorized { api ->
            fun part(file: File) =
                MultipartBody.Part.createFormData(
                    "image",
                    "clothing.jpg",
                    file.asRequestBody("image/jpeg".toMediaType()),
                )
            if (id == null) {
                requireNotNull(photo) { "请拍照或选择一张衣物图片" }
                val fields = buildList {
                    add(MultipartBody.Part.createFormData("skip_ai", "true"))
                    data.name?.let { add(MultipartBody.Part.createFormData("name", it)) }
                    data.categoryId?.let {
                        add(MultipartBody.Part.createFormData("category_id", it))
                    }
                    data.condition?.let { add(MultipartBody.Part.createFormData("condition", it)) }
                    data.storageLocationId?.let {
                        add(MultipartBody.Part.createFormData("storage_location_id", it))
                    }
                    data.notes?.let { add(MultipartBody.Part.createFormData("notes", it)) }
                    data.season.forEach { add(MultipartBody.Part.createFormData("season", it)) }
                }
                api.createItem(part(photo), fields)
            } else {
                val item = api.updateItem(id, data)
                if (photo == null) item
                else
                    try {
                        api.replaceImage(id, part(photo))
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        throw PartialSaveException(error)
                    }
            }
        }

    suspend fun deleteItem(id: String) = authorized { it.deleteItem(id) }

    suspend fun archive(id: String, restore: Boolean) = authorized {
        if (restore) it.restoreItem(id) else it.archiveItem(id)
    }

    suspend fun saveEntry(category: Boolean, id: String?, name: String) = authorized {
        val body = NameBody(name.trim())
        if (category) {
            if (id == null) it.createCategory(body) else it.updateCategory(id, body)
        } else {
            if (id == null) it.createLocation(body) else it.updateLocation(id, body)
        }
    }

    suspend fun deleteEntry(category: Boolean, id: String) = authorized {
        if (category) it.deleteCategory(id) else it.deleteLocation(id)
    }

    suspend fun outfits(page: Int = 1, sourceId: String? = null) = authorized {
        it.outfits(
            buildMap {
                put("source", "manual")
                put("is_lookbook", "true")
                put("page", page.toString())
                put("page_size", "20")
                sourceId?.let { source -> put("item_id", source) }
            },
        )
    }

    suspend fun outfit(id: String) = authorized { it.outfit(id) }

    suspend fun saveOutfit(id: String?, name: String, items: List<String>) = authorized {
        if (id == null) it.createOutfit(OutfitCreate(name.trim(), items))
        else it.updateOutfit(id, OutfitPatch(name.trim(), items))
    }

    suspend fun deleteOutfit(id: String) = authorized { it.deleteOutfit(id) }

    suspend fun changePassword(old: String, new: String) {
        authorized { it.changePassword(PasswordBody(old, new)) }
        auth.clear()
    }
}

class PartialSaveException(cause: Throwable) : Exception("文字信息已保存，但图片替换失败。请保留当前页面并重试保存。", cause)
