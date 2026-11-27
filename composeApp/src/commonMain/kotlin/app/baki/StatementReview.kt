package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The week that should open for review immediately after a statement import. */
object StatementReview {
    var pendingWeek by mutableStateOf<String?>(null)
}
