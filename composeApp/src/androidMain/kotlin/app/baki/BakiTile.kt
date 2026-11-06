package app.baki

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: start a shift, or pause/resume the one running — without opening the app.
 * Ending a shift stays in the app, because it writes a record and pushes it.
 */
class BakiTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        LocalStore.attach(filesDir)
        refresh()
    }

    override fun onClick() {
        super.onClick()
        LocalStore.attach(filesDir)
        val live = loadLive()
        when {
            live == null -> {
                val started = LiveShift(id = newId("r"), start = nowMillis())
                saveLive(started)
                LiveShiftService.update(this, started.start, 0)
            }
            isPaused(live) -> {
                saveLive(live.copy(pauses = closeOpen(live)))
                LiveShiftService.update(this, live.start, liveTotalCents(live))
            }
            else -> {
                saveLive(live.copy(pauses = live.pauses + LivePause(from = nowMillis())))
                LiveShiftService.update(this, live.start, liveTotalCents(live))
            }
        }
        refresh()
    }

    private fun closeOpen(l: LiveShift): List<LivePause> {
        val now = nowMillis()
        return l.pauses.mapIndexed { i, p ->
            if (i == l.pauses.lastIndex && p.to == null) p.copy(to = now) else p
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val live = loadLive()
        tile.state = when {
            live == null -> Tile.STATE_INACTIVE
            isPaused(live) -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        tile.label = when {
            live == null -> "Start shift"
            isPaused(live) -> "Resume"
            else -> "Pause"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_baki)
        tile.contentDescription = tile.label
        tile.updateTile()
    }
}
