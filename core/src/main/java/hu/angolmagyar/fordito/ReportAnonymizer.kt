package hu.angolmagyar.fordito
object ReportAnonymizer {
    private val EXT = "mp4|mkv|mov|avi|webm|m4a|mp3|wav|aac|ogg|opus|flac|srt|vtt|ass|txt|zip|json|csv"
    private val URI = Regex("""(?:content|file)://[^\s"']+""")
    private val PATH = Regex("""(?<![\p{L}\p{N}])/(?:storage|sdcard|data|mnt|Download|Movies|DCIM)[^\s"'<>|]*""")
    private val FILE = Regex("""[^\s"'/\\<>|:]+\.(?:$EXT)\b""", RegexOption.IGNORE_CASE)
    private val QUOTED_FILE = Regex("""(["'„“])([^"'”“\n]{1,120}\.(?:$EXT))(["'”“])""", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val URL_QUERY = Regex("""(https?://[^\s"'?]+)\?[^\s"']+""")
    private val SECRET = Regex("""\b(?:sk-[A-Za-z0-9_-]{16,}|AIza[A-Za-z0-9_-]{20,}|[A-Fa-f0-9]{32,}|[A-Za-z0-9+/_-]{40,}={0,2})\b""")
    private val JOB_LINE = Regex("""(FÁJL INDUL:\s*)(.+?)(\s·|$)""", RegexOption.MULTILINE)
    data class Result(val text: String, val replaced: Int)
    fun anonymize(text: String): Result {
        var n = 0
        val names = LinkedHashMap<String, Int>()
        fun tag(kind: String, v: String): String { n++; return "<$kind#${names.getOrPut("$kind|$v") { names.size + 1 }}>" }
        var s = text
        s = JOB_LINE.replace(s) { m -> m.groupValues[1] + tag("fájl", m.groupValues[2].trim()) + m.groupValues[3] }
        s = SECRET.replace(s) { n++; "<titok>" }
        s = EMAIL.replace(s) { n++; "<e-mail>" }
        s = URI.replace(s) { m -> tag("uri", m.value) }
        s = URL_QUERY.replace(s) { m -> n++; m.groupValues[1] + "?<paraméterek>" }
        s = PATH.replace(s) { m -> tag("útvonal", m.value) }
        s = QUOTED_FILE.replace(s) { m -> m.groupValues[1] + tag("fájl", m.groupValues[2]) + m.groupValues[3] }
        s = FILE.replace(s) { m -> if (m.value.startsWith("<")) m.value else tag("fájl", m.value) }
        s = IPV4.replace(s) { n++; "<ip>" }
        return Result(s, n)
    }
}
