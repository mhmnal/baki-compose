package app.baki

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic .verify build: separate application id, unreachable test server, fresh store.
 * This is the Android-only lane; Xvfb never exercises these surfaces.
 */
@RunWith(AndroidJUnit4::class)
class BakiSmokeTest {
    @get:Rule(order = 0)
    val locationPermission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 1)
    val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun restoredBackupCountsArePresent() {
        val fixture = app.activity.getExternalFilesDir(null)?.resolve("baki-restore-test.json") ?: java.io.File("/sdcard/Download/baki-restore-test.json")
        org.junit.Assume.assumeTrue("restore fixture not supplied", fixture.exists())
        val restored = loadLocal()
        org.junit.Assert.assertEquals(106, restored.entries.size)
        org.junit.Assert.assertEquals(16, restored.shifts.size)
    }

    @Test
    fun prefilledStatementEntrySavesOnPhone() {
        org.junit.Assume.assumeTrue("restore fixture loaded; prefill fixture is separate", !(app.activity.getExternalFilesDir(null)?.resolve("baki-restore-test.json")?.exists() ?: false))
        app.onNodeWithContentDescription("More").performClick()
        app.waitForIdle()
        app.onNodeWithTag("statement-prefill").performClick()
        app.onNodeWithText("Add statement expense").assertIsDisplayed()
        app.onNodeWithText("Save on phone").performClick()
        app.waitForIdle()
        app.onNodeWithText("Add statement expense").assertDoesNotExist()
        app.onNodeWithText("2 matched · 0 unrecorded").assertIsDisplayed()
    }

    @Test
    fun phoneShellNavigationAndMoreTabsWork() {
        app.onNodeWithText("Baki").assertIsDisplayed()

        app.onNodeWithContentDescription("Shifts").performClick()
        app.onNodeWithText("WHAT YOU ACTUALLY MADE").assertIsDisplayed()

        app.onNodeWithContentDescription("More").performClick()
        app.onNodeWithText("Money").assertIsDisplayed()
        app.onNodeWithText("Statement reconciliation").assertIsDisplayed()
        app.onNodeWithText("Work").performClick()
        app.onNodeWithText("Payment QR & service").assertIsDisplayed()

        app.onNodeWithText("App").performClick()
        app.waitForIdle()
        app.onNodeWithText("Server address").assertIsDisplayed()
    }
}
