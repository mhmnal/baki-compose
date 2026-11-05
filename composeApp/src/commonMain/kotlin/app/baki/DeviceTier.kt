package app.baki

/**
 * How much visual work this device can afford. Currently unused: glass is Material-only until the
 * toolchain can reach compileSdk 37 for Haze (see DESIGN.md). Kept because the tiering logic is
 * the part that took the thinking.
 *
 * How much visual work this device can afford.
 *   LOW  — no backdrop recording, solid surfaces (Material default)
 *   MID  — layered lensing glass
 *   HIGH — layered lensing + chromatic fringing
 */
expect object DeviceTier {
    /** 0 = low, 1 = mid, 2 = high */
    val level: Int
    /** Human-readable reason, shown in More. */
    val reason: String
}

/** Resolves the user's preference ("auto" | "on" | "off") against the hardware. */
fun effectiveGlassTier(mode: String, device: Int): Int = when (mode) {
    // "on" forces the Haze path even on a device that would not pick it
    "on" -> if (device <= 0) 2 else device
    // "auto" currently means Material: Haze builds and runs, but the refraction is not visible
    // yet, so paying for it by default is not justified. Flip to "on" in More to try it.
    else -> 0
}
