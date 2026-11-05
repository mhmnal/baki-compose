package app.baki

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/** The shared-element scope for the whole app; null when transitions aren't available. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }

/** The visibility scope of the screen currently on stage (provided per tab). */
val LocalAnimatedVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }
