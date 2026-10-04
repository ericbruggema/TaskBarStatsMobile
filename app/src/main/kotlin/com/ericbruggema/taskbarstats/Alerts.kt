package com.ericbruggema.taskbarstats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Meldingen als een waarde boven een drempel komt (alles standaard uit); één melding per regel per 10 minuten. */
object Alerts {
    class Rule(val key: String, val label: Int, val message: Int, val default: Int, val min: Int, val max: Int, val step: Int, val unit: String)

    val rules = listOf(
        Rule("mem", R.string.alert_mem, R.string.alert_msg_mem, 90, 50, 99, 5, "%"),
        Rule("storage", R.string.alert_storage, R.string.alert_msg_storage, 90, 50, 99, 5, "%"),
        Rule("ping", R.string.alert_ping, R.string.alert_msg_ping, 200, 50, 1000, 50, " ms"),
        Rule("temp", R.string.alert_temp, R.string.alert_msg_temp, 42, 30, 60, 1, " °C"),
    )

    private const val CHANNEL = "alerts"
    private const val COOLDOWN_MS = 10 * 60 * 1000L
    private val last = HashMap<String, Long>()

    fun enabled(ctx: Context, r: Rule) = StatusBarOverlay.prefs(ctx).getBoolean("alert_${r.key}_on", false)
    fun threshold(ctx: Context, r: Rule) = StatusBarOverlay.prefs(ctx).getInt("alert_${r.key}_v", r.default)
    fun setEnabled(ctx: Context, r: Rule, on: Boolean) = StatusBarOverlay.prefs(ctx).edit().putBoolean("alert_${r.key}_on", on).apply()
    fun setThreshold(ctx: Context, r: Rule, v: Int) = StatusBarOverlay.prefs(ctx).edit().putInt("alert_${r.key}_v", v.coerceIn(r.min, r.max)).apply()

    private fun value(r: Rule, s: Snapshot): Float? = when (r.key) {
        "mem" -> s.memPercent
        "storage" -> s.storagePercent
        "ping" -> s.pingMs?.toFloat()
        else -> s.tempC
    }

    fun check(ctx: Context, s: Snapshot) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val now = SystemClock.elapsedRealtime()
        for ((i, r) in rules.withIndex()) {
            if (!enabled(ctx, r)) continue
            val v = value(r, s) ?: continue
            val thr = threshold(ctx, r)
            if (v < thr) { last.remove(r.key); continue }      // weer normaal: de volgende overschrijding meldt meteen
            if ((last[r.key] ?: 0L).let { it != 0L && now - it < COOLDOWN_MS }) continue
            last[r.key] = now
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            nm.notify(10 + i, Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(ctx.getString(r.message, v.toInt(), thr)).setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
