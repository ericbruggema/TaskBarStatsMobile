package com.ericbruggema.taskbarstats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon

/**
 * De onderdelen die je in de statusbalk kunt zien. Dezelfde keuze geldt voor de iconen naast de klok (één melding per
 * onderdeel, met de waarde in het icoon getekend) en voor de tekststrook (overlay).
 */
object StatusItems {
    val ALL = listOf("down", "up", "mem", "cpu", "disk", "ping", "temp")

    /** Wat er op een icoon of in een cel staat: een korte kop en de waarde. */
    class Face(val top: String, val value: String, val color: Int)

    private const val DEFAULT = "down,up,mem"

    fun selected(ctx: Context): List<String> {
        val raw = StatusBarOverlay.prefs(ctx).getString("items", DEFAULT)!!.split(",")
        return ALL.filter { it in raw }
    }

    fun setSelected(ctx: Context, ids: Collection<String>) =
        StatusBarOverlay.prefs(ctx).edit().putString("items", ALL.filter { it in ids }.joinToString(",")).apply()

    /** Eén statusbalkicoon dat langs de gekozen onderdelen wisselt in plaats van een icoon per onderdeel. */
    fun rotate(ctx: Context) = StatusBarOverlay.prefs(ctx).getBoolean("rotate", false)

    /** Hoeveel statusbalkiconen er naast de klok staan (één bij wisselen). */
    fun iconCount(ctx: Context) = if (!iconsOn(ctx)) 0 else if (rotate(ctx)) 1 else selected(ctx).size

    fun iconsOn(ctx: Context) = StatusBarOverlay.prefs(ctx).getBoolean("icons", false)

    fun nameRes(id: String) = when (id) {
        "down" -> R.string.download; "up" -> R.string.upload; "mem" -> R.string.memory; "cpu" -> R.string.cpu
        "disk" -> R.string.storage; "ping" -> R.string.ping; else -> R.string.temperature
    }

    fun face(id: String, s: Snapshot): Face = when (id) {
        "down" -> Face("↓", Fmt.rateShort(s.rxBps), StatsRenderer.OVERLAY_NET)
        "up" -> Face("↑", Fmt.rateShort(s.txBps), StatsRenderer.OVERLAY_UP)
        "mem" -> Face("RAM", Fmt.percent(s.memPercent), StatsRenderer.OVERLAY_MEM)
        "cpu" -> Face("CPU", s.cpuPercent?.let { Fmt.percent(it) } ?: "—", StatsRenderer.OVERLAY_CPU)
        "disk" -> Face("DISK", Fmt.percent(s.storagePercent), StatsRenderer.OVERLAY_DISK)
        "ping" -> Face("PING", s.pingMs?.let { "${it}ms" } ?: "—", StatsRenderer.OVERLAY_PING)
        else -> Face("TEMP", s.tempC?.let { String.format("%.0f°", it) } ?: "—", StatsRenderer.OVERLAY_TEMP)
    }

    /** De regel onder de titel van de melding van één onderdeel. */
    fun detail(id: String, s: Snapshot): String = when (id) {
        "down" -> Fmt.rate(s.rxBps)
        "up" -> Fmt.rate(s.txBps)
        "mem" -> "${Fmt.percent(s.memPercent)} · ${Fmt.bytes(s.memUsed)} / ${Fmt.bytes(s.memTotal)}"
        "cpu" -> s.cpuPercent?.let { Fmt.percent(it) } ?: "—"
        "disk" -> "${Fmt.percent(s.storagePercent)} · ${Fmt.bytes(s.storageTotal - s.storageUsed)}"
        "ping" -> s.pingMs?.let { "$it ms" } ?: "—"
        else -> s.tempC?.let { String.format("%.0f °C", it) } ?: "—"
    }

    /** Statusbalkicoon: kop klein boven, waarde groot eronder; wit op transparant (Android kleurt het zelf). */
    fun icon(face: Face): Icon {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        p.textSize = 38f
        while (p.measureText(face.top) > 94f && p.textSize > 16f) p.textSize -= 2f
        c.drawText(face.top, 48f, 36f, p)
        p.textSize = 62f
        while (p.measureText(face.value) > 94f && p.textSize > 20f) p.textSize -= 2f
        c.drawText(face.value, 48f, 92f, p)
        return Icon.createWithBitmap(b)
    }
}
