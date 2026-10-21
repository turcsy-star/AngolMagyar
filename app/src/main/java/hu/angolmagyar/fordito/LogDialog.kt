package hu.angolmagyar.fordito

import android.content.Context
import android.content.Intent
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** „Mi történt?” – a legutóbbi hibák és figyelmeztetések (melegedés, akku, megszakadt rögzítés…). */
object LogDialog {

    fun show(ctx: Context) {
        val lines = AppState.readLog(80)
        val body = if (lines.isEmpty()) ctx.getString(R.string.log_empty) else lines.joinToString("\n\n")
        val tv = TextView(ctx).apply {
            text = body
            textSize = 12.5f
            setTextIsSelectable(true)
            val p = Ui.dp(ctx, 18)
            setPadding(p, p, p, p)
            setTextColor(Ui.color(ctx, R.color.textPrimary))
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.log_title)
            .setView(ScrollView(ctx).apply { addView(tv) })
            .setPositiveButton(R.string.common_close, null)
            .setNeutralButton(R.string.common_share) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.common_share)))
            }
            .setNegativeButton(R.string.log_clear) { _, _ -> AppState.clearLog() }
            .show()
    }
}
