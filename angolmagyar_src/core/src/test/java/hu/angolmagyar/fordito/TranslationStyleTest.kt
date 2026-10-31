package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationStyleTest {
    @Test fun storedStyleFallsBackSafely() {
        assertEquals(TranslationStyle.NATURAL, TranslationStyle.fromStorage(null))
        assertEquals(TranslationStyle.NATURAL, TranslationStyle.fromStorage("unknown"))
        assertEquals(TranslationStyle.FORMAL, TranslationStyle.fromStorage("formal"))
    }

    @Test fun promptIncludesSelectedStyleInstruction() {
        val cfg = OnlineTranslator.Config(
            provider = OnlineTranslator.Provider.OPENAI,
            apiKey = "test",
            model = "test-model",
            style = TranslationStyle.FORMAL
        )
        val prompt = OnlineTranslator.systemPrompt(cfg)
        assertTrue(prompt.contains("Requested translation style"))
        assertTrue(prompt.contains(TranslationStyle.FORMAL.instruction))
    }
}
