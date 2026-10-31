package hu.angolmagyar.fordito

/**
 * Offline képességmátrix: kizárólag ténylegesen elérhető helyi komponens alapján
 * jelölhető egy funkció offline-képesnek. A hálózati szolgáltató önmagában nem elég.
 */
object OfflineCapabilityMatrix {
    data class Capability(
        val id: String,
        val requiredLocalComponents: Set<String> = emptySet(),
        val requiresNetwork: Boolean = false
    )

    data class Snapshot(
        val installedComponents: Set<String>,
        val verifiedComponents: Set<String>,
        val networkAvailable: Boolean
    )

    enum class State { READY_OFFLINE, READY_ONLINE_ONLY, MISSING_COMPONENT, UNVERIFIED_COMPONENT, NETWORK_UNAVAILABLE }
    data class Assessment(val capabilityId: String, val state: State, val missing: Set<String> = emptySet()) {
        val available: Boolean get() = state == State.READY_OFFLINE || state == State.READY_ONLINE_ONLY
        val worksWithoutNetwork: Boolean get() = state == State.READY_OFFLINE
    }

    fun assess(capability: Capability, snapshot: Snapshot): Assessment {
        if (capability.id.isBlank()) return Assessment(capability.id, State.MISSING_COMPONENT)
        val missing = capability.requiredLocalComponents - snapshot.installedComponents
        if (missing.isNotEmpty()) return Assessment(capability.id, State.MISSING_COMPONENT, missing)
        val unverified = capability.requiredLocalComponents - snapshot.verifiedComponents
        if (unverified.isNotEmpty()) return Assessment(capability.id, State.UNVERIFIED_COMPONENT, unverified)
        if (capability.requiresNetwork) {
            return Assessment(
                capability.id,
                if (snapshot.networkAvailable) State.READY_ONLINE_ONLY else State.NETWORK_UNAVAILABLE
            )
        }
        return Assessment(capability.id, State.READY_OFFLINE)
    }

    fun assessAll(capabilities: List<Capability>, snapshot: Snapshot): List<Assessment> {
        require(capabilities.map { it.id }.distinct().size == capabilities.size) { "A képességazonosítók legyenek egyediek." }
        return capabilities.map { assess(it, snapshot) }
    }
}
