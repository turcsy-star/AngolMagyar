package hu.angolmagyar.fordito

import org.junit.Assert.assertTrue
import org.junit.Test

class TipsTest {
    @Test fun keyError() { assertTrue(Tips.forError("Az API-kulcs érvénytelen (HTTP 401)").any { it.contains("kulcs") }) }
    @Test fun unknownGivesFallback() { assertTrue(Tips.forError("valami furcsa").isNotEmpty()) }
    @Test fun emptyResult() { assertTrue(Tips.forResult(0, 0, "angol").isNotEmpty()) }
    @Test fun manyFlags() { assertTrue(Tips.forResult(100, 40, "angol").isNotEmpty()) }
    @Test fun fine() { assertTrue(Tips.forResult(100, 3, "angol").isEmpty()) }
}
