package com.ericbruggema.taskbarstats

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

open class StatsWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Zonder draaiende service is dit één momentopname; de MonitorService ververst elke 2 s.
        val app = ctx.applicationContext
        Sampler.acquire(app)
        Thread { Thread.sleep(1500); update(app); Sampler.release() }.start()
    }

    // Formaat veranderd: opnieuw tekenen met de juiste verhouding
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) = update(ctx)

    override fun onDeleted(ctx: Context, ids: IntArray) { ids.forEach { WidgetOptions.delete(ctx, it) } }

    companion object {
        fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = WidgetKind.entries.flatMap { it.ids(ctx).toList() }
            if (ids.isEmpty()) return
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            for (id in ids) {
                // verhouding van de widget volgen, zodat tekst niet uitrekt als je hem groter of kleiner maakt
                val o = mgr.getAppWidgetOptions(id)
                // staand is de widget minW breed en maxH hoog, liggend maxW breed en minH hoog
                val portrait = ctx.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val ow = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
                val oh = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val h = if (ow > 0 && oh > 0) (800f * oh / ow).toInt().coerceIn(110, 800) else (800 * WidgetKind.of(ctx, id).aspect).toInt()
                val bmp = StatsRenderer.render(Sampler.snapshot, 800, h, WidgetOptions.load(ctx, id))
                val rv = RemoteViews(ctx.packageName, R.layout.widget_stats)
                rv.setImageViewBitmap(R.id.widget_image, bmp)
                rv.setOnClickPendingIntent(R.id.widget_image, open)
                mgr.updateAppWidget(id, rv)
            }
        }
    }
}

/** Smalle strook (4x1), standaard zonder grafiekjes. */
class StatsWidgetStrip : StatsWidget()

/** Klein vierkant (2x2) met één onderdeel, standaard het netwerk. */
class StatsWidgetSmall : StatsWidget()

/** Klein vierkantje (1x1) met één getal, standaard het geheugen. */
class StatsWidgetMini : StatsWidget()

/** Twee onderdelen naast elkaar op één rij (2x1), standaard geheugen en netwerk. */
class StatsWidgetDuo : StatsWidget()

/** Grote widget (4x3) met alle onderdelen in twee rijen. */
class StatsWidgetLarge : StatsWidget()
