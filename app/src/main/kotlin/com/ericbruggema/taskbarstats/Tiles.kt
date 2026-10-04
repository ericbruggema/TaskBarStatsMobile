package com.ericbruggema.taskbarstats

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Welke tegels het dashboard en de cockpit tonen, en in welke volgorde (zoals Tiles op Windows). */
object Tiles {
    val ALL = listOf("mem", "net", "storage", "ping", "temp", "cpu", "wifi", "wifidata", "mobiledata", "uptime")

    /** Kleine tegels staan op het dashboard twee naast elkaar als ze elkaar opvolgen. */
    fun isSmall(id: String) = id == "ping" || id == "temp" || id == "uptime" || id == "wifidata" || id == "mobiledata"

    var order by mutableStateOf(ALL)
        private set
    var hidden by mutableStateOf(emptySet<String>())
        private set

    /** Periode van de datategels en -cellen: 1 = vandaag, 7 of 30 dagen. */
    var dataDays by mutableStateOf(1)
        private set

    fun setDataDays(ctx: Context, d: Int) { dataDays = d; StatusBarOverlay.prefs(ctx).edit().putInt("data_days", d).apply() }

    fun visible() = order.filter { it !in hidden }

    fun load(ctx: Context) {
        val p = StatusBarOverlay.prefs(ctx)
        val saved = p.getString("tile_order", null)?.split(",")?.filter { it in ALL }.orEmpty()
        order = (saved + ALL.filter { it !in saved }).distinct()   // nieuwe tegels komen achteraan
        dataDays = p.getInt("data_days", 1).takeIf { it in listOf(1, 7, 30) } ?: 1
        hidden = p.getString("tile_hidden", "")!!.split(",").filter { it in ALL }.toSet()
    }

    private fun save(ctx: Context) {
        StatusBarOverlay.prefs(ctx).edit()
            .putString("tile_order", order.joinToString(",")).putString("tile_hidden", hidden.joinToString(",")).apply()
    }

    fun toggle(ctx: Context, id: String) { hidden = if (id in hidden) hidden - id else hidden + id; save(ctx) }

    fun move(ctx: Context, id: String, delta: Int) {
        val i = order.indexOf(id); val j = i + delta
        if (i < 0 || j !in order.indices) return
        order = order.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        save(ctx)
    }

    fun reset(ctx: Context) { order = ALL; hidden = emptySet(); save(ctx) }
}
