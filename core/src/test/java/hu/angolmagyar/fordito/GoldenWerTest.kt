package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GoldenWerTest {
    private fun resource(name: String): String? {
        GoldenWerTest::class.java.classLoader?.getResourceAsStream("golden/$name")?.use { return it.readBytes().toString(Charsets.UTF_8) }
        val f = File("src/test/resources/golden/$name")
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    private fun loadCases(): List<GoldenWer.Case> {
        val manifest = GoldenWer.parseManifest(resource("manifest.csv") ?: error("hiányzik: golden/manifest.csv"))
        return manifest.map { (id, max) ->
            val ref = resource("$id.ref.txt") ?: error("hiányzik: golden/$id.ref.txt")
            val hyp = resource("$id.hyp.srt") ?: resource("$id.hyp.txt") ?: error("hiányzik: golden/$id.hyp.*")
            GoldenWer.Case(id, ref, hyp, max)
        }
    }

    @Test fun goldenSamplesStayWithinBaseline() {
        val cases = loadCases()
        assertTrue("nincs arany-minta", cases.isNotEmpty())
        val outcomes = GoldenWer.evaluate(cases)
        val bad = GoldenWer.failures(outcomes)
        assertTrue("WER-romlás:\n" + GoldenWer.report(outcomes), bad.isEmpty())
    }

    @Test fun regressionIsDetected() {
        val worse = GoldenWer.evaluate(listOf(GoldenWer.Case("x", "one two three four", "one two", 0.1)))
        assertEquals(1, GoldenWer.failures(worse).size)
        assertEquals(0.5, worse[0].wer, 1e-9)
    }

    @Test fun srtIsReducedToSpokenText() {
        val srt = "1\n00:00:00,000 --> 00:00:02,000\nHello world\n\n2\n00:00:02,500 --> 00:00:04,000\nsecond line\n"
        assertEquals("Hello world second line", GoldenWer.plainText(srt))
        assertEquals("plain text", GoldenWer.plainText("plain text"))
    }

    @Test fun manifestParsingIsLenient() {
        val m = GoldenWer.parseManifest("# komment\n\na,0.1\nb, 0,25\nbad\nc,xyz")
        assertEquals(setOf("a", "b"), m.keys)
        assertEquals(0.25, m.getValue("b"), 1e-9)
    }
}
