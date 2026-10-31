package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Test

class FixesTest {
    @Test fun wholeWordCaseInsensitive() {
        val r = Fixes.parse("cat=dog")
        assertEquals("a dog and dog, concatenate", Fixes.apply("a cat and Cat, concatenate", r))
    }

    @Test fun ignoresInvalidLines() {
        assertEquals(1, Fixes.parse("x=y\n=bad\nnoequals").size)
    }

    @Test fun replacementIsLiteral() {
        assertEquals("a \$1 b", Fixes.apply("a x b", Fixes.parse("x=\$1")))
    }
}
