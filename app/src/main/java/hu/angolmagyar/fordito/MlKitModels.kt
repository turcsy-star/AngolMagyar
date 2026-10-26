package hu.angolmagyar.fordito

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * Az ML Kit fordítómodellek egyetlen, közös letöltési pontja. Korábban a Modellek képernyő, a háttér-letöltő
 * és a feliratkészítés egyszerre is elindíthatta ugyanazt a letöltést, ezek egymást megakaszthatták
 * ("Fordítómodell letöltése…" a végtelenségig). Most:
 *  - ha a modell már megvan, nem indul letöltés;
 *  - egyszerre csak egy letöltés fut, a többi hívó ugyanarra vár;
 *  - a kész állapotot a modellkezelőtől kérdezzük (nem csak a visszahívásban bízunk);
 *  - időtúllépésnél érthető hibaüzenet jön.
 * Blokkoló, háttérszálról hívandó.
 */
object MlKitModels {
    private val lock = ReentrantLock()
    private val direct = Executor { it.run() }

    private fun <T> await(task: Task<T>, sec: Long): T? {
        val latch = CountDownLatch(1)
        val v = AtomicReference<T?>(null)
        task.addOnSuccessListener(direct) { v.set(it); latch.countDown() }
        task.addOnFailureListener(direct) { latch.countDown() }
        latch.await(sec, TimeUnit.SECONDS)
        return v.get()
    }

    private fun model(code: String) = TranslateRemoteModel.Builder(code).build()

    /** Megvan-e minden felsorolt nyelv modellje? */
    fun present(codes: List<String>): Boolean {
        val mgr = RemoteModelManager.getInstance()
        for (c in codes.distinct()) {
            val ok = try { await(mgr.isModelDownloaded(model(c)), 10) == true } catch (_: Exception) { false }
            if (!ok) return false
        }
        return true
    }

    /**
     * Biztosítja a modelleket. [status] másodpercenként kapja az eltelt időt (kivételt dobhat, pl. szüneteltetéskor).
     * @throws IllegalStateException érthető üzenettel, ha nem sikerült
     */
    fun ensure(codes: List<String>, cancel: () -> Boolean, status: (elapsedSec: Long) -> Unit, maxSec: Long = 600) {
        if (present(codes)) return
        val t0 = System.currentTimeMillis()
        // más letöltés fut? várunk rá, de közben jelzünk és figyeljük a megszakítást
        while (!lock.tryLock(2, TimeUnit.SECONDS)) {
            if (cancel()) return
            status((System.currentTimeMillis() - t0) / 1000)
            if (present(codes)) return
        }
        try {
            if (present(codes)) return
            val mgr = RemoteModelManager.getInstance()
            var failure: Exception? = null
            val pending = CountDownLatch(1)
            val missing = codes.distinct().filter { c ->
                !(try { await(mgr.isModelDownloaded(model(c)), 10) == true } catch (_: Exception) { false })
            }
            val left = java.util.concurrent.atomic.AtomicInteger(missing.size)
            for (c in missing) {
                mgr.download(model(c), DownloadConditions.Builder().build())
                    .addOnSuccessListener(direct) { if (left.decrementAndGet() == 0) pending.countDown() }
                    .addOnFailureListener(direct) { e -> failure = e; pending.countDown() }
            }
            while (!pending.await(2, TimeUnit.SECONDS)) {
                if (cancel()) return
                val el = (System.currentTimeMillis() - t0) / 1000
                status(el)
                if (present(codes)) return
                if (el > maxSec) {
                    throw IllegalStateException(
                        "A fordítómodell letöltése nem indult el / nem fejeződött be (${el / 60} perc). " +
                            "Ellenőrizd az internetet és az adatkorlátozást, frissítsd a Google Play szolgáltatásokat, " +
                            "vagy állíts be online fordítást."
                    )
                }
            }
            failure?.let {
                throw IllegalStateException("A fordítómodell letöltése nem sikerült: ${it.message ?: "ismeretlen hiba"}")
            }
            if (!present(codes)) {
                throw IllegalStateException("A fordítómodell letöltése nem sikerült. Ellenőrizd az internetet, majd próbáld újra.")
            }
        } finally {
            lock.unlock()
        }
    }
}
