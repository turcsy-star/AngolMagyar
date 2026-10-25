package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {
    @Test fun parsesNestedValues() {
        val v = MiniJson.parse("""{"a":[1,2.5,"x\n\u00e9",true,null],"b":{"c":"d"}}""") as Map<*, *>
        val a = v["a"] as List<*>
        assertEquals(5, a.size)
        assertEquals("x\né", a[2])
        assertEquals("d", (v["b"] as Map<*, *>)["c"])
    }

    @Test fun quoteRoundTrips() {
        val s = "Ó, \"idézet\"\n\tútvonal\\ és \u0001 vezérlő"
        assertEquals(s, MiniJson.parse(MiniJson.quote(s)))
    }

    @Test fun extractsArrayFromChattyText() {
        val t = "Itt van:\n```json\n[\"a [b]\", \"c\"]\n```\nKész."
        assertEquals("[\"a [b]\", \"c\"]", MiniJson.extractArray(t))
    }

    @Test fun badJsonThrows() {
        var failed = false
        try { MiniJson.parse("{\"a\":") } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
}
