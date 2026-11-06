package app.baki

/**
 * What this device can actually do. The desktop never had GPS, routes, widgets or a share sheet;
 * saying so in one place keeps screens from pretending otherwise.
 */
expect object Caps {
    val isDesktop: Boolean
    val hasGps: Boolean
    val hasRoutes: Boolean
    val hasWidgets: Boolean
    val hasShareSheet: Boolean
}
