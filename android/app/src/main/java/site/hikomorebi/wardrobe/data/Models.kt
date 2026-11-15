package site.hikomorebi.wardrobe.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder

val apiJson =
    GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .serializeNulls()
        .create()

data class UserInfo(val id: String, val email: String, val displayName: String)

data class MobileTokens(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: Long,
    val sessionExpiresAt: String?,
    val user: UserInfo?,
)

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val sessionExpiresAt: String,
    val user: UserInfo,
    val server: String,
)

data class LoginBody(val email: String, val password: String)

data class RefreshBody(val refreshToken: String)

data class PasswordBody(val currentPassword: String, val newPassword: String)

data class NamedEntry(val id: String, val name: String)

data class NameBody(val name: String)

data class ClothingItem(
    val id: String,
    val name: String? = null,
    val type: String = "unknown",
    val categoryId: String? = null,
    val categoryName: String? = null,
    val condition: String? = null,
    val storageLocationId: String? = null,
    val season: List<String> = emptyList(),
    val notes: String? = null,
    val imageUrl: String = "",
    val thumbnailUrl: String? = null,
    val mediumUrl: String? = null,
    val isArchived: Boolean = false,
    val status: String = "ready",
) {
    val title: String
        get() = name?.takeIf { it.isNotBlank() } ?: categoryName ?: "未命名衣物"
}

data class ItemsPage(val items: List<ClothingItem>, val total: Int, val hasMore: Boolean)

data class OutfitItem(
    val id: String,
    val name: String?,
    val categoryName: String?,
    val imageUrl: String?,
    val thumbnailUrl: String?,
) {
    val title: String
        get() = name?.takeIf { it.isNotBlank() } ?: categoryName ?: "未命名衣物"
}

data class Outfit(val id: String, val name: String?, val items: List<OutfitItem>) {
    val title: String
        get() = name?.takeIf { it.isNotBlank() } ?: "未命名搭配"

    fun companions(sourceId: String): List<OutfitItem> = items.filterNot { it.id == sourceId }
}

data class OutfitsPage(val outfits: List<Outfit>, val total: Int, val hasMore: Boolean)

data class ItemWrite(
    val name: String?,
    val categoryId: String?,
    val condition: String?,
    val storageLocationId: String?,
    val season: List<String>,
    val notes: String?,
)

data class OutfitCreate(
    val name: String,
    val items: List<String>,
    val occasion: String = "casual",
    val markWorn: Boolean = false,
)

data class OutfitPatch(val name: String, val items: List<String>)

data class ItemFilters(
    val search: String = "",
    val category: String? = null,
    val condition: String? = null,
    val location: String? = null,
    val season: String? = null,
    val archived: Boolean = false,
) {
    fun query(page: Int = 1) = buildMap {
        put("page", page.toString())
        put("page_size", "20")
        put("is_archived", archived.toString())
        if (search.isNotBlank()) put("search", search.trim())
        category?.let { put("category_id", it) }
        condition?.let { put("condition", it) }
        location?.let { put("storage_location_id", it) }
        season?.let { put("season", it) }
    }
}

val seasons = linkedMapOf("spring" to "春季", "summer" to "夏季", "fall" to "秋季", "winter" to "冬季")
// Keep the personal app's condition names consistent with the web client.
val conditions = linkedMapOf("excellent" to "优秀", "good" to "良好", "fair" to "一般", "poor" to "较差")

fun orderedSeasons(values: List<String>): List<String> =
    if ("all-season" in values) seasons.keys.toList() else seasons.keys.filter { it in values }
