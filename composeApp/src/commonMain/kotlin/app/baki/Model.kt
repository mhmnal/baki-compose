package app.baki

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Mirror of the JSON the tracker server stores. Field names and units are unchanged:
 * money is integer cents, distance is km, hours is decimal hours.
 */
@Serializable
data class Entry(
    val id: String,
    val date: String,
    val type: String = "expense",
    val cat: String = "other",
    val amount: Long,
    val note: String = "",
    /** last edit, used by sync */
    val updated: String = "",
    /** immutable creation time; blank on rows imported before this field existed */
    val loggedAt: String = "",
    val odo: Int? = null,
    val sample: Boolean? = null,
)

@Serializable
data class Payout(val c: Long, val at: Long)

@Serializable
data class Shift(
    val id: String,
    val date: String,
    val start: String = "",
    val hours: Double = 0.0,
    val odoStart: Double? = null,
    val odoEnd: Double? = null,
    val km: Double = 0.0,
    val gross: Long = 0,
    val incentive: Long = 0,
    val extra: Long = 0,
    /** Grab paid these tolls back; the amount stays recorded but is not an operating cost. */
    val reimbursedTolls: Boolean = false,
    @SerialName("kmPerLitre") val kmPerLitre: Double? = null,
    val grabFee: Long? = null,
    val note: String = "",
    val updated: String = "",
    val payouts: List<Payout> = emptyList(),
    val sample: Boolean? = null,
)

/** A recurring commitment. Its payment is an ordinary expense plus a [BillPayment] link. */
@Serializable data class Bill(
    val id: String,
    val name: String,
    /** cents */
    val amount: Long,
    /** "monthly" or "annual" */
    val cadence: String = "monthly",
    val category: String,
    /** deductible on the tax pack (phone, subscriptions) */
    val business: Boolean = false,
    /** a leftover goal, never a due bill (e.g. the car loan the family covers) */
    val optional: Boolean = false,
    /** annual bills renew in this month (1..12) */
    val anchorMonth: Int? = null,
    val updated: String = "",
    /** soft delete: bills have no tombstone, so removal is a flag the server keeps */
    val active: Boolean = true,
)

/** "This bill's 2026-12 (or 2026) period was paid by this entry." */
@Serializable data class BillPayment(
    val billId: String,
    /** "YYYY-MM" for monthly, "YYYY" for annual */
    val period: String,
    val amount: Long,
    val entryId: String,
    val paidAt: String,
    val updated: String = "",
    /** unlinking clears the flag instead of deleting the row, so the removal syncs */
    val active: Boolean = true,
)

@Serializable data class Cat(val id: String, val name: String, val icon: String)

@Serializable
data class Cats(
    @SerialName("in") val catsIn: List<Cat> = emptyList(),
    @SerialName("out") val catsOut: List<Cat> = emptyList(),
)

@Serializable
data class Profit(
    val fuelPrice: Double = 1.99,
    val kmPerLitre: Double = 11.0,
    val wearPerKm: Double = 0.09,
    val targetRate: Double = 18.0,
    val avgSpeed: Double = 30.0,
    val grabInsurance: Double = 7.5,
    /** fixed costs that exist whether you drive or not, spread over the hours you did drive */
    val loanMonthly: Double = 0.0,
    val roadTaxYearly: Double = 0.0,
    val carInsuranceYearly: Double = 0.0,
)

@Serializable
data class Settings(
    val name: String = "",
    val cats: Cats = Cats(),
    val profit: Profit = Profit(),
    /**
     * Multiplier applied to GPS-tracked distance when a live shift ends. Chords under-measure a
     * curving route, so the raw track usually reads short; this is calibrated against the odometer.
     */
    val gpsCorrection: Double = 1.0,
    /** Monthly spending caps in ringgit, keyed by category id. Empty = no budgets set. */
    val budgets: Map<String, Double> = emptyMap(),
    /** Optional service target. Deliberately user-set; Baki must not invent an interval. */
    val nextServiceKm: Int? = null,
    /** ISO date for the next service, or null. */
    val nextServiceOn: String? = null,
)

@Serializable
data class Tomb(
    val ids: List<String> = emptyList(),
    val cats: List<String> = emptyList(),
)

@Serializable
data class Statement(
    @SerialName("week_start") val weekStart: String,
    @SerialName("range_text") val rangeText: String = "",
    val income: Double = 0.0,
    val deductions: Double = 0.0,
    val weekly: Double = 0.0,
    @SerialName("transport_net") val transportNet: Double = 0.0,
    @SerialName("pickup_fares") val pickupFares: Double = 0.0,
    @SerialName("trip_fares") val tripFares: Double = 0.0,
    @SerialName("other_income") val otherIncome: Double = 0.0,
    val incentives: Double = 0.0,
    val tips: Double = 0.0,
    @SerialName("insurance_count") val insuranceCount: Int = 0,
    val consistent: Boolean = false,
    @SerialName("imported_at") val importedAt: String = "",
)

@Serializable
data class Store(
    val v: Int = 1,
    val updatedAt: String = "",
    val entries: List<Entry> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val statements: Map<String, Statement> = emptyMap(),
    val settings: Settings = Settings(),
    val tomb: Tomb = Tomb(),
    /** recurring commitments; payments link to ordinary expense entries */
    val bills: List<Bill> = emptyList(),
    val billPayments: List<BillPayment> = emptyList(),
)

val BakiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
