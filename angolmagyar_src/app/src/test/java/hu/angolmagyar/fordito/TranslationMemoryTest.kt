package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationMemoryTest {
    @Test fun importedPhrasesAreNormalizedForLookup() {
        val memory = TranslationMemory.fromText("Hello, WORLD!\tSzia, világ!\n")
        assertEquals("Szia, világ!", memory.lookup("hello world"))
        assertEquals("Szia, világ!", memory.lookup("HELLO, world!!!"))
    }

    @Test fun exportImportRoundTripKeepsTranslations() {
        val original = TranslationMemory()
        original.put("Good morning!", "Jó reggelt!")
        original.put("Thank you", "Köszönöm")
        val restored = TranslationMemory.fromText(original.toText())
        assertEquals("Jó reggelt!", restored.lookup("GOOD MORNING"))
        assertEquals("Köszönöm", restored.lookup("Thank you."))
    }

    @Test fun malformedAndBlankRowsAreIgnored() {
        val memory = TranslationMemory.fromText("header only\n\tNo source\nSource\t   \n\nValid source\tValid target\n")
        assertEquals(1, memory.size)
        assertEquals("Valid target", memory.lookup("Valid source"))
        assertNull(memory.lookup("Source"))
    }

    @Test fun canonicallyEquivalentUnicodePhrasesMatch() {
        val composed = "árvíztűrő tükörfúrógép"
        val decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD)
        val memory = TranslationMemory()
        memory.put(composed, "Hungarian phrase")
        assertEquals("Hungarian phrase", memory.lookup(decomposed))
    }

    @Test fun latestValueWinsForEquivalentNormalizedKeys() {
        val memory = TranslationMemory.fromText("Open the door\tNyisd ki az ajtót\nopen, THE door!\tAjtónyitás\n")
        assertEquals(1, memory.size)
        assertEquals("Ajtónyitás", memory.lookup("OPEN THE DOOR"))
    }
}
