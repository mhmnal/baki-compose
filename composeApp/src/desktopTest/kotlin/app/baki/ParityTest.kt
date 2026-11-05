package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Income entries, editing, and vehicle costs — the four parity gaps. */
class ParityTest {

    private val base = System.getProperty("baki.parityServer") ?: "http://127.0.0.1:8814"

    private suspend fun repo(): BakiRepo? {
        val client = TrackerClient(base)
        return try { client.pull()!!; BakiRepo(ClientConfig(serverUrl = base, allowWrites = true)) } catch (e: Exception) {
            println("[parity] no server at $base — skipping (${e.message})"); null
        }
    }

    @Test
    fun incomeCountsAsIncomeNotExpense() = runBlocking {
        val repo = repo() ?: return@runBlocking
        val client = TrackerClient(base)
        val before = client.pull()!!
        repo.sync()

        val day = "2026-12-10"
        val beforeTotals = dayTotals(repo.store, day)
        repo.addEntry("income", 2500, "tips", "tip run", day)
        repo.sync()
        val after = dayTotals(repo.store, day)
        assertEquals(beforeTotals.income + 2500, after.income, "income raised income")
        assertEquals(beforeTotals.expense, after.expense, "and did not touch expense")

        val saved = client.pull()!!.entries.firstOrNull { it.note == "tip run" }
        assertTrue(saved != null && saved.type == "income", "server has it as income")
        client.push(client.pull()!!, deletedIds = listOf(saved!!.id))
        assertEquals(before.entries.size, client.pull()!!.entries.size, "cleaned up")
    }

    @Test
    fun editingAnEntryKeepsItsIdAndWinsTheMerge() = runBlocking {
        val repo = repo() ?: return@runBlocking
        val client = TrackerClient(base)
        repo.sync()
        repo.addEntry("expense", 1000, "food", "edit-me", "2026-12-11")
        val created = repo.store.entries.first { it.note == "edit-me" }

        repo.updateEntry(created, "expense", 1750, "toll", "edited", "2026-12-11")
        repo.sync()

        val saved = client.pull()!!.entries.first { it.id == created.id }
        assertEquals(1750L, saved.amount, "amount changed")
        assertEquals("toll", saved.cat, "category changed")
        assertEquals("edited", saved.note, "note changed")
        assertTrue(saved.updated > created.updated, "newer timestamp so it wins the merge")

        client.push(client.pull()!!, deletedIds = listOf(saved.id))
        assertTrue(client.pull()!!.entries.none { it.id == saved.id }, "cleaned up")
    }

    @Test
    fun editingAShiftRecomputesCover() = runBlocking {
        val repo = repo() ?: return@runBlocking
        val client = TrackerClient(base)
        repo.sync()

        repo.addShift("2026-12-12", "08:00", hours = 2.0, km = 40.0, grossCents = 6000)
        val shift = repo.store.shifts.first { it.date == "2026-12-12" }

        repo.updateShift(shift, "2026-12-12", "08:00", hours = 5.0, km = 90.0, grossCents = 12000, extraCents = 300, note = "longer", kmPerLitre = 14.0)
        repo.sync()

        val saved = client.pull()!!.shifts.first { it.id == shift.id }
        assertEquals(12000L, saved.gross)
        assertEquals(5.0, saved.hours)
        assertEquals(90.0, saved.km)
        assertEquals(14.0, saved.kmPerLitre, "per-shift km/L override kept")
        val m = shiftMetrics(saved, repo.store.settings.profit)
        assertTrue(m.fuel < 90.0 * 1.99 / 11.0, "14 km/L uses less fuel than 11 km/L")

        val feeId = "fee-${saved.id}"
        client.push(client.pull()!!, deletedIds = listOf(saved.id, feeId))
        assertTrue(client.pull()!!.shifts.none { it.id == saved.id }, "cleaned up")
    }

    @Test
    fun changingVehicleCostsMovesEveryShift() = runBlocking {
        val repo = repo() ?: return@runBlocking
        val client = TrackerClient(base)
        repo.sync()
        val original = repo.store.settings.profit
        val before = repo.store.shifts.map { shiftMetrics(it, original).net }.sum()

        repo.updateProfit(original.copy(fuelPrice = 3.50))
        repo.sync()
        val after = repo.store.shifts.map { shiftMetrics(it, repo.store.settings.profit).net }.sum()
        assertTrue(after < before, "dearer fuel lowers net everywhere")
        assertEquals(3.50, client.pull()!!.settings.profit.fuelPrice, "saved on the server")

        repo.updateProfit(original)
        repo.sync()
        assertEquals(original.fuelPrice, client.pull()!!.settings.profit.fuelPrice, "restored")
    }
}
