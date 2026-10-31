package com.jarvis.assistant.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap to start or stop background listening. */
class JarvisTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (JarvisListeningService.isRunning) {
            ListeningServiceController.stop(this)
        } else {
            ListeningServiceController.start(this)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = JarvisListeningService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(com.jarvis.assistant.R.string.tile_label)
        tile.updateTile()
    }
}
