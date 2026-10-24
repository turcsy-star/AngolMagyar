package hu.angolmagyar.fordito

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** SRT mellett Markdown, jegyzőkönyv, valódi DOCX és PDF export. */
object ExportHelper {

    const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MIME_PDF = "application/pdf"

    fun toMarkdown(cues: List<SrtExporter.Cue>, title: String = "Átirat"): String {
        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n\n")
        sb.append("*Készült: ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            .append("*\n\n")
        var lastSpk = ""
        for (c in cues) {
            val t = SrtExporter.formatTime(c.startMs).replace(',', '.')
            val spk = c.speaker.ifBlank { "" }
            if (spk.isNotBlank() && spk != lastSpk) {
                sb.append("\n### ").append(spk).append("\n\n")
                lastSpk = spk
            }
            val line = c.target.ifBlank { c.source }
            if (line.isBlank()) continue
            sb.append("`").append(t).append("` ")
            if (spk.isNotBlank()) sb.append("**").append(spk).append(":** ")
            sb.append(line).append("\n\n")
        }
        return sb.toString()
    }

    /** Egyszerű szöveges jegyzőkönyv. */
    fun toPlainMinutes(cues: List<SrtExporter.Cue>, title: String = "Tárgyalási jegyzőkönyv"): String {
        val sb = StringBuilder()
        sb.append(title).append("\n")
        sb.append("=".repeat(title.length)).append("\n\n")
        for (c in cues) {
            val t = SrtExporter.formatTime(c.startMs).substring(0, 8)
            val spk = c.speaker.ifBlank { "—" }
            val line = c.target.ifBlank { c.source }
            if (line.isBlank()) continue
            sb.append("[").append(t).append("] ").append(spk).append(": ").append(line).append("\n")
        }
        return sb.toString()
    }

    fun write(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ közös dokumentum-modell

    private data class Block(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val size: Int = 11,      // pt
        val gray: Boolean = false,
        val spaceBefore: Boolean = false
    )

    private fun blocks(
        cues: List<SrtExporter.Cue>,
        title: String,
        summary: MeetingSummary.Result?,
        includeSource: Boolean
    ): List<Block> {
        val out = mutableListOf<Block>()
        out.add(Block(title, bold = true, size = 18))
        out.add(
            Block(
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                size = 9, gray = true
            )
        )
        if (summary != null) {
            out.add(Block("Összefoglaló", bold = true, size = 13, spaceBefore = true))
            out.add(Block("Időtartam: ${summary.durationLabel} · Bejegyzések: ${summary.cueCount}", size = 10))
            if (summary.speakers.any { it.first != "—" }) {
                out.add(Block("Beszélők: " + summary.speakers.joinToString(", ") { "${it.first} (${it.second})" }, size = 10))
            }
            if (summary.decisions.isNotEmpty()) {
                out.add(Block("Döntések", bold = true, size = 11, spaceBefore = true))
                summary.decisions.forEach { out.add(Block("✔ $it", size = 10)) }
            }
            if (summary.actions.isNotEmpty()) {
                out.add(Block("Teendők", bold = true, size = 11, spaceBefore = true))
                summary.actions.forEach { out.add(Block("☐ $it", size = 10)) }
            }
            if (summary.keyLines.isNotEmpty()) {
                out.add(Block("Kiemelt mondatok", bold = true, size = 11, spaceBefore = true))
                summary.keyLines.forEach { out.add(Block("• $it", size = 10)) }
            }
            out.add(Block("Teljes átirat", bold = true, size = 13, spaceBefore = true))
        }
        var lastSpk = "\u0000"
        for (c in cues) {
            val line = c.target.ifBlank { c.source }
            if (line.isBlank() || line == "…") continue
            if (c.speaker != lastSpk && c.speaker.isNotBlank()) {
                out.add(Block(c.speaker, bold = true, size = 11, spaceBefore = true))
            }
            lastSpk = c.speaker
            val t = SrtExporter.formatTime(c.startMs).substring(0, 8)
            out.add(Block("$t  $line", size = 10))
            if (includeSource && c.source.isNotBlank() && c.source != c.target && c.source != "…") {
                out.add(Block("      ${c.source}", italic = true, size = 9, gray = true))
            }
        }
        return out
    }

    // ------------------------------------------------------------------ DOCX

    private fun xml(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 32 && ch != '\t' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Minimális, Wordben/LibreOffice-ban megnyíló OOXML (.docx). */
    fun writeDocx(
        out: OutputStream,
        cues: List<SrtExporter.Cue>,
        title: String,
        summary: MeetingSummary.Result? = null,
        includeSource: Boolean = false
    ) {
        val body = StringBuilder()
        for (b in blocks(cues, title, summary, includeSource)) {
            body.append("<w:p><w:pPr><w:spacing w:before=\"")
                .append(if (b.spaceBefore) 200 else 0)
                .append("\" w:after=\"60\"/></w:pPr><w:r><w:rPr>")
            if (b.bold) body.append("<w:b/>")
            if (b.italic) body.append("<w:i/>")
            if (b.gray) body.append("<w:color w:val=\"6B7280\"/>")
            body.append("<w:sz w:val=\"").append(b.size * 2).append("\"/></w:rPr>")
            body.append("<w:t xml:space=\"preserve\">").append(xml(b.text)).append("</w:t></w:r></w:p>")
        }
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" +
            body +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" " +
            "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>"
        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" " +
            "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
            "Target=\"word/document.xml\"/></Relationships>"
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("word/document.xml", document)
        }
    }

    fun docxBytes(
        cues: List<SrtExporter.Cue>, title: String,
        summary: MeetingSummary.Result? = null, includeSource: Boolean = false
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        writeDocx(bos, cues, title, summary, includeSource)
        return bos.toByteArray()
    }

    // ------------------------------------------------------------------ PDF

    /** Többoldalas A4 PDF a rendszer PdfDocument osztályával. */
    fun writePdf(
        out: OutputStream,
        cues: List<SrtExporter.Cue>,
        title: String,
        summary: MeetingSummary.Result? = null,
        includeSource: Boolean = false
    ) {
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val maxW = pageW - 2 * margin
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun footer() {
            val c = canvas ?: return
            paint.typeface = Typeface.DEFAULT
            paint.isFakeBoldText = false
            paint.textSize = 8f
            paint.color = 0xFF777777.toInt()
            c.drawText("$title – $pageNo", margin, pageH - 22f, paint)
        }

        fun newPage() {
            page?.let {
                footer()
                doc.finishPage(it)
            }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = p
            canvas = p.canvas
            y = margin
        }

        newPage()
        for (b in blocks(cues, title, summary, includeSource)) {
            paint.typeface = if (b.italic) Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) else Typeface.DEFAULT
            paint.isFakeBoldText = b.bold
            paint.textSize = b.size.toFloat()
            paint.color = if (b.gray) 0xFF6B7280.toInt() else 0xFF111111.toInt()
            val lineH = b.size * 1.35f
            if (b.spaceBefore) y += b.size * 0.8f
            var rest = b.text
            while (rest.isNotEmpty()) {
                if (y + lineH > pageH - 50f) {
                    newPage()
                    paint.typeface = if (b.italic) Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) else Typeface.DEFAULT
                    paint.isFakeBoldText = b.bold
                    paint.textSize = b.size.toFloat()
                    paint.color = if (b.gray) 0xFF6B7280.toInt() else 0xFF111111.toInt()
                }
                var n = paint.breakText(rest, true, maxW, null)
                if (n <= 0) n = 1
                if (n < rest.length) {
                    // szóhatáron törünk, ha lehet
                    val sp = rest.lastIndexOf(' ', n - 1)
                    if (sp > 0) n = sp + 1
                }
                canvas?.drawText(rest.substring(0, n).trimEnd(), margin, y + b.size, paint)
                y += lineH
                rest = rest.substring(n).trimStart()
            }
        }
        page?.let {
            footer()
            doc.finishPage(it)
        }
        try {
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    fun pdfBytes(
        cues: List<SrtExporter.Cue>, title: String,
        summary: MeetingSummary.Result? = null, includeSource: Boolean = false
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        writePdf(bos, cues, title, summary, includeSource)
        return bos.toByteArray()
    }
}
