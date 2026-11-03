package hu.angolmagyar.fordito

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * Android 16 (API 36) élő frissítés: szegmentált folyamatsáv (Notification.ProgressStyle) a lépésekkel
 * és a darabhatárokkal, plusz kérés a "promoted ongoing" megjelenítésre és rövid állapotszöveg.
 * A projekt compileSdk 35-ös, ezért az API 36-os osztályokat reflexióval érjük el; bármilyen hiba esetén
 * null a visszatérés, és a hívó a megszokott értesítést használja.
 */
object LiveProgress {
    private val INT = Int::class.javaPrimitiveType!!
    private val BOOL = Boolean::class.javaPrimitiveType!!

    fun build(
        ctx: Context, channelId: String, plan: NotificationPlan.Plan,
        title: String, text: String, subText: String?, whenMs: Long, smallIcon: Int,
        contentIntent: PendingIntent, cancelIntent: PendingIntent
    ): Notification? {
        if (Build.VERSION.SDK_INT < 36) return null
        return try {
            val b = Notification.Builder(ctx, channelId)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(whenMs)
                .setUsesChronometer(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
            if (!subText.isNullOrBlank()) b.setSubText(subText)
            @Suppress("DEPRECATION")
            b.addAction(0, "Megszakítás", cancelIntent)
            b.setStyle(progressStyle(plan))
            promote(b, plan.shortText)
            b.build()
        } catch (e: Throwable) {
            DiagLog.swallow("LiveProgress", e)
            null
        }
    }

    private fun progressStyle(plan: NotificationPlan.Plan): Notification.Style {
        val psCls = Class.forName("android.app.Notification\$ProgressStyle")
        val segCls = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
        val ptCls = Class.forName("android.app.Notification\$ProgressStyle\$Point")
        val ps = psCls.getConstructor().newInstance()
        psCls.getMethod("setStyledByProgress", BOOL).invoke(ps, true)
        if (plan.indeterminate) {
            try { psCls.getMethod("setProgressIndeterminate", BOOL).invoke(ps, true) }
            catch (_: Throwable) { psCls.getMethod("setProgress", INT).invoke(ps, 0) }
        } else {
            psCls.getMethod("setProgress", INT).invoke(ps, plan.progress)
        }
        val segments = plan.segments.map { s ->
            val o = segCls.getConstructor(INT).newInstance(s.length)
            segCls.getMethod("setColor", INT).invoke(o, s.color)
            o
        }
        psCls.getMethod("setProgressSegments", List::class.java).invoke(ps, segments)
        if (plan.points.isNotEmpty()) {
            val points = plan.points.map { p ->
                val o = ptCls.getConstructor(INT).newInstance(p)
                ptCls.getMethod("setColor", INT).invoke(o, 0xFFFFFFFF.toInt())
                o
            }
            psCls.getMethod("setProgressPoints", List::class.java).invoke(ps, points)
        }
        return ps as Notification.Style
    }

    private fun promote(b: Notification.Builder, shortText: String) {
        try { Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", BOOL).invoke(b, true) }
        catch (e: Throwable) { DiagLog.swallow("LiveProgress.promote", e) }
        if (shortText.isNotEmpty()) {
            try { Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java).invoke(b, shortText) }
            catch (e: Throwable) { DiagLog.swallow("LiveProgress.shortText", e) }
        }
    }
}
