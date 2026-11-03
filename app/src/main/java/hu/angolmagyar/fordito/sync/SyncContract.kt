package hu.angolmagyar.fordito.sync
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
    val ciphertext: ByteArray,
    val keyId: String,
    val schemaVersion: Int,
    val createdAtEpochMs: Long
)
interface ConflictResolver {
    fun resolve(conflicts: List<SyncConflict>): List<SyncConflict>
}
interface SyncProvider {
    val id: String
    fun state(): SyncState
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
    @Synchronized
    fun register(provider: SyncProvider?) {
        this.provider = provider ?: NoOpSyncProvider
    }
    fun isEffectivelyEnabled(): Boolean =
        current().id != "noop" && current().state() != SyncState.DISABLED
}
