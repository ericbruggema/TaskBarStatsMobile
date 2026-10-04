package com.ericbruggema.taskbarstats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent

/**
 * Piepkleine statistieken in de statusbalk, naast klok / camera-gaatje / systeemiconen.
 * Android laat apps niet in de statusbalk tekenen; dit is een niet-aanraakbaar overlay-venster
 * (toestemming "Weergeven over andere apps") dat op de hoogte van de statusbalk staat.
 * Wordt ververst door [MonitorService].
 */
object StatusBarOverlay {
    const val LEFT = 0
    const val RIGHT = 2

    private var view: ImageView? = null
    private var lp: WindowManager.LayoutParams? = null

    fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("overlay", false)
    fun position(ctx: Context) = if (prefs(ctx).getInt("overlay_pos", RIGHT) == LEFT) LEFT else RIGHT
    /** Fijnafstelling in dp (positief = naar rechts), met de pijltjes in de app. */
    fun nudge(ctx: Context) = prefs(ctx).getInt("overlay_dx", 0)
    fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun permissionIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun statusBarHeight(ctx: Context): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else (24 * ctx.resources.displayMetrics.density).toInt()
    }

    /** Zet het venster aan/uit en ververst het bitmapje; goedkoop genoeg om elke 2 s te draaien. */
    fun update(ctx: Context, s: Snapshot) {
        if (!enabled(ctx) || !canDraw(ctx)) { remove(ctx); return }
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = ctx.resources.displayMetrics
        val h = statusBarHeight(ctx)
        val pos = position(ctx)
        // staan de onderdelen ook als iconen naast de klok (~20 dp per icoon), dan begint de strook daarachter
        val dx = nudge(ctx) + if (StatusItems.iconsOn(ctx) && pos == LEFT) 20 * StatusItems.selected(ctx).size else 0
        // ruimte tussen klok/meldingsiconen (~112 dp) en systeemiconen (~104 dp breed vanaf rechts), met de verschuiving erbij
        val free = if (pos == LEFT) d.widthPixels - (112 + dx + 100) * d.density else d.widthPixels - (104 - dx + 112) * d.density
        val items = StatusItems.selected(ctx)
        if (items.isEmpty()) { remove(ctx); return }
        val bmp = render(s, h, d.density, free.coerceAtLeast(120 * d.density), items)
        val w = bmp.width
        if (view == null) {
            view = ImageView(ctx).also {
                it.scaleType = ImageView.ScaleType.FIT_XY
                // Fullscreen-detectie: verdwijnt de statusbalk (video, game, cockpit), dan verbergen we het pilletje ook
                ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                    v.alpha = if (insets.isVisible(WindowInsetsCompat.Type.statusBars())) 1f else 0f
                    insets
                }
            }
            lp = WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).also { it.y = 0; wm.addView(view, it); view!!.requestApplyInsets() }
        }
        val p = lp!!
        p.width = w
        when (pos) {
            LEFT -> { p.gravity = Gravity.TOP or Gravity.START; p.x = ((112 + dx) * d.density).toInt() }
            else -> { p.gravity = Gravity.TOP or Gravity.END; p.x = ((104 - dx) * d.density).toInt() }
        }
        wm.updateViewLayout(view, p)
        view!!.setImageBitmap(bmp)
    }

    fun remove(ctx: Context) {
        val v = view ?: return
        try { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) { }
        view = null; lp = null
    }

    /**
     * Tweeregelige cellen voor de gekozen onderdelen. Elke cel is minstens zo breed als zijn langste mogelijke
     * tekst, zodat het pilletje niet heen en weer springt als de waarden veranderen. Download en upload samen
     * vormen één cel (twee regels); alleen één van beide krijgt een eigen cel.
     */
    private fun render(s: Snapshot, h: Int, density: Float, maxW: Float, items: List<String>): Bitmap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = h * 0.27f
        class Cell(val key: String, val top: String, val topColor: Int, val bottom: String, val bottomColor: Int, val minText: String)
        val cells = ArrayList<Cell>()
        for (id in items) {
            val f = StatusItems.face(id, s)
            when {
                id == "down" && "up" in items -> {
                    val u = StatusItems.face("up", s)
                    cells += Cell("net", "↓" + f.value, f.color, "↑" + u.value, u.color, "↑888.8K")
                }
                id == "up" && "down" in items -> { }
                id == "down" || id == "up" -> cells += Cell(id, if (id == "down") "DOWN" else "UP", f.color, f.top + f.value, StatsRenderer.OVERLAY_FG, "↑888.8K")
                else -> cells += Cell(id, f.top, f.color, f.value, StatsRenderer.OVERLAY_FG, if (id == "ping") "888ms" else if (id == "temp") "88°" else "100%")
            }
        }
        var pad = 6f * density; var gap = 7f * density
        val shown = cells.toMutableList()
        fun layout() = shown.map { maxOf(p.measureText(it.top), p.measureText(it.bottom), p.measureText(it.minText)) }
        var widths = layout()
        var total = pad * 2 + gap * (shown.size - 1) + widths.sum()
        // te weinig ruimte: eerst tot 80% kleiner, dan cellen weglaten (CPU eerst, dan ping)
        if (total > maxW) {
            val k = (maxW / total).coerceAtLeast(0.8f)
            p.textSize *= k; pad *= k; gap *= k
            widths = layout(); total = pad * 2 + gap * (shown.size - 1) + widths.sum()
            for (drop in listOf("cpu", "ping", "temp", "disk")) {
                if (total <= maxW) break
                shown.removeAll { it.key == drop }
                widths = layout(); total = pad * 2 + gap * (shown.size - 1) + widths.sum()
            }
        }
        val w = total.toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        p.color = 0xCC000000.toInt()
        c.drawRoundRect(0f, h * 0.12f, w.toFloat(), h * 0.88f, h * 0.2f, h * 0.2f, p)
        var x = pad
        shown.forEachIndexed { i, cell ->
            p.color = cell.topColor; c.drawText(cell.top, x, h * 0.45f, p)
            p.color = cell.bottomColor; c.drawText(cell.bottom, x, h * 0.78f, p)
            x += widths[i] + gap
        }
        return bmp
    }
}
