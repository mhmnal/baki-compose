package app.baki

/**
 * Switches the app into the phone shell for offscreen captures.
 *
 * A desktop normally renders the desktop shell (`Caps.isDesktop && DesktopRoot(repo)`), which
 * makes the phone UI impossible to inspect without a device. A preview harness sets [active] and
 * [initialTab] before composing, so `App` renders the phone shell at phone size on the JVM.
 *
 * Every field is inert in shipped builds: the defaults keep the normal platform behaviour, and
 * nothing here reads state the app writes.
 */
object PhonePreview {

    /** Render the phone shell even on a desktop. */
    var active: Boolean = false

    /** Tab the phone shell opens on: 0 overview, 1 spending, 2 shifts, 3 summary, 4 more. */
    var initialTab: Int = 0

    /**
     * Desktop shell surface to open: overview, entries, shifts, routes, compare, year,
     * statement, settings. Null falls back to the `BAKI_VIEW` environment variable.
     */
    var desktopView: String? = null
}
