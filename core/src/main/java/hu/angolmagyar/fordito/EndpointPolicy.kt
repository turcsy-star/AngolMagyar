package hu.angolmagyar.fordito
import java.net.URI
object EndpointPolicy {
    fun isAllowed(baseUrl: String): Boolean {
        val raw = baseUrl.trim()
        if (raw.isEmpty()) return true
        val uri = try { URI(raw) } catch (_: Exception) { return false }
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase()?.removeSuffix(".") ?: return false
        if (uri.userInfo != null || uri.fragment != null) return false
        if (scheme == "https") return true
        if (scheme != "http") return false
        return host == "localhost" || host.endsWith(".localhost") || isPrivateIpv4(host)
    }
    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || part.any { !it.isDigit() }) return false
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        val a = octets[0]
        val b = octets[1]
        return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }
}
