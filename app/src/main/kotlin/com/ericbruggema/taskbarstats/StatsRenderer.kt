package com.ericbruggema.taskbarstats

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Tekent het widget naar een gewone Bitmap, net als WidgetRenderer op Windows: de home-screen-widget,
 * de melding en het voorbeeld in de app gebruiken dezelfde routine.
 */
object StatsRenderer {
    // Kleuren volgen het gekozen thema (zie Themes); de rest is vast
    val BG get() = Themes.current.bg
    val FG get() = Themes.current.text
    val DIM get() = Themes.mix(Themes.current.text, Themes.current.bg, 0.45f)
    val CARD get() = Themes.mix(Themes.current.bg, Themes.current.text, 0.08f)
    val MEM get() = Themes.current.accent
    /** Vaste reekskleuren worden op lichte thema's donkerder gemaakt, anders zijn ze niet te lezen. */
    fun tone(c: Int) = if (androidx.core.graphics.ColorUtils.calculateLuminance(BG) > 0.5) Themes.mix(c, 0xFF000000.toInt(), 0.4f) else c
    val NET get() = tone(0xFF81C784.toInt())
    val UP get() = Themes.current.warn
    /** Het statusbalk-pilletje is altijd donker, dus de tekst daarin ook altijd licht. */
    const val OVERLAY_FG = 0xFFE8EEF2.toInt()
    const val OVERLAY_MEM = 0xFF4FC3F7.toInt()
    const val OVERLAY_NET = 0xFF81C784.toInt()
    const val OVERLAY_UP = 0xFFFFB74D.toInt()
    const val OVERLAY_PING = 0xFFFFD54F.toInt()
    const val OVERLAY_CPU = 0xFF9FA8DA.toInt()
    const val OVERLAY_DISK = 0xFFBA68C8.toInt()
    const val OVERLAY_TEMP = 0xFFFF8A65.toInt()
    val DISK get() = tone(0xFFBA68C8.toInt())
    val PING get() = tone(0xFFFFD54F.toInt())
    val CPU get() = tone(0xFF9FA8DA.toInt())
    val TEMP get() = tone(0xFFFF8A65.toInt())

    private class Cell(
        val label: String, val value: String, val sub: String, val color: Int,
        val hist: List<Float>, val fixedMax: Float?, val bar: Float? = null,
    )

    /** Vertaald woord "vrij"; gezet door [Sampler.acquire] zodra er een Context is. */
    @Volatile var freeWord = "free"
    @Volatile var dataWord = "Mobile"
    /** Verbindingsnamen op index netKind (offline, wifi, mobiel, overig); gezet door [Sampler.acquire]. */
    @Volatile var connWords = arrayOf("Offline", "Wi-Fi", "Mobile data", "Other network")

