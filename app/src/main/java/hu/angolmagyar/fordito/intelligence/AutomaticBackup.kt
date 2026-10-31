package hu.angolmagyar.fordito.intelligence

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hu.angolmagyar.fordito.core.db.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Device-local scheduled backup. The AES key is non-exportable and protected by Android Keystore. */
class AutomaticBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.get(applicationContext)
            // Read all tables in one Room transaction so the archive represents one consistent snapshot.
            val backup = db.withTransaction {
                LearningBackup(
                    System.currentTimeMillis(),
                    db.dictionaryDao().all(),
                    db.translationDao().all(),
                    db.reviewScheduleDao().all()
                )
            }
            val plain = BackupArchive.encode(backup).toByteArray(Charsets.UTF_8)
            val payload = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                cipher.updateAAD(MAGIC)
                val encrypted = cipher.doFinal(plain)
                MAGIC + byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
            } finally {
                plain.fill(0)
            }

            val dir = File(applicationContext.filesDir, BACKUP_DIR)
            check((dir.exists() || dir.mkdirs()) && dir.isDirectory) { "A biztonsági mentés mappája nem hozható létre." }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val target = File(dir, "diamond-$stamp.pibackup-auto")
            val temp = File(dir, ".${target.name}.tmp")
            try {
                FileOutputStream(temp).use { stream ->
                    stream.write(payload)
                    stream.fd.sync()
                }
                check(temp.renameTo(target)) { "Az ideiglenes mentés véglegesítése nem sikerült." }
            } finally {
                if (temp.exists()) temp.delete()
                payload.fill(0)
            }

            // Retain the newest 14 completed archives; ignore temporary files.
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(".pibackup-auto") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_BACKUPS)?.forEach { it.delete() }
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("last_success", System.currentTimeMillis())
                .putString("last_error", "")
                .apply()
            Result.success()
        } catch (e: Exception) {
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_error", e.message ?: e.javaClass.simpleName)
                .apply()
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val PREFS = "automatic_backup"
        private const val KEY_ALIAS = "angolmagyar_diamond_auto_backup_v1"
        private const val BACKUP_DIR = "automatic-backups"
        private const val MAX_BACKUPS = 14
        private const val MAX_RETRIES = 3
        private val MAGIC = byteArrayOf(65, 77, 65, 85, 84, 79, 49) // AMAUTO1
    }
}

object AutomaticBackupScheduler {
    fun configure(context: Context, daily: Boolean, weekly: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(AutomaticBackupWorker.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("daily_enabled", daily)
            .putBoolean("weekly_enabled", weekly)
            .apply()
        val manager = WorkManager.getInstance(app)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        if (daily) {
            val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            manager.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else manager.cancelUniqueWork(DAILY_WORK)

        if (weekly) {
            val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            manager.enqueueUniquePeriodicWork(WEEKLY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else manager.cancelUniqueWork(WEEKLY_WORK)
    }

    private const val DAILY_WORK = "diamond_daily_backup"
    private const val WEEKLY_WORK = "diamond_weekly_backup"
}
