package app.baki

import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivacyTest {
    @Test
    fun discreetMasksDisplayButNeverExports() {
        val old = BakiPrivacy.discreet
        try {
            BakiPrivacy.discreet = true
            assertEquals("••••", rm(12_345))
            assertEquals("123.45", rmRaw(12_345))
            BakiPrivacy.discreet = false
            assertEquals("123.45", rm(12_345))
        } finally {
            BakiPrivacy.discreet = old
        }
    }

    @Test
    fun meaningfulTypeIsAtLeastFourteenSp() {
        assertTrue(BakiType.body.fontSize >= 14.sp)
        assertTrue(BakiType.bodyStrong.fontSize >= 14.sp)
        assertTrue(BakiType.label.fontSize >= 14.sp)
        assertTrue(BakiType.labelStrong.fontSize >= 14.sp)
        assertTrue(BakiType.caption.fontSize >= 14.sp)
        assertTrue(BakiType.captionStrong.fontSize >= 14.sp)
        assertTrue(BakiType.bodySmall.fontSize >= 14.sp)
    }
}