    fun render(s: Snapshot, w: Int, h: Int, opts: WidgetOptions = WidgetOptions()): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = h * 0.07f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = androidx.core.graphics.ColorUtils.setAlphaComponent(BG, opts.opacity * 255 / 100)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h * 0.16f, h * 0.16f, p)

        val all = linkedMapOf(
            "mem" to Cell("MEM", Fmt.percent(s.memPercent), Fmt.bytes(s.memUsed), MEM, s.memHist, 100f),
            "net" to Cell("NET", "\u2193 " + Fmt.rate(s.rxBps), "\u2191 " + Fmt.rate(s.txBps), NET, s.rxHist, null),
            "cpu" to Cell("CPU", s.cpuPercent?.let { Fmt.percent(it) } ?: "\u2014", "", CPU, s.cpuHist, 100f),
            "disk" to Cell("DISK", Fmt.percent(s.storagePercent), Fmt.bytes(s.storageTotal - s.storageUsed) + " " + freeWord, DISK, emptyList(), null, s.storagePercent / 100f),
            "data" to Cell("DATA", s.dataWifi?.let { "Wi-Fi " + Fmt.bytes(it) } ?: "\u2014", s.dataMobile?.let { dataWord + " " + Fmt.bytes(it) } ?: "", NET, emptyList(), null),
            "temp" to Cell("TEMP", s.tempC?.let { String.format("%.0f \u00B0C", it) } ?: "\u2014", "", TEMP, s.tempHist, null),
            "conn" to Cell("LINK", connWords.getOrElse(s.netKind) { connWords[0] }, connDetail(s), NET, emptyList(), null),
            "uptime" to Cell("UPTIME", uptimeText(s.uptimeMs), "", MEM, emptyList(), null),
            "ping" to Cell("PING", s.pingMs?.let { "$it ms" } ?: "\u2014", s.tempC?.let { String.format("%.0f \u00B0C", it) } ?: "", PING, s.pingHist, null),
        )
        val cells = all.filterKeys { it in opts.cells }.values.toList().ifEmpty { listOf(all.getValue("mem")) }
        // hoge widgets (en vier of meer onderdelen) krijgen twee rijen
        val rows = if (cells.size >= 4 && h > w * 0.6f) 2 else 1
        val perRow = (cells.size + rows - 1) / rows
        val rh = (h - pad * 2) / rows
        val cw = (w - pad * 2) / perRow
        cells.chunked(perRow).forEachIndexed { r, rowCells ->
            rowCells.forEachIndexed { i, cell ->
                drawCell(c, p, cell, pad + i * cw + pad * 0.4f, pad + r * rh, cw - pad * 0.8f, rh - (if (rows > 1) pad * 0.4f else 0f), opts.graphs)
            }
        }
        return bmp
    }

    /** Verkleint de tekst tot hij in de kolom past (smalle widget, lange waarden als "1.1 MB/s"). */
    private fun fit(p: Paint, text: String, size: Float, maxW: Float): Float {
        p.textSize = size
        val w = p.measureText(text)
        return if (w > maxW) size * maxW / w else size
    }

    private fun drawCell(c: Canvas, p: Paint, cell: Cell, x: Float, y: Float, w: Float, h: Float, graphs: Boolean) {
        p.style = Paint.Style.FILL
        if (!graphs) {
            // zonder grafiekjes en balken krijgt de tekst de hele hoogte
            p.typeface = Fonts.typeface(true); p.color = cell.color; p.textSize = fit(p, cell.label, h * 0.2f, w)
            c.drawText(cell.label, x, y + h * 0.2f, p)
            p.textSize = fit(p, cell.value, h * 0.3f, w); p.color = FG
            c.drawText(cell.value, x, y + h * 0.58f, p)
            p.typeface = Fonts.typeface(false); p.color = DIM
            p.textSize = fit(p, cell.sub, h * 0.2f, w)
            c.drawText(cell.sub, x, y + h * 0.88f, p)
            return
        }
        p.typeface = Fonts.typeface(true); p.color = cell.color; p.textSize = fit(p, cell.label, h * 0.16f, w)
        c.drawText(cell.label, x, y + h * 0.16f, p)
        p.textSize = fit(p, cell.value, h * 0.2f, w); p.color = FG
        c.drawText(cell.value, x, y + h * 0.44f, p)
        p.typeface = Fonts.typeface(false); p.color = DIM
        p.textSize = fit(p, cell.sub, h * 0.15f, w)
        c.drawText(cell.sub, x, y + h * 0.62f, p)
        if (cell.hist.size > 1) {
            val top = y + h * 0.70f; val gh = h * 0.30f
            val max = cell.fixedMax ?: (cell.hist.max() * 1.2f).coerceAtLeast(1f)
            val path = Path()
            cell.hist.forEachIndexed { i, v ->
                val px = x + w * i / (cell.hist.size - 1)
                val py = top + gh * (1f - (v / max).coerceIn(0f, 1f))
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.025f; p.color = cell.color
            c.drawPath(path, p)
            p.style = Paint.Style.FILL
        } else if (cell.bar != null) {
            val top = y + h * 0.78f; val bh = h * 0.10f
            p.color = Themes.mix(BG, FG, 0.18f); c.drawRoundRect(RectF(x, top, x + w, top + bh), bh / 2, bh / 2, p)
            p.color = cell.color
            c.drawRoundRect(RectF(x, top, x + w * (if (cell.bar.isNaN()) 0f else cell.bar.coerceIn(0f, 1f)), top + bh), bh / 2, bh / 2, p)
        }
    }
}
