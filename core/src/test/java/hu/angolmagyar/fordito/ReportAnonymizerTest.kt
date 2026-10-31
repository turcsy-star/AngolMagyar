package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class ReportAnonymizerTest {
    @Test fun hidesFileNamesPathsAndUris() {
        val src = "FÁJL INDUL: Kovács Péter tárgyalás.mp4 · 12 perc\nmegnyit: content://media/external/video/1234\n/storage/emulated/0/Download/titkos.srt kész"
        val r = ReportAnonymizer.anonymize(src)
        assertFalse(r.text.contains("Kovács"))
        assertFalse(r.text.contains("titkos"))
        assertFalse(r.text.contains("content://"))
        assertFalse(r.text.contains("/storage/"))
        assertTrue(r.text.contains("12 perc"))
        assertTrue(r.replaced >= 3)
    }

    @Test fun sameNameGetsSameMarker() {
        val r = ReportAnonymizer.anonymize("a interju.mp4 és újra interju.mp4 majd masik.mp4")
        val tags = Regex("<fájl#(\\d+)>").findAll(r.text).map { it.groupValues[1] }.toList()
        assertEquals(3, tags.size)
        assertEquals(tags[0], tags[1])
        assertNotEquals(tags[0], tags[2])
    }

    @Test fun hidesSecretsEmailsAndIps() {
        val r = ReportAnonymizer.anonymize("key=sk-abcdefghijklmnopqrstuv mail anna@example.com ip 192.168.1.20 https://x.hu/a?token=1&u=2")
        assertFalse(r.text.contains("sk-abcdef"))
        assertFalse(r.text.contains("anna@"))
        assertFalse(r.text.contains("192.168"))
        assertFalse(r.text.contains("token=1"))
    }

    @Test fun keepsDiagnosticContent() {
        val s = "Verzió: 4.2.0\nTelefon: HONOR Magic · Android 16 (SDK 36)\nÁllapot: fut=true lépés=2 folyamat=45%"
        assertEquals(s, ReportAnonymizer.anonymize(s).text)
    }
}
