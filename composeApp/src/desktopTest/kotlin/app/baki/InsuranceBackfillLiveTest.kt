package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in maintenance run: re-derives the insurance fees on the *live* store and pushes the correction
 * through the normal merge path. Used once when the cover rule changed (renewals while driving were
 * not being charged retroactively). Skipped unless BAKI_BACKFILL=1.
 */
class InsuranceBackfillLiveTest {

    @Test
    fun backfillInsuranceFees() = runBlocking {
        if (System.getenv("BAKI_BACKFILL") != "1") return@runBlocking
        val client = TrackerClient("http://127.0.0.1:8787")
        val before = client.pull() ?: error("server returned nothing")
        val fee = (before.settings.profit.grabInsurance * 100).roundToLong()
        val (fixed, changed) = recomputeCover(before)
        assertTrue(changed, "nothing to correct")
        val windows = coverWindows(fixed.shifts).values.sum()
        val expected = windows * fee
        println("[backfill] fee=$fee windows=$windows expected=$expected " +
            "recorded=${before.shifts.sumOf { it.grabFee ?: 0 }}")

        // a corrected shift must look newer than the stored one, or an equal-timestamp merge could
        // resurrect the old fee
        val now = nowIso()
        val pushed = fixed.copy(
            shifts = fixed.shifts.map { s ->
                val old = before.shifts.firstOrNull { it.id == s.id }?.grabFee ?: 0
                if (old != (s.grabFee ?: 0)) s.copy(updated = now) else s
            },
            updatedAt = now,
        )
        // a fee entry may have been tombstoned when the old rule zeroed its shift; tombstones win,
        // so the correction has to undelete the ids it legitimately needs again
        val stored = before.tomb.ids
        val needed = pushed.entries.filter { it.cat == "grabfee" }.map { it.id }
        val resurrect = needed.filter { it in stored }
        if (resurrect.isNotEmpty()) println("[backfill] undeleting ${resurrect.size}: $resurrect")
        val merged = client.push(pushed, undeleteIds = resurrect)
        val after = client.pull() ?: merged
        val shiftFees = after.shifts.sumOf { it.grabFee ?: 0 }
        val entryFees = after.entries.filter { it.cat == "grabfee" }.sumOf { it.amount }
        println("[backfill] after: shift fees=$shiftFees entries=$entryFees windows=" +
            coverWindows(after.shifts).values.sum())

        assertEquals(expected, shiftFees, "shift fees were not corrected")
        assertEquals(shiftFees, entryFees, "linked fee entries disagree with the shifts")
        assertEquals(6, coverWindows(after.shifts).values.sum(), "unexpected window count")
    }
}
