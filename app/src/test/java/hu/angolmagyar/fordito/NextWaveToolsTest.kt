package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualTranslationTest {
    @Test fun identicalVersionsAreReported() {
        val c = DualTranslation.compare("I miss you.", "Hiányzol nekem.", "hiányzol nekem")
        assertTrue(c.identical)
        assertEquals(100, c.similarityPercent)
        assertTrue(c.notes.any { it.contains("azonos") })
    }

    @Test fun differingWordsAreListed() {
        val c = DualTranslation.compare("Bring the soup.", "Hozd a levest.", "Hozd el a levest.")
        assertEquals(listOf("el"), c.onlyB)
        assertTrue(c.onlyA.isEmpty())
        assertEquals(listOf("hozd", "a", "levest"), c.common)
        assertEquals(75, c.similarityPercent)
    }

    @Test fun numberMismatchAgainstSourceIsNoted() {
        val c = DualTranslation.compare("I have 5 apples.", "5 almám van.", "6 almám van.")
        assertFalse(c.numbersAgree)
        assertTrue(c.notes.any { it.contains("B változat számai nem egyeznek a forrással") })
        assertFalse(c.notes.any { it.contains("Az A változat számai") })
    }

    @Test fun mixedFormalityIsNoted() {
        val c = DualTranslation.compare("Can you help me?", "Segítsen nekem.", "Segíts nekem.")
        assertTrue(c.formalA)
        assertFalse(c.formalB)
        assertTrue(c.notes.any { it.contains("hangnem nem egységes") })
    }

    @Test fun veryDifferentVersionsAreFlagged() {
        val c = DualTranslation.compare("It is fine.", "Rendben van minden.", "Kiváló állapotban lévő eszköz.")
        assertTrue(c.similarityPercent < 40)
        assertTrue(c.notes.any { it.contains("Jelentősen eltérő") })
    }
}

class DictionaryProTest {
    @Test fun exactMatchComesFirst() {
        val hits = DictionaryPro.search("mind")
        assertEquals("mind", hits.first().entry.word)
        assertEquals(DictionaryPro.MatchKind.EXACT, hits.first().kind)
    }

    @Test fun prefixAndReverseSearchWork() {
        assertTrue(DictionaryPro.search("sym").any { it.entry.word == "sympathetic" })
        val rev = DictionaryPro.search("felad")
        assertTrue(rev.any { it.entry.word == "give up" && it.kind == DictionaryPro.MatchKind.MEANING })
    }

    @Test fun accentInsensitiveReverseSearch() {
        assertTrue(DictionaryPro.search("kozos").any { it.entry.word == "common" })
    }

    @Test fun sensesCarryPartOfSpeech() {
        val e = DictionaryPro.lookup("address")!!
        assertEquals(listOf("fn.", "ige"), e.senses.map { it.pos })
        assertEquals("fn. cím; ige megszólít, kezel (problémát)", e.meaningLine())
    }

    @Test fun falseFriendsHaveTip() {
        assertTrue(DictionaryPro.lookup("sensible")!!.tip.contains("Hamis barát"))
        assertTrue(DictionaryPro.lookup("eventually")!!.tip.contains("esetleg"))
    }

    @Test fun datasetIsConsistent() {
        val words = DictionaryPro.entries.map { it.word }
        assertEquals(words.size, words.toSet().size)
        assertTrue(words.size >= 100)
        assertTrue(DictionaryPro.entries.all { it.senses.isNotEmpty() && it.senses.all { s -> s.meanings.isNotEmpty() } })
        assertTrue(DictionaryPro.entries.all { it.example.isNotBlank() && it.exampleHu.isNotBlank() })
        assertNotNull(DictionaryPro.lookup("ACTUALLY"))
    }

    @Test fun blankQueryGivesNothing() {
        assertTrue(DictionaryPro.search("  ").isEmpty())
    }
}

class PronunciationCoachTest {
    @Test fun perfectReadingIsAllCorrect() {
        val a = PronunciationCoach.analyze("Where is the library?", "where is the library")
        assertEquals(100, a.scorePercent)
        assertTrue(a.words.all { it.status == PronunciationCoach.WordStatus.CORRECT })
        assertTrue(a.practiceWords.isEmpty())
    }

    @Test fun wrongAndMissingWordsAreSeparated() {
        val a = PronunciationCoach.analyze("I think the weather is nice", "I sink the is nice")
        val byWord = a.words.associate { it.word to it.status }
        assertEquals(PronunciationCoach.WordStatus.WRONG, byWord["think"])
        assertEquals(PronunciationCoach.WordStatus.MISSING, byWord["weather"])
        assertEquals(listOf("think", "weather"), a.practiceWords)
        assertTrue(a.tips.any { it.contains("th") })
    }

    @Test fun extraWordsAreReported() {
        val a = PronunciationCoach.analyze("good morning", "very good morning")
        assertEquals(listOf("very"), a.extras)
        assertTrue(a.scorePercent < 100)
    }

    @Test fun tipsAreCappedAndTargeted() {
        val tips = PronunciationCoach.tipsFor(listOf("hour", "water", "worked", "thought", "ring", "nation"))
        assertTrue(tips.size <= 4)
        assertTrue(tips.any { it.contains("néma") })
        assertTrue(PronunciationCoach.tipsFor(listOf("cat")).isEmpty())
    }

    @Test fun slowChunksSplitTheSentence() {
        assertEquals(listOf("Where is the", "library today"), PronunciationCoach.slowChunks("Where is the library today", 3))
    }

    @Test fun trendFollowsRecentScores() {
        assertEquals("javul", PronunciationCoach.trend(listOf(50, 60, 80)))
        assertEquals("romlik", PronunciationCoach.trend(listOf(90, 70)))
        assertEquals("stabil", PronunciationCoach.trend(listOf(80, 82, 81)))
        assertEquals("kevés adat", PronunciationCoach.trend(listOf(80)))
    }

    @Test fun emptyTargetIsHandled() {
        val a = PronunciationCoach.analyze("", "hello")
        assertEquals(0, a.scorePercent)
        assertEquals(listOf("hello"), a.extras)
    }
}
