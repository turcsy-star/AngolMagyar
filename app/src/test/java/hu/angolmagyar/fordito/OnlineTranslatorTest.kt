package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineTranslatorTest {
    /** Álszerver: a kérésből kiolvassa a fordítandó blokkokat, és "HU:<szöveg>" választ ad a megfelelő formában. */
    private class Fake(val provider: OnlineTranslator.Provider, val script: ((Int) -> Pair<Int, String>?)? = null) : OnlineTranslator.Http {
        var calls = 0
        val bodies = ArrayList<String>()
        val urls = ArrayList<String>()
        val heads = ArrayList<Map<String, String>>()
        override fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): Pair<Int, String> {
            calls++
            bodies.add(body); urls.add(url); heads.add(headers)
            script?.invoke(calls)?.let { return it }
            val root = MiniJson.parse(body) as Map<*, *>
            val user = when (provider) {
                OnlineTranslator.Provider.ANTHROPIC -> ((root["messages"] as List<*>)[0] as Map<*, *>)["content"] as String
                OnlineTranslator.Provider.OPENAI -> ((root["messages"] as List<*>)[1] as Map<*, *>)["content"] as String
            }
            val payload = MiniJson.parse(user) as Map<*, *>
            val tr = (payload["translate"] as List<*>).map { "HU:" + ((it as Map<*, *>)["text"] as String) }
            val arr = "[" + tr.joinToString(",") { MiniJson.quote(it) } + "]"
            val text = "Itt a fordítás:\n$arr"
            return 200 to when (provider) {
                OnlineTranslator.Provider.ANTHROPIC -> "{\"content\":[{\"type\":\"text\",\"text\":${MiniJson.quote(text)}}]}"
                OnlineTranslator.Provider.OPENAI -> "{\"choices\":[{\"message\":{\"content\":${MiniJson.quote(text)}}}]}"
            }
        }
    }

    private fun cfg(p: OnlineTranslator.Provider, batch: Int = 30) =
        OnlineTranslator.Config(p, "kulcs", "modell", batchSize = batch, glossary = listOf("Moby" to "Moby"), hint = "film")

    @Test fun anthropicBatchesAndContext() {
        val http = Fake(OnlineTranslator.Provider.ANTHROPIC)
        val ot = OnlineTranslator(cfg(OnlineTranslator.Provider.ANTHROPIC, batch = 2), http)
        val en = listOf("a", "b", "c", "d", "e")
        val hu = MutableList(5) { "" }
        val failed = ot.translate(en, hu, listOf(0, 1, 2, 3, 4))
        assertTrue(failed.isEmpty())
        assertEquals(listOf("HU:a", "HU:b", "HU:c", "HU:d", "HU:e"), hu)
        assertEquals(3, http.calls)
        assertTrue(http.urls[0].endsWith("/v1/messages"))
        assertEquals("kulcs", http.heads[0]["x-api-key"])
        // a 2. köteg kontextusa már tartalmazza az 1. köteg fordítását
        assertTrue(http.bodies[1].contains("HU:a"))
        // a 3. kötegnél nincs "after" kontextus
        assertTrue(http.bodies[2].contains("after"))
    }

    @Test fun openAiFormat() {
        val http = Fake(OnlineTranslator.Provider.OPENAI)
        val ot = OnlineTranslator(cfg(OnlineTranslator.Provider.OPENAI).copy(baseUrl = "http://192.168.1.5:8080/v1/"), http)
        val hu = mutableListOf("")
        assertTrue(ot.translate(listOf("Hi"), hu, listOf(0)).isEmpty())
        assertEquals("HU:Hi", hu[0])
        assertEquals("http://192.168.1.5:8080/v1/chat/completions", http.urls[0])
        assertEquals("Bearer kulcs", http.heads[0]["authorization"])
    }

    @Test fun badKeyIsFatalAndNotRetried() {
        val http = Fake(OnlineTranslator.Provider.ANTHROPIC) { 401 to "{\"error\":{\"message\":\"invalid x-api-key\"}}" }
        val ot = OnlineTranslator(cfg(OnlineTranslator.Provider.ANTHROPIC, batch = 1), http)
        val hu = MutableList(3) { "" }
        val failed = ot.translate(listOf("a", "b", "c"), hu, listOf(0, 1, 2))
        assertEquals(listOf(0, 1, 2), failed)
        assertEquals(1, http.calls)
        assertTrue(ot.fatal)
        assertTrue(ot.lastError.contains("invalid x-api-key"))
    }

    @Test fun wrongCountIsRetriedThenFails() {
        val bad = "{\"content\":[{\"type\":\"text\",\"text\":\"[\\\"csak egy\\\"]\"}]}"
        val http = Fake(OnlineTranslator.Provider.ANTHROPIC) { 200 to bad }
        val ot = OnlineTranslator(cfg(OnlineTranslator.Provider.ANTHROPIC), http)
        val hu = MutableList(2) { "" }
        assertEquals(listOf(0, 1), ot.translate(listOf("a", "b"), hu, listOf(0, 1)))
        assertEquals(2, http.calls)
        assertEquals("", hu[0])
    }

    @Test fun transientErrorThenSuccess() {
        var first = true
        val http = Fake(OnlineTranslator.Provider.ANTHROPIC) { if (first) { first = false; 503 to "{}" } else null }
        val ot = OnlineTranslator(cfg(OnlineTranslator.Provider.ANTHROPIC), http)
        val hu = mutableListOf("")
        assertTrue(ot.translate(listOf("Hello"), hu, listOf(0)).isEmpty())
        assertEquals("HU:Hello", hu[0])
    }

    @Test fun onlySelectedIndicesAreSent() {
        val http = Fake(OnlineTranslator.Provider.ANTHROPIC)
        val ot = OnlineTranslator(cfg(OnlineTranslator.Provider.ANTHROPIC), http)
        val hu = mutableListOf("régi0", "régi1", "régi2")
        assertTrue(ot.translate(listOf("x", "y", "z"), hu, listOf(1)).isEmpty())
        assertEquals(listOf("régi0", "HU:y", "régi2"), hu)
    }

    @Test fun promptMentionsInformalAndGlossary() {
        val p = OnlineTranslator.systemPrompt(cfg(OnlineTranslator.Provider.ANTHROPIC))
        assertTrue(p.contains("tegezés"))
        assertTrue(p.contains("Moby => Moby"))
        assertTrue(p.contains("Background: film"))
    }
}
