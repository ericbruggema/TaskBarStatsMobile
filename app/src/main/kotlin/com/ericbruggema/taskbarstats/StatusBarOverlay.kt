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
import android.os.Handler
import android.os.Looper

/**
 * Piepkleine statistieken in de statusbalk, naast klok / camera-gaatje / systeemiconen.
 * Android laat apps niet in de statusbalk tekenen; dit is een niet-aanraakbaar overlay-venster
 * (toestemming "Weergeven over andere apps") dat op de hoogte van de statusbalk staat.
 * Wordt ververst door [MonitorService].
 */
object StatusBarOverlay {
    const val LEFT = 0
    const val CENTER = 1
    const val RIGHT = 2

    // twee vensters: bij "midden" met een cameragat staat er één links en één rechts van de camera
    private val views = arrayOfNulls<ImageView>(3)   // 0 en 1 = strook, 2 = hulpmarkering van het vrije gebied
    private val lps = arrayOfNulls<WindowManager.LayoutParams>(3)

    fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("overlay", false)
    fun position(ctx: Context) = when (prefs(ctx).getInt("overlay_pos", RIGHT)) { LEFT -> LEFT; CENTER -> CENTER; else -> RIGHT }
    /** Fijnafstelling in dp (positief = naar rechts), met de pijltjes in de app. */
    fun nudge(ctx: Context) = prefs(ctx).getInt("overlay_dx", 0)
    fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)
    /** Camera-gaatje (selfiecamera) ontwijken: standaard aan. */
    /** Cellen weglaten als de ruimte te krap is (CPU eerst, dan ping): standaard uit, een gekozen onderdeel blijft dan staan. */
    fun autoHide(ctx: Context) = prefs(ctx).getBoolean("strip_autohide", false)
    fun avoidCutout(ctx: Context) = prefs(ctx).getBoolean("avoid_cutout", true)

    /** Camera automatisch herkennen (standaard) of zelf de breedte en plek instellen. Alle maten in pixels van het scherm. */
    fun camAuto(ctx: Context) = prefs(ctx).getBoolean("cam_auto", true)
    fun camWidth(ctx: Context) = prefs(ctx).getInt("cam_w", 100)
    /** Vrije ruimte links én rechts van de camera. */
    fun camMargin(ctx: Context) = prefs(ctx).getInt("cam_m", 8)
    fun camShift(ctx: Context) = prefs(ctx).getInt("cam_s", 0)

    /** Wat Android over het cameragat meldt (voor de weergave in de app), of null. */
    fun detected(view: android.view.View): android.graphics.Rect? {
        if (android.os.Build.VERSION.SDK_INT < 28) return null
        val r = view.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull { it.top <= 0 && it.width() > 0 } ?: return null
        return android.graphics.Rect(r)
    }

    /**
     * De camera zelf (pixels van links, op de plek die de gebruiker koos): het gemelde cameragat, of de zelf ingestelde
     * breedte in het midden als automatisch herkennen uit staat of de telefoon niets meldt.
     */
    private fun cameraBase(ctx: Context, wm: WindowManager): android.graphics.Rect? {
        if (!avoidCutout(ctx)) return null
        val d = ctx.resources.displayMetrics
        val r = (if (camAuto(ctx)) cutoutTop(wm) else null) ?: run {
            val half = camWidth(ctx) / 2; val c = d.widthPixels / 2
            android.graphics.Rect(c - half, 0, c + half, statusBarHeight(ctx))
        }
        val shift = camShift(ctx)
        return android.graphics.Rect(r.left + shift, r.top, r.right + shift, r.bottom)
    }

    /** Het deel van de statusbalk dat vrij moet blijven: de camera plus de ingestelde ruimte links en rechts. */
    private fun cameraRect(ctx: Context, wm: WindowManager): android.graphics.Rect? {
        val r = cameraBase(ctx, wm) ?: return null
        val m = camMargin(ctx)
        return android.graphics.Rect(r.left - m, r.top, r.right + m, r.bottom)
    }

    private val guideHandler = Handler(Looper.getMainLooper())
    private var guideUntil = 0L

    /** Toont een paar seconden een rode markering op de échte statusbalk: donker = camera, licht = vrije ruimte. */
    private fun showGuide(ctx: Context) {
        if (!canDraw(ctx)) return
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val base = cameraBase(ctx, wm) ?: run { removeSlot(ctx, 2); return }
        val full = cameraRect(ctx, wm)!!
        val h = statusBarHeight(ctx)
        val bmp = Bitmap.createBitmap(full.width().coerceAtLeast(1), h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint()
        p.color = 0x66FF5A4D; c.drawRect(0f, 0f, bmp.width.toFloat(), h.toFloat(), p)
        p.color = 0xCCFF1A1A.toInt(); c.drawRect((base.left - full.left).toFloat(), 0f, (base.right - full.left).toFloat(), h.toFloat(), p)
        show(ctx, wm, 2, bmp, h, Gravity.TOP or Gravity.START, full.left)
        guideUntil = System.currentTimeMillis() + 3500
        guideHandler.postDelayed({ if (System.currentTimeMillis() >= guideUntil) removeSlot(ctx, 2) }, 3600)
    }

    /** Past de strook direct aan als de instellingen veranderen en toont even het vrije gebied. */
    fun refresh(ctx: Context) {
        if (enabled(ctx)) update(ctx, Sampler.snapshot)
        showGuide(ctx)
    }

    private fun cutoutTop(wm: WindowManager) = if (android.os.Build.VERSION.SDK_INT >= 28) cutoutTopApi28(wm) else null

    /** Het cameragat bovenin (in pixels van links), of null als het toestel geen uitsparing heeft. */
    @androidx.annotation.RequiresApi(28)
    private fun cutoutTopApi28(wm: WindowManager): android.graphics.Rect? {
        val cut = if (android.os.Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.windowInsets.displayCutout
        else views[0]?.rootWindowInsets?.displayCutout
        val r = cut?.boundingRects?.firstOrNull { it.top <= 0 && it.width() > 0 } ?: return null
        return android.graphics.Rect(r)
    }

    /**
     * Android zelf zet een melding "<app> wordt over andere apps weergegeven" (met het app-logo) in de statusbalk zolang
     * de strook aan staat. Een app kan die niet verwijderen; de gebruiker kan hem op veel telefoons uitzetten in de
     * instellingen van dat systeemkanaal. Deze intent opent die pagina.
     */
    fun overlayNoticeIntent(ctx: Context) =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, "android")
            .putExtra(Settings.EXTRA_CHANNEL_ID, "com.android.server.wm.AlertWindowNotification - ${ctx.packageName}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
        val dx = nudge(ctx) + if (pos == LEFT) 20 * StatusItems.iconCount(ctx) else 0
        // ruimte tussen klok/meldingsiconen (~112 dp) en systeemiconen (~104 dp breed vanaf rechts), met de verschuiving erbij
        var free = if (pos == LEFT) d.widthPixels - (112 + dx + 100) * d.density else d.widthPixels - (104 - dx + 112) * d.density
        val items = StatusItems.selected(ctx)
        if (items.isEmpty()) { remove(ctx); return }
        if (pos == CENTER) { updateCenter(ctx, wm, s, h, items); return }
        removeSlot(ctx, 1)
        // het cameragat bovenin: de strook past in de ruimte tot of na de camera, en komt er nooit overheen
        val cut = cameraRect(ctx, wm)
        var minW = 120 * d.density
        if (cut != null) {
            val gap = 0f
            if (pos == LEFT) {
                val start = (112 + dx) * d.density
                if (start < cut.right) { free = minOf(free, cut.left - gap - start); minW = 60 * d.density }
            } else {
                val end = d.widthPixels - (104 - dx) * d.density   // rechterrand van de strook
                if (end > cut.left) { free = minOf(free, end - (cut.right + gap)); minW = 60 * d.density }
            }
        }
        val bmp = render(s, h, d.density, free.coerceAtLeast(minW), items, autoHide(ctx))
        when (pos) {
            LEFT -> show(ctx, wm, 0, bmp, h, Gravity.TOP or Gravity.START, ((112 + dx) * d.density).toInt())
            else -> show(ctx, wm, 0, bmp, h, Gravity.TOP or Gravity.END, ((104 - dx) * d.density).toInt())
        }
    }

    /**
     * Midden: zonder cameragat één strook in het midden; met een cameragat de onderdelen in twee helften, links en
     * rechts van de camera (download en upload blijven bij elkaar).
     */
    private fun updateCenter(ctx: Context, wm: WindowManager, s: Snapshot, h: Int, items: List<String>) {
        val d = ctx.resources.displayMetrics
        val cut = cameraRect(ctx, wm)
        if (cut == null) {
            val free = d.widthPixels - 2 * 112 * d.density
            show(ctx, wm, 0, render(s, h, d.density, free, items, autoHide(ctx)), h, Gravity.TOP or Gravity.CENTER_HORIZONTAL, (nudge(ctx) * d.density).toInt())
            removeSlot(ctx, 1); return
        }
        val gap = 0f
        val groups = ArrayList<List<String>>()
        for (id in items) when {
            id == "up" && "down" in items -> { }
            id == "down" && "up" in items -> groups += listOf("down", "up")
            else -> groups += listOf(id)
        }
        val iconShift = 20 * StatusItems.iconCount(ctx)
        val freeL = cut.left - gap - (112 + iconShift) * d.density
        val freeR = d.widthPixels - cut.right - gap - 100 * d.density
        // verdeel de groepen zo over links en rechts dat de krapste kant het minst overschrijdt (op natuurlijke breedte)
        val nat = groups.map { render(s, h, d.density, 10_000f, it, false).width.toFloat() }
        var best = 0; var bestScore = Float.MAX_VALUE
        for (k in 0..groups.size) {
            val lw = nat.take(k).sum(); val rw = nat.drop(k).sum()
            val score = maxOf(lw / freeL.coerceAtLeast(60 * d.density), rw / freeR.coerceAtLeast(60 * d.density))
            if (score < bestScore) { bestScore = score; best = k }
        }
        val left = groups.take(best).flatten(); val right = groups.drop(best).flatten()
        if (left.isEmpty()) removeSlot(ctx, 0) else {
            val bl = render(s, h, d.density, freeL.coerceAtLeast(60 * d.density), left, autoHide(ctx))
            show(ctx, wm, 0, bl, h, Gravity.TOP or Gravity.START, (cut.left - gap - bl.width).toInt())
        }
        if (right.isEmpty()) { removeSlot(ctx, 1); return }
        val br = render(s, h, d.density, freeR.coerceAtLeast(60 * d.density), right, autoHide(ctx))
        show(ctx, wm, 1, br, h, Gravity.TOP or Gravity.START, (cut.right + gap).toInt())
    }

    /** Maakt of verplaatst overlay-venster `i` en zet het bitmapje erin. */
    private fun show(ctx: Context, wm: WindowManager, i: Int, bmp: Bitmap, h: Int, gravity: Int, x: Int) {
        val w = bmp.width
        if (views[i] == null) {
            views[i] = ImageView(ctx).also {
                it.scaleType = ImageView.ScaleType.FIT_XY
                // Fullscreen-detectie: verdwijnt de statusbalk (video, game, cockpit), dan verbergen we het pilletje ook
                ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                    v.alpha = if (insets.isVisible(WindowInsetsCompat.Type.statusBars())) 1f else 0f
                    insets
                }
            }
            lps[i] = WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).also { it.y = 0; wm.addView(views[i], it); views[i]!!.requestApplyInsets() }
        }
        val p = lps[i]!!
        p.width = w; p.gravity = gravity; p.x = x
        wm.updateViewLayout(views[i], p)
        views[i]!!.setImageBitmap(bmp)
    }

    private fun removeSlot(ctx: Context, i: Int) {
        val v = views[i] ?: return
        try { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) { }
        views[i] = null; lps[i] = null
    }

    fun remove(ctx: Context) { removeSlot(ctx, 0); removeSlot(ctx, 1); removeSlot(ctx, 2) }

    /**
     * Tweeregelige cellen voor de gekozen onderdelen. Elke cel is minstens zo breed als zijn langste mogelijke
     * tekst, zodat het pilletje niet heen en weer springt als de waarden veranderen. Download en upload samen
     * vormen één cel (twee regels); alleen één van beide krijgt een eigen cel.
     */
    private fun render(s: Snapshot, h: Int, density: Float, maxW: Float, items: List<String>, autoHide: Boolean): Bitmap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = Fonts.typeface(true)
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
        // te weinig ruimte: eerst tot 70% kleiner; cellen weglaten alleen als de gebruiker dat aanzet (CPU eerst, dan ping)
        if (total > maxW) {
            val k = (maxW / total).coerceAtLeast(0.7f)
            p.textSize *= k; pad *= k; gap *= k
            widths = layout(); total = pad * 2 + gap * (shown.size - 1) + widths.sum()
            if (autoHide) for (drop in listOf("cpu", "ping", "temp", "disk")) {
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
