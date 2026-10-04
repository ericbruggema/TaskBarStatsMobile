package com.ericbruggema.taskbarstats

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Een soort widget dat de gebruiker kan plaatsen: eigen formaat en eigen beginwaarden (daarna per widget aan te passen). */
enum class WidgetKind(val cls: Class<out StatsWidget>, val label: Int, val defaults: WidgetOptions, val aspect: Float) {
    DASHBOARD(StatsWidget::class.java, R.string.kind_dashboard, WidgetOptions(), 0.43f),
    STRIP(StatsWidgetStrip::class.java, R.string.kind_strip, WidgetOptions(setOf("mem", "net", "disk", "ping"), 100, false), 0.16f),
    SMALL(StatsWidgetSmall::class.java, R.string.kind_small, WidgetOptions(setOf("net"), 100, true), 1f),
    MINI(StatsWidgetMini::class.java, R.string.kind_mini, WidgetOptions(setOf("mem"), 100, false), 1f),
    DUO(StatsWidgetDuo::class.java, R.string.kind_duo, WidgetOptions(setOf("mem", "net"), 100, false), 0.5f),
    LARGE(StatsWidgetLarge::class.java, R.string.kind_large, WidgetOptions(setOf("mem", "net", "cpu", "disk", "data", "ping", "conn", "uptime"), 100, true), 0.75f);

    fun ids(ctx: Context): IntArray = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    companion object {
        fun of(ctx: Context, id: Int): WidgetKind {
            val name = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider?.className
            return entries.firstOrNull { it.cls.name == name } ?: DASHBOARD
        }
    }
}

/** Instellingen per widget (appWidgetId): welke cellen, hoe doorzichtig de achtergrond is en of de grafiekjes en balken getekend worden. */
data class WidgetOptions(val cells: Set<String> = DEFAULT_CELLS, val opacity: Int = 100, val graphs: Boolean = true) {
    fun save(ctx: Context, id: Int) {
        StatusBarOverlay.prefs(ctx).edit()
            .putString("widget_$id", "${ALL_CELLS.filter { it in cells }.joinToString(",")};$opacity;${if (graphs) 1 else 0}").apply()
    }

    companion object {
        val ALL_CELLS = listOf("mem", "net", "cpu", "disk", "data", "ping", "temp", "conn", "uptime")
        val DEFAULT_CELLS = setOf("mem", "net", "disk", "ping")

        fun load(ctx: Context, id: Int): WidgetOptions {
            val def = WidgetKind.of(ctx, id).defaults
            val raw = StatusBarOverlay.prefs(ctx).getString("widget_$id", null) ?: return def
            val parts = raw.split(";")
            val cells = parts[0].split(",").filter { it in ALL_CELLS }.toSet()
            val opacity = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(20, 100) ?: 100
            val graphs = parts.getOrNull(2)?.let { it != "0" } ?: true
            return WidgetOptions(if (cells.isEmpty()) DEFAULT_CELLS else cells, opacity, graphs)
        }

        fun delete(ctx: Context, id: Int) { StatusBarOverlay.prefs(ctx).edit().remove("widget_$id").apply() }
    }
}
