package com.ericbruggema.taskbarstats

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings-tegel: toont geheugen en netwerk op het label, tik = live-monitor aan/uit.
 * Verversing loopt alleen zolang het paneel open is (onStartListening/onStopListening).
 */
class StatsTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private val tick = object : Runnable {
        override fun run() { refresh(); if (listening) handler.postDelayed(this, 1000) }
    }

    override fun onStartListening() {
        listening = true
        Sampler.acquire(this)
        handler.post(tick)
    }

    override fun onStopListening() {
        listening = false
        handler.removeCallbacks(tick)
        Sampler.release()
    }

    override fun onClick() {
        if (MonitorService.running) MonitorService.stop(this) else MonitorService.start(this)
        handler.postDelayed({ refresh() }, 300)
    }

    private fun refresh() {
        val t = qsTile ?: return
        val s = Sampler.snapshot
        t.label = "RAM ${Fmt.percent(s.memPercent)}"
        if (android.os.Build.VERSION.SDK_INT >= 29)
            t.subtitle = "\u2193${Fmt.rateShort(s.rxBps)} \u2191${Fmt.rateShort(s.txBps)}"
        t.state = if (MonitorService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
