package app.baki

import android.content.Context

/** The application context handed to the tracker, so a watchdog restart does not need a caller. */
object WatchdogContext {
    var ctx: Context? = null
}
