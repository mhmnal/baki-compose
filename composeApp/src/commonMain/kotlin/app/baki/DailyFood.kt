package app.baki

data class DailyFood(val spent: Long, val cap: Long) {
    val remaining: Long get() = cap - spent
    val over: Boolean get() = spent > cap
}

/** The food entries on [day] against the user-set daily cap; null when no cap is set. */
fun dailyFood(store: Store, day: String): DailyFood? {
    val cap = store.settings.dailyFoodCapCents?.takeIf { it > 0 } ?: return null
    val spent = store.entries
        .filter { it.date == day && it.type != "income" && it.cat == "food" }
        .sumOf { it.amount }
    return DailyFood(spent, cap)
}
