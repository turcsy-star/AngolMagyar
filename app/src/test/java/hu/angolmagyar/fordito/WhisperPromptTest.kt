package hu.angolmagyar.fordito

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperPromptTest {

    @Test
    fun hungarianBasicPrompt() {
        val p = WhisperPrompt.build(lang = "hu")
        assertNotNull(p)
        assertTrue(p!!.contains("magyar") || p.contains("központoz"))
    }

    @Test
    fun englishBasicPrompt() {
        val p = WhisperPrompt.build(lang = "en")
        assertNotNull(p)
        assertTrue(p!!.lowercase().contains("english") || p.lowercase().contains("punctuation"))
    }

    @Test
    fun includesNames() {
        val p = WhisperPrompt.build(lang = "hu", castNames = listOf("Anna", "Péter"))
        assertNotNull(p)
        assertTrue(p!!.contains("Anna"))
        assertTrue(p.contains("Péter"))
    }

    @Test
    fun subtitleDomain() {
        val p = WhisperPrompt.build(lang = "hu", domain = "subtitle")
        assertNotNull(p)
        assertTrue(p!!.contains("film") || p.contains("felirat") || p.contains("sorozat"))
    }

    @Test
    fun prevTailIncludedWhenLongEnough() {
        val tail = "A múltkori megbeszélésen egyeztettünk a határidőkről."
        val p = WhisperPrompt.build(lang = "hu", prevTail = tail)
        assertNotNull(p)
        assertTrue(p!!.contains("múltkori") || p.contains("Előző"))
    }

    @Test
    fun shortNoiseTailIgnored() {
        val p = WhisperPrompt.build(lang = "hu", prevTail = "um")
        assertNotNull(p)
        // ne tartalmazza a zajt kontextusként
        assertFalse(p!!.contains("um"))
    }

    @Test
    fun respectsMaxLength() {
        val longTail = "szó ".repeat(100)
        val p = WhisperPrompt.build(lang = "en", prevTail = longTail, castNames = listOf("A", "B", "C"))
        assertNotNull(p)
        assertTrue(p!!.length <= 220)
    }
}
