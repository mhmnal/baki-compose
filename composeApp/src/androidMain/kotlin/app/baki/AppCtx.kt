package app.baki

import android.content.Context

/** Application context, set once in MainActivity so the service can be reached from common code. */
object AppCtx {
    var ctx: Context? = null
}
