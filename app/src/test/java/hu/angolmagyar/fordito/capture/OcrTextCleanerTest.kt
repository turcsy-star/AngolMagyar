package hu.angolmagyar.fordito.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextCleanerTest {
    @Test
    fun joinsHyphenatedWordsAndLines() {
        assertEquals("This is an international text with lines.", OcrTextCleaner.clean("This is an inter-\nnational  text\nwith lines."))
    }

    @Test
    fun keepsParagraphBreaks() {
        assertEquals("First paragraph.\n\nSecond one.", OcrTextCleaner.clean("First\nparagraph.\n\n\nSecond   one."))
    }

    @Test
    fun blankInputGivesEmpty() {
        assertEquals("", OcrTextCleaner.clean("  \n \n"))
    }

    @Test
    fun fromBlocksJoinsBlocks() {
        val blocks = listOf(OcrBlock("Hello", null), OcrBlock("world", 0.2f))
        assertEquals("Hello\n\nworld", OcrTextCleaner.fromBlocks(blocks))
        assertEquals(1, OcrTextCleaner.lowConfidenceCount(blocks))
    }
}
