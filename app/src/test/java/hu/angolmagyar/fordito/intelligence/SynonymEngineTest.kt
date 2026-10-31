package hu.angolmagyar.fordito.intelligence
import org.junit.Assert.*
import org.junit.Test
class SynonymEngineTest { @Test fun returnsCuratedAlternatives() { assertTrue(SynonymEngine.suggest("good").isNotEmpty()) } }
