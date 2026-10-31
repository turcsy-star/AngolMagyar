package hu.angolmagyar.fordito

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair as AndroidPair

/**
 * Egységes, látványos képernyőváltások + opcionális shared-element átmenet.
 */
object NavTransitions {

    fun open(ctx: Context, intent: Intent, shared: View? = null, transitionName: String? = null) {
        if (shared != null && transitionName != null && ctx is Activity) {
            shared.transitionName = transitionName
            intent.putExtra(EXTRA_SHARED, transitionName)
            val opts = ActivityOptionsCompat.makeSceneTransitionAnimation(
                ctx, AndroidPair.create(shared, transitionName)
            )
            ctx.startActivity(intent, opts.toBundle())
        } else {
            val opts = ActivityOptionsCompat.makeCustomAnimation(
                ctx, R.anim.screen_in, R.anim.screen_hold
            )
            ctx.startActivity(intent, opts.toBundle())
        }
        if (ctx is Activity && shared == null) {
            // soft fade for return path is set in themes / overridePendingTransition fallback
            if (Build.VERSION.SDK_INT < 34) {
                @Suppress("DEPRECATION")
                ctx.overridePendingTransition(R.anim.screen_in, R.anim.screen_hold)
            }
        }
    }

    fun open(ctx: Context, cls: Class<*>, shared: View? = null, transitionName: String? = null) {
        open(ctx, Intent(ctx, cls), shared, transitionName)
    }

    fun finishWithSlide(activity: Activity) {
        activity.finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.anim.screen_hold, R.anim.screen_out)
        }
    }

    const val EXTRA_SHARED = "nav_shared_name"
}
