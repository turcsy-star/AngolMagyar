package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class SmartContextTranslationTest {
    @Test fun bankAccountPrefersFinancialMeaning() {
        val result = SmartContextTranslation.suggest("Please check the bank account.")
        assertTrue(result.alternatives.any { it.text == "pénzintézet" })
        assertFalse(result.alternatives.any { it.text == "part" })
    }

    @Test fun riverBankPrefersGeographicMeaning() {
        val result = SmartContextTranslation.suggest("We walked along the river bank.")
        assertTrue(result.alternatives.any { it.text == "part" })
        assertFalse(result.alternatives.any { it.text == "pénzintézet" })
    }

    @Test fun unresolvedBankKeepsAlternativesAndStatesUncertainty() {
        val result = SmartContextTranslation.suggest("The bank is closed.")
        assertTrue(result.alternatives.any { it.text == "part" })
        assertTrue(result.alternatives.any { it.text == "pénzintézet" })
        assertTrue(result.alternatives.any { it.reason.contains("nem döntik el") })
    }

    @Test fun substringDoesNotCreateAmbiguity() {
        assertFalse(SmartContextTranslation.hasAmbiguity("The banking system is reliable."))
    }

    @Test fun batteryChargeUsesTechnicalSense() {
        val result = SmartContextTranslation.suggest("Please charge the phone battery.")
        assertTrue(result.alternatives.any { it.text == "tölt" })
        assertFalse(result.alternatives.any { it.text == "vád" })
    }
}
