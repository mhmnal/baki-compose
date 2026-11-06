package app.baki

import android.content.Context

/** Application context, set once in MainActivity so the service can be reached from common code. */
object AppCtx {
    /** Widgets and receivers run in a fresh process: attach the store before reading it. */
    fun attach(ctx: Context) {
        this.ctx = ctx.applicationContext
        LocalStore.attach(ctx.filesDir)
    }

    var ctx: Context? = null
}
