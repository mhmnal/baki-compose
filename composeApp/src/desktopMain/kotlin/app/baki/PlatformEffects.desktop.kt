package app.baki

/** Desktop has no shade and no vibration: the live shift lives in the window only. */
actual object PlatformEffects {
    actual fun requestLiveWidget() {}
    actual fun haptic(kind: Haptic) {}
    actual fun liveChanged(startMs: Long?, totalCents: Long) = Unit
    actual fun log(msg: String) = println(msg)
    actual fun dataChanged() = Unit

    actual fun exportFile(name: String, mime: String, content: String) {
        val f = java.io.File(System.getProperty("user.home"), "baki-$name")
        f.writeText(content)
        println("exported to ${f.absolutePath}")
    }
    actual fun tapFeedback() = Unit
    actual fun successFeedback() = Unit
}

/** No Monet on the desktop. */
actual fun dynamicPalette(dark: Boolean): BakiPalette? = null
