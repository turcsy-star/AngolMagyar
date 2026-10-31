package hu.angolmagyar.fordito

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Összeomlás-megelőző szerkezeti teszt (eszköz nélkül): a manifestben deklarált komponensek osztálya létezzen,
 * és minden Activity/Service/Receiver osztály be legyen jegyezve (a hiányzó bejegyzés futásidőben
 * ActivityNotFoundException-nel omlana össze).
 */
class ManifestIntegrityTest {
    private val pkg = "hu.angolmagyar.fordito"
    private val appDir: File = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").exists() }
    private val srcRoot = File(appDir, "src/main/java")

    private fun declared(): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder().parse(File(appDir, "src/main/AndroidManifest.xml"))
        val out = LinkedHashMap<String, String>()
        for (tag in listOf("activity", "service", "receiver", "provider")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val name = (nodes.item(i) as Element).getAttribute("android:name")
                if (name.isEmpty()) continue
                val fq = when {
                    name.startsWith(".") -> pkg + name
                    name.contains('.') -> name
                    else -> "$pkg.$name"
                }
                out[fq] = tag
            }
        }
        return out
    }

    private fun sources(): List<File> = srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun fileFor(fq: String): File? {
        val rel = fq.replace('.', '/')
        val simple = fq.substringAfterLast('.')
        val dir = File(srcRoot, rel.substringBeforeLast('/'))
        File(dir, "$simple.kt").takeIf { it.exists() }?.let { return it }
        return sources().firstOrNull { f ->
            f.readText().let { t -> Regex("""\bclass\s+$simple\b""").containsMatchIn(t) && Regex("""^package\s+${Regex.escape(fq.substringBeforeLast('.'))}\s*$""", RegexOption.MULTILINE).containsMatchIn(t) }
        }
    }

    @Test fun everyDeclaredComponentHasAClass() {
        val missing = declared().keys.filter { !it.startsWith("androidx.") && !it.startsWith("com.google.") && fileFor(it) == null }
        assertTrue("A manifest olyan komponenst deklarál, amihez nincs osztály: $missing", missing.isEmpty())
    }

    @Test fun everyActivityClassIsDeclared() {
        val declaredNames = declared().keys
        val bases = Regex("""^\s*(?:abstract\s+|open\s+)?class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*(?:[\w.]*)(ComponentActivity|AppCompatActivity|FragmentActivity|Activity)\s*\(""", RegexOption.MULTILINE)
        val undeclared = ArrayList<String>()
        for (f in sources()) {
            val t = f.readText()
            val p = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(t)?.groupValues?.get(1) ?: continue
            for (m in bases.findAll(t)) {
                val fq = "$p.${m.groupValues[1]}"
                if (fq !in declaredNames && !t.contains("abstract class ${m.groupValues[1]}")) undeclared.add(fq)
            }
        }
        assertTrue("Activity osztály nincs a manifestben (összeomlás indításkor): $undeclared", undeclared.isEmpty())
    }

    @Test fun launcherActivityExists() {
        val text = File(appDir, "src/main/AndroidManifest.xml").readText()
        assertTrue(text.contains("android.intent.action.MAIN") && text.contains("android.intent.category.LAUNCHER"))
    }
}
