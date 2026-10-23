package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CheckpointTest {
    private fun tmp(): File = File.createTempFile("ckpt", ".txt").also { it.delete() }

    @Test fun roundTripWithSpecialCharacters() {
        val c = SrtExporter.Cue(1000, 2500, "Tab\there\nnew line \\ slash", "Cél\tszöveg", "S1")
        val back = Checkpoint.decode(Checkpoint.encode(c))
        assertEquals(c, back)
    }

    @Test fun appendAndLoad() {
        val f = tmp()
        val cp = Checkpoint(f, "key1")
        cp.append(0, listOf(SrtExporter.Cue(0, 1000, "a", "b")))
        cp.append(2, emptyList())
        cp.append(1, listOf(SrtExporter.Cue(2000, 3000, "c", ""), SrtExporter.Cue(3000, 4000, "d", "e", "S2")))
        val loaded = Checkpoint(f, "key1").load()
        assertEquals(setOf(0, 1, 2), loaded.keys)
        assertEquals(2, loaded[1]?.size)
        assertEquals(0, loaded[2]?.size)
        f.delete()
    }

    @Test fun differentKeyLoadsNothing() {
        val f = tmp()
        Checkpoint(f, "A").append(0, listOf(SrtExporter.Cue(0, 1, "x", "")))
        assertTrue(Checkpoint(f, "B").load().isEmpty())
        f.delete()
    }

    @Test fun truncatedRecordIsIgnored() {
        val f = tmp()
        val cp = Checkpoint(f, "K")
        cp.append(0, listOf(SrtExporter.Cue(0, 1, "ok", "")))
        f.appendText("C\t1\t3\n0\t1\t\tonly one line\t\n")
        val loaded = Checkpoint(f, "K").load()
        assertEquals(setOf(0), loaded.keys)
        f.delete()
    }

    @Test fun keyChangesWithInput() {
        assertTrue(Checkpoint.keyOf("a", 1) != Checkpoint.keyOf("a", 2))
        assertEquals(Checkpoint.keyOf("a", 1), Checkpoint.keyOf("a", 1))
    }
}
