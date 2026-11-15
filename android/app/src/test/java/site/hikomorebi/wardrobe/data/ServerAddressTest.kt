package site.hikomorebi.wardrobe.data

import org.junit.Assert.*
import org.junit.Test

class ServerAddressTest {
    @Test
    fun `normalizes secure server with optional port`() {
        assertEquals(
            "https://cd.hikomorebi.site/",
            normalizedServer(" https://cd.hikomorebi.site "),
        )
        assertEquals("https://example.com:8443/", normalizedServer("https://example.com:8443"))
    }

    @Test
    fun `release rejects cleartext including localhost`() {
        for (url in
            listOf("http://139.155.149.243", "http://localhost:18080", "http://127.0.0.1")) {
            assertThrows(IllegalArgumentException::class.java) { normalizedServer(url) }
        }
    }

    @Test
    fun `debug cleartext only allows local test endpoints`() {
        assertEquals("http://127.0.0.1:18080/", normalizedServer("http://127.0.0.1:18080", true))
        assertEquals("http://10.0.2.2:8000/", normalizedServer("http://10.0.2.2:8000", true))
        assertThrows(IllegalArgumentException::class.java) {
            normalizedServer("http://139.155.149.243:18080", true)
        }
    }

    @Test
    fun `rejects embedded credentials path query and fragment`() {
        for (url in
            listOf(
                "https://user:pass@example.com",
                "https://example.com/api/v1",
                "https://example.com?x=1",
                "https://example.com#test",
                "example.com",
            )) {
            assertThrows(IllegalArgumentException::class.java) { normalizedServer(url) }
        }
    }
}
