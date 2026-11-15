package site.hikomorebi.wardrobe.data

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface WardrobeApi {
    @POST("api/v1/auth/mobile/login") suspend fun login(@Body body: LoginBody): MobileTokens

    @POST("api/v1/auth/mobile/refresh") suspend fun refresh(@Body body: RefreshBody): MobileTokens

    @POST("api/v1/auth/mobile/logout") suspend fun logout(@Body body: RefreshBody)

    @POST("api/v1/auth/password") suspend fun changePassword(@Body body: PasswordBody)

    @GET("api/v1/users/me") suspend fun profile(): UserInfo

    @GET("api/v1/items") suspend fun items(@QueryMap query: Map<String, String>): ItemsPage

    @GET("api/v1/items/{id}") suspend fun item(@Path("id") id: String): ClothingItem

    @Multipart
    @POST("api/v1/items")
    suspend fun createItem(
        @Part image: MultipartBody.Part,
        @Part parts: List<MultipartBody.Part>,
    ): ClothingItem

    @PATCH("api/v1/items/{id}")
    suspend fun updateItem(@Path("id") id: String, @Body body: ItemWrite): ClothingItem

    @Multipart
    @PUT("api/v1/items/{id}/image")
    suspend fun replaceImage(
        @Path("id") id: String,
        @Part image: MultipartBody.Part,
    ): ClothingItem

    @DELETE("api/v1/items/{id}") suspend fun deleteItem(@Path("id") id: String)

    @POST("api/v1/items/{id}/archive")
    suspend fun archiveItem(
        @Path("id") id: String,
        @Body body: Map<String, String> = emptyMap(),
    ): ClothingItem

    @POST("api/v1/items/{id}/restore") suspend fun restoreItem(@Path("id") id: String): ClothingItem

    @GET("api/v1/categories") suspend fun categories(): List<NamedEntry>

    @POST("api/v1/categories") suspend fun createCategory(@Body body: NameBody): NamedEntry

    @PATCH("api/v1/categories/{id}")
    suspend fun updateCategory(@Path("id") id: String, @Body body: NameBody): NamedEntry

    @DELETE("api/v1/categories/{id}") suspend fun deleteCategory(@Path("id") id: String)

    @GET("api/v1/locations") suspend fun locations(): List<NamedEntry>

    @POST("api/v1/locations") suspend fun createLocation(@Body body: NameBody): NamedEntry

    @PATCH("api/v1/locations/{id}")
    suspend fun updateLocation(@Path("id") id: String, @Body body: NameBody): NamedEntry

    @DELETE("api/v1/locations/{id}") suspend fun deleteLocation(@Path("id") id: String)

    @GET("api/v1/outfits") suspend fun outfits(@QueryMap query: Map<String, String>): OutfitsPage

    @GET("api/v1/outfits/{id}") suspend fun outfit(@Path("id") id: String): Outfit

    @POST("api/v1/outfits/studio") suspend fun createOutfit(@Body body: OutfitCreate): Outfit

    @PATCH("api/v1/outfits/{id}")
    suspend fun updateOutfit(@Path("id") id: String, @Body body: OutfitPatch): Outfit

    @DELETE("api/v1/outfits/{id}") suspend fun deleteOutfit(@Path("id") id: String)
}

fun normalizedServer(input: String, allowDebugHttp: Boolean = false): String {
    val url = input.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入完整的 HTTPS 服务器地址")
    val debugLocal =
        allowDebugHttp &&
            url.scheme == "http" &&
            url.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
    require(url.isHttps || debugLocal) { "正式连接需要 HTTPS 地址" }
    require(
        url.username.isEmpty() &&
            url.password.isEmpty() &&
            url.encodedPath == "/" &&
            url.query == null &&
            url.fragment == null,
    ) {
        "服务器地址只需填写协议、主机和端口，不包含路径或账号"
    }
    return url.toString()
}

fun baseHttpClient(): OkHttpClient.Builder =
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)

fun createApi(server: String, client: OkHttpClient = baseHttpClient().build()): WardrobeApi =
    Retrofit.Builder()
        .baseUrl(server)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(apiJson))
        .build()
        .create(WardrobeApi::class.java)
