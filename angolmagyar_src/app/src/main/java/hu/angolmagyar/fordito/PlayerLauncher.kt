package hu.angolmagyar.fordito

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** A videó megnyitása külső lejátszóban a kész felirattal (MX Player "subs" extra; a többi lejátszó a videó melletti SRT-t tölti be). */
object PlayerLauncher {
    fun open(ctx: Context, video: Uri, srt: File?) {
        try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(video, "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (srt != null && srt.exists()) {
                val su = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", srt)
                i.clipData = ClipData.newRawUri("srt", su).apply { addItem(ClipData.Item(video)) }
                i.putExtra("subs", arrayOf<android.os.Parcelable>(su))
                i.putExtra("subs.enable", arrayOf<android.os.Parcelable>(su))
                i.putExtra("subs.name", arrayOf(srt.name))
            }
            ctx.startActivity(Intent.createChooser(i, "Megnyitás lejátszóval"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Nem sikerült megnyitni: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
