package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLTest {
    @Test fun codes() {
        assertEquals("HU", OnlineTranslator.deeplCode("magyar", true))
        assertEquals("EN-GB", OnlineTranslator.deeplCode("angol", true))
        assertEquals("EN", OnlineTranslator.deeplCode("angol", false))
        assertEquals("NB", OnlineTranslator.deeplCode("norvég", false))
        assertEquals("PT-PT", OnlineTranslator.deeplCode("portugál", true))
        assertNull(OnlineTranslator.deeplCode("hindi", true))
    }

    @Test fun endpoint() {
        assertEquals("https://api-free.deepl.com/v2/translate", OnlineTranslator.deeplEndpoint("abc:fx", ""))
        assertEquals("https://api.deepl.com/v2/translate", OnlineTranslator.deeplEndpoint("abc", ""))
        assertEquals("http://x/v2/translate", OnlineTranslator.deeplEndpoint("abc", "http://x/"))
    }

    @Test fun requestAndParse() {
        val r = OnlineTranslator.deeplRequest(listOf("Hi", "Bye"), "EN", "HU", "ctx", true)
        assertTrue(r.contains("\"text\":[\"Hi\",\"Bye\"]"))
        assertTrue(r.contains("\"target_lang\":\"HU\""))
        assertTrue(r.contains("\"context\":\"ctx\""))
        assertTrue(r.contains("prefer_less"))
        assertEquals(listOf("Szia", "Viszlát"), OnlineTranslator.parseDeepL("{\"translations\":[{\"text\":\"Szia\"},{\"text\":\"Viszlát\"}]}", 2))
        assertNull(OnlineTranslator.parseDeepL("{\"translations\":[{\"text\":\"Szia\"}]}", 2))
    }

    @Test fun contextHasNeighbours() {
        val en = listOf("a1", "a2", "b", "c1", "c2")
        val c = OnlineTranslator.deeplContext("Dráma", en, 2, 2)
        assertTrue(c.startsWith("Dráma"))
        assertTrue(c.contains("a1 a2"))
        assertTrue(c.contains("c1 c2"))
    }

    @Test fun translateEndToEnd() {
        var seenUrl = ""
        var seenAuth = ""
        val http = object : OnlineTranslator.Http {
            override fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): Pair<Int, String> {
                seenUrl = url; seenAuth = headers["authorization"] ?: ""
                return 200 to "{\"translations\":[{\"text\":\"Szia\"},{\"text\":\"Kösz\"}]}"
            }
        }
        val cfg = OnlineTranslator.Config(OnlineTranslator.Provider.DEEPL, "k:fx", "", srcLang = "angol", tgtLang = "magyar")
        val hu = mutableListOf("", "")
        val failed = OnlineTranslator(cfg, http).translate(listOf("Hello", "Thanks"), hu, listOf(0, 1))
        assertEquals(emptyList<Int>(), failed)
        assertEquals(listOf("Szia", "Kösz"), hu)
        assertEquals("https://api-free.deepl.com/v2/translate", seenUrl)
        assertEquals("DeepL-Auth-Key k:fx", seenAuth)
    }

    @Test fun quotaIsFatal() {
        val http = object : OnlineTranslator.Http {
            override fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int) = 456 to "{\"message\":\"Quota\"}"
        }
        val cfg = OnlineTranslator.Config(OnlineTranslator.Provider.DEEPL, "k", "", srcLang = "angol", tgtLang = "magyar")
        val t = OnlineTranslator(cfg, http)
        assertEquals(listOf(0), t.translate(listOf("Hi"), mutableListOf(""), listOf(0)))
        assertTrue(t.fatal)
    }
}
