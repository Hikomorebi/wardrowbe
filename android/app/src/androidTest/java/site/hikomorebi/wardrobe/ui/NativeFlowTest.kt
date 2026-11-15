package site.hikomorebi.wardrobe.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import site.hikomorebi.wardrobe.MainActivity
import site.hikomorebi.wardrobe.WardrobeApplication

/** Opt-in integration test. Only an isolated API forwarded to emulator loopback is accepted. */
class NativeFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private var base = ""
    private var token = ""
    private val itemIds = mutableListOf<String>()
    private var categoryId: String? = null
    private var locationId: String? = null

    private fun call(method: String, path: String, body: RequestBody? = null): String {
        val builder = Request.Builder().url(base + path).method(method, body)
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
        return http.newCall(builder.build()).execute().use {
            check(it.isSuccessful) { "Isolated API $method $path returned ${it.code}" }
            it.body?.string().orEmpty()
        }
    }

    private fun json(text: String) = text.toRequestBody("application/json".toMediaType())

    private fun id(text: String) = JsonParser.parseString(text).asJsonObject["id"].asString

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(90_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun fill(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(value)
    }

    private fun scrollClick(text: String) {
        compose.waitUntil(90_000) {
            compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        click(text)
    }

    private fun awaitListText(text: String) {
        compose.waitUntil(90_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(90_000) {
            compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        awaitText(text)
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder =
            File(instrumentation.targetContext.cacheDir, "test-screenshots").apply { mkdirs() }
        requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap ->
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun seedItem(name: String, circle: Boolean): String {
        val image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.WHITE)
            val paint =
                Paint().apply {
                    color = if (circle) Color.rgb(210, 150, 50) else Color.rgb(60, 100, 190)
                }
            if (circle) drawCircle(100f, 75f, 45f, paint) else drawRect(20f, 20f, 60f, 135f, paint)
        }
        val bytes =
            ByteArrayOutputStream()
                .also { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                .toByteArray()
        image.recycle()
        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "test.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType()),
                )
                .addFormDataPart("name", name)
                .addFormDataPart("category_id", categoryId!!)
                .addFormDataPart("storage_location_id", locationId!!)
                .addFormDataPart("condition", "good")
                .addFormDataPart("season", "summer")
                .addFormDataPart("season", "spring")
                .addFormDataPart("skip_ai", "true")
                .build()
        return id(call("POST", "/api/v1/items", body)).also { itemIds += it }
    }

    @Test
    fun wardrobeAndSavedCompanionsWorkAgainstIsolatedBackend() {
        val argument = InstrumentationRegistry.getArguments().getString("isolatedApi")
        assumeTrue(
            "Pass -e isolatedApi http://127.0.0.1:18082 to run isolated integration",
            argument != null,
        )
        require(argument!!.startsWith("http://127.0.0.1:")) { "Only emulator loopback is allowed" }
        base = argument.trimEnd('/')
        val login =
            JsonParser.parseString(
                    call(
                        "POST",
                        "/api/v1/auth/mobile/login",
                        json(
                            """{"email":"android-e2e@example.com","password":"android-e2e-test-password"}""",
                        ),
                    ),
                )
                .asJsonObject
        token = login["access_token"].asString
        check(
            JsonParser.parseString(call("GET", "/api/v1/items")).asJsonObject["total"].asInt == 0,
        ) {
            "Use a fresh, isolated account"
        }
        categoryId = id(call("POST", "/api/v1/categories", json("""{"name":"安卓验收类别"}""")))
        locationId = id(call("POST", "/api/v1/locations", json("""{"name":"测试衣柜"}""")))
        val source = seedItem("安卓蓝上衣", false)
        val mate = seedItem("安卓米色短裤", true)
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as WardrobeApplication
        runBlocking {
            app.auth.clear()
            app.auth.setServer(base)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("服务器地址")
        fill("邮箱", "android-e2e@example.com")
        fill("密码", "android-e2e-test-password")
        click("登录")
        awaitText("安卓蓝上衣")
        screenshot("wardrobe")
        click("安卓蓝上衣")
        awaitText("衣物详情")
        click("编辑")
        awaitText("编辑衣物")
        fill("衣物名称", "安卓蓝上衣更新")
        compose.onNode(hasSetTextAction() and hasText("备注")).performScrollTo()
        fill("备注", "安卓原生页面保存")
        compose.onNodeWithText("保存衣物").performScrollTo()
        click("保存衣物")
        awaitText("衣物详情")
        awaitText("安卓蓝上衣更新")
        val savedItem = JsonParser.parseString(call("GET", "/api/v1/items/$source")).asJsonObject
        assertEquals("安卓原生页面保存", savedItem["notes"].asString)
        compose.onNodeWithContentDescription("返回").performClick()
        click("我的搭配")
        awaitText("还没有保存的搭配")
        compose.onNodeWithContentDescription("创建搭配").performClick()
        awaitText("搭配名称")
        fill("搭配名称", "安卓验收组合")
        scrollClick("安卓蓝上衣更新")
        scrollClick("安卓米色短裤")
        scrollClick("保存搭配")
        awaitText("安卓验收组合")
        click("衣橱")
        awaitText("安卓蓝上衣更新")
        compose
            .onNode(hasText("推荐搭配") and hasAnyAncestor(hasTestTag("clothing-$source")))
            .performScrollTo()
            .performClick()
        awaitText("当前衣物")
        awaitText("安卓米色短裤")
        screenshot("matching")
        assertEquals(1, compose.onAllNodesWithText("安卓蓝上衣更新").fetchSemanticsNodes().size)
        click("安卓米色短裤")
        awaitText("衣物详情")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        click("我的")
        click("类别管理")
        awaitListText("安卓验收类别")
        compose.onNodeWithContentDescription("新增类别").performClick()
        awaitText("类别名称")
        fill("类别名称", "安卓临时类别")
        click("保存")
        awaitListText("安卓临时类别")
        compose.onNodeWithContentDescription("修改安卓临时类别").performClick()
        fill("类别名称", "安卓临时类别改名")
        click("保存")
        awaitListText("安卓临时类别改名")
        compose.onNodeWithContentDescription("删除安卓临时类别改名").performClick()
        click("删除")
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("安卓临时类别改名").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("返回").performClick()
        click("衣橱")
        // Delete a referenced item: verify the modal closes and the Chinese conflict is visible.
        compose.onNodeWithContentDescription("删除安卓蓝上衣更新").performScrollTo().performClick()
        click("删除")
        awaitText("该衣物已用于保存的搭配，请先移出搭配，或选择归档")
        val outfits =
            JsonParser.parseString(call("GET", "/api/v1/outfits?source=manual&is_lookbook=true"))
                .asJsonObject["outfits"]
                .asJsonArray
        assertEquals(1, outfits.size())
        call("DELETE", "/api/v1/outfits/" + outfits[0].asJsonObject["id"].asString)
        click("安卓米色短裤")
        awaitText("衣物详情")
        compose.onNodeWithText("删除衣物").performScrollTo()
        click("删除衣物")
        click("删除")
        awaitText("我的衣橱")
        assertEquals(
            1,
            JsonParser.parseString(call("GET", "/api/v1/items")).asJsonObject["total"].asInt,
        )
        itemIds.remove(mate)
        click("我的")
        click("退出登录")
        compose
            .onNode(hasText("退出登录") and hasClickAction() and hasAnyAncestor(isDialog()))
            .performClick()
        awaitText("服务器地址")
    }

    @After
    fun cleanup() {
        scenario?.close()
        if (token.isNotEmpty()) {
            runCatching {
                val outfits =
                    JsonParser.parseString(
                            call("GET", "/api/v1/outfits?source=manual&is_lookbook=true"),
                        )
                        .asJsonObject["outfits"]
                        .asJsonArray
                outfits.forEach {
                    call("DELETE", "/api/v1/outfits/" + it.asJsonObject["id"].asString)
                }
                itemIds.forEach { runCatching { call("DELETE", "/api/v1/items/$it") } }
                categoryId?.let { call("DELETE", "/api/v1/categories/$it") }
                locationId?.let { call("DELETE", "/api/v1/locations/$it") }
            }
        }
    }
}
