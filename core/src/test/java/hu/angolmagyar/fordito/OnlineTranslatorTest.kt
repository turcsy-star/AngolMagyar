package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineTranslatorTest {
    @Test fun parseTranslationsAcceptsStringsAndObjects() {
        assertEquals(listOf("Szia", "Világ"), OnlineTranslator.parseTranslations("[\"Szia\", \"Világ\"]", 2))
        assertEquals(listOf("Szia"), OnlineTranslator.parseTranslations("[{\"text\":\"Szia\"}]", 1))
    }

    @Test fun parseTranslationsToleratesProse() {
        assertEquals(listOf("Igen"), OnlineTranslator.parseTranslations("Íme:\n[\"Igen\"]\nKész.", 1))
    }

    @Test fun parseTranslationsRejectsWrongCount() {
        assertNull(OnlineTranslator.parseTranslations("[\"a\",\"b\"]", 3))
        assertNull(OnlineTranslator.parseTranslations("nem json", 1))
    }

    @Test fun payloadHasContextAroundBatch() {
        val en = List(10) { "line$it" }
        val hu = List(10) { if (it < 4) "sor$it" else "" }
        val p = OnlineTranslator.userPayload(en, hu, listOf(4, 5))
        val root = MiniJson.parse(p) as Map<*, *>
        assertEquals(2, (root["translate"] as List<*>).size)
        assertEquals(4, (root["before"] as List<*>).size)
        assertEquals(3, (root["after"] as List<*>).size)
        assertTrue(p.contains("\"translation\":\"sor3\""))
    }

    @Test fun payloadAtEdgesDoesNotOverflow() {
        val en = listOf("a", "b")
        val p = OnlineTranslator.userPayload(en, listOf("", ""), listOf(0, 1))
        val root = MiniJson.parse(p) as Map<*, *>
        assertEquals(0, (root["before"] as List<*>).size)
        assertEquals(0, (root["after"] as List<*>).size)
    }

    @Test fun systemPromptMentionsInformalAddressForHungarian() {
        val cfg = OnlineTranslator.Config(OnlineTranslator.Provider.OPENAI, "k", "m", informal = true)
        assertTrue(OnlineTranslator.systemPrompt(cfg).contains("tegezés"))
        val formal = cfg.copy(informal = false)
        assertTrue(!OnlineTranslator.systemPrompt(formal).contains("tegezés"))
    }

    @Test fun deeplContextIncludesNeighbours() {
        val en = listOf("one", "two", "three", "four")
        val ctx = OnlineTranslator.deeplContext("hint", en, 1, 2)
        assertNotNull(ctx)
        assertTrue(ctx.contains("one") && ctx.contains("four") && ctx.contains("hint"))
    }
    @Test fun gemini503AutomaticallyFallsBackToFlashLite() {
        val urls = mutableListOf<String>()
        var calls = 0
        val http = OnlineTranslator.Http { url, _, _, _ ->
            urls.add(url)
            calls++
            if (calls == 1) {
                503 to """{"error":{"message":"This model is currently experiencing high demand."}}"""
            } else {
                200 to """{"candidates":[{"content":{"parts":[{"text":"[\"Szia\"]"}]}}]}"""
            }
        }
        val translator = OnlineTranslator(
            OnlineTranslator.Config(
                provider = OnlineTranslator.Provider.GEMINI,
                apiKey = "test-key",
                model = OnlineTranslator.DEFAULT_GEMINI_MODEL
            ),
            http
        )
        val output = mutableListOf("")
        val failed = translator.translate(listOf("Hello"), output, listOf(0))
        assertTrue(failed.isEmpty())
        assertEquals("Szia", output[0])
        assertEquals(2, calls)
        assertTrue(urls[0].contains(OnlineTranslator.DEFAULT_GEMINI_MODEL))
        assertTrue(urls[1].contains(OnlineTranslator.GEMINI_FALLBACK_MODEL))
    }

}
