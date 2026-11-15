package site.hikomorebi.wardrobe.data

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: WardrobeRepository
    private lateinit var photo: File
    private val item =
        """{"id":"item-1","name":"蓝上衣","category_id":"category-1","category_name":"上衣","condition":"good","season":["spring","summer"],"image_url":"/api/v1/images/user/image.jpg?expires=123&sig=test","is_archived":false,"status":"ready"}"""

    @Before
    fun setup() = runBlocking {
        server = MockWebServer().apply { start() }
        val storage =
            object : SessionStorage {
                override var server = this@RepositoryTest.server.url("/").toString()
                var current: StoredSession? = null

                override fun read() = current

                override fun write(session: StoredSession?) {
                    current = session
                }
            }
        val auth = SessionManager(storage, true)
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"access_token":"access","refresh_token":"${"a".repeat(64)}","expires_in":3600,"session_expires_at":"2030-01-01T00:00:00Z","user":{"id":"user-1","email":"test@example.com","display_name":"测试"}}""",
                ),
        )
        auth.login("test@example.com", "test-password")
        server.takeRequest()
        repo = WardrobeRepository(auth)
        photo =
            File.createTempFile("wardrobe-test", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun cleanup() {
        photo.delete()
        server.shutdown()
    }

    @Test
    fun `multipart repeats season fields and uses managed category IDs`() = runBlocking {
        server.enqueue(MockResponse().setBody(item))
        repo.saveItem(
            null,
            ItemWrite("蓝上衣", "category-1", "good", null, listOf("spring", "summer"), null),
            photo,
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertEquals(2, "name=\"season\"".toRegex().findAll(body).count())
        assertTrue(body.contains("spring"))
        assertTrue(body.contains("summer"))
        assertTrue(body.contains("category-1"))
        assertTrue(body.contains("name=\"skip_ai\""))
        assertFalse(body.contains("subtype"))
        assertFalse(body.contains("all-season"))
    }

    @Test
    fun `editing explicitly clears optional fields and reports image partial failure`() =
        runBlocking {
            server.enqueue(MockResponse().setBody(item))
            server.enqueue(MockResponse().setResponseCode(503))
            try {
                repo.saveItem("item-1", ItemWrite(null, null, null, null, emptyList(), null), photo)
                fail("Expected partial failure")
            } catch (_: PartialSaveException) {}
            val update = server.takeRequest()
            assertEquals("PATCH", update.method)
            val body = update.body.readUtf8()
            assertTrue(body.contains("\"category_id\":null"))
            assertTrue(body.contains("\"storage_location_id\":null"))
            val replacement = server.takeRequest()
            assertEquals("PUT", replacement.method)
            assertEquals("/api/v1/items/item-1/image", replacement.path)
            assertTrue(photo.exists())
        }

    @Test
    fun `recommendations query saved manual templates and exclude source`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"outfits":[{"id":"outfit-1","name":"日常","items":[{"id":"source","name":"上衣"},{"id":"mate","name":"短裤"}]}],"total":1,"has_more":false}""",
                ),
        )
        val result = repo.outfits(sourceId = "source")
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("source=manual"))
        assertTrue(request.path!!.contains("is_lookbook=true"))
        assertTrue(request.path!!.contains("item_id=source"))
        assertEquals(listOf("mate"), result.outfits.single().companions("source").map { it.id })
    }

    @Test
    fun `image origin is restricted to selected server`() {
        assertNotNull(repo.imageUrl("/api/v1/images/user/a.jpg?expires=123&sig=abc"))
        assertNull(repo.imageUrl("https://other.example.com/steal"))
        assertNull(repo.imageUrl("//other.example.com/steal"))
    }

    @Test
    fun `filters and seasons follow current personal vocabulary`() {
        val filters =
            ItemFilters(
                category = "category-1",
                location = "location-1",
                season = "winter",
                archived = true,
            )
        assertEquals("true", filters.query()["is_archived"])
        assertEquals("category-1", filters.query()["category_id"])
        assertFalse(filters.query().containsKey("subtype"))
        assertEquals(
            listOf("spring", "summer", "winter"),
            orderedSeasons(listOf("winter", "spring", "summer", "spring")),
        )
        assertEquals(seasons.keys.toList(), orderedSeasons(listOf("all-season")))
    }
}
