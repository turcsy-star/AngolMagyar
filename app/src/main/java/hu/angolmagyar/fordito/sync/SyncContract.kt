package hu.angolmagyar.fordito.sync

/**
 * P9 – Cloud Sync contract only.
 *
 * No network implementation ships until a backend, auth model, encryption envelope,
 * key recovery and threat model are chosen. UI must not expose a working sync toggle
 * that calls these methods until a real provider is registered.
 */
enum class SyncState {
    DISABLED,
    IDLE,
    PENDING,
    IN_PROGRESS,
    CONFLICT,
    ERROR
}

data class SyncConflict(
    val localId: String,
    val remoteId: String,
    val reason: String
)

data class EncryptedSyncEnvelope(
    /** Opaque ciphertext produced client-side; server stores bytes only. */
    val ciphertext: ByteArray,
    val keyId: String,
    val schemaVersion: Int,
    val createdAtEpochMs: Long
)

interface ConflictResolver {
    fun resolve(conflicts: List<SyncConflict>): List<SyncConflict>
}

/**
 * Platform-agnostic sync port. Default [NoOpSyncProvider] keeps the feature inert.
 */
interface SyncProvider {
    val id: String
    fun state(): SyncState
    /** Returns false when not configured / not allowed. */
    fun enqueuePush(envelope: EncryptedSyncEnvelope): Boolean
    fun poll(): List<EncryptedSyncEnvelope>
}

object NoOpSyncProvider : SyncProvider {
    override val id: String = "noop"
    override fun state(): SyncState = SyncState.DISABLED
    override fun enqueuePush(envelope: EncryptedSyncEnvelope): Boolean = false
    override fun poll(): List<EncryptedSyncEnvelope> = emptyList()
}

object SyncRegistry {
    @Volatile
    private var provider: SyncProvider = NoOpSyncProvider

    fun current(): SyncProvider = provider

    /**
     * Register a real provider only after security review.
     * Passing null resets to [NoOpSyncProvider].
     */
    @Synchronized
    fun register(provider: SyncProvider?) {
        this.provider = provider ?: NoOpSyncProvider
    }

    fun isEffectivelyEnabled(): Boolean =
        current().id != "noop" && current().state() != SyncState.DISABLED
}
