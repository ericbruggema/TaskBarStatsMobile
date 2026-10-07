package com.ericbruggema.taskbarstats

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optionele updatecontrole (standaard uit): vraagt hooguit één keer per dag de nieuwste GitHub-release op
 * (api.github.com, zonder enige gegevens van de gebruiker) en meldt een nieuwe versie maar één keer per versie.
 * Downloaden en installeren gaat via de browser / het installatiescherm van Android; de app installeert niets zelf.
 * Uit als de app uit de Play Store komt (die werkt zichzelf bij).
 */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/ericbruggema/TaskBarStatsMobile/releases/latest"
    private const val DAY = 24L * 3600 * 1000
    private const val CHANNEL = "updates"
    private const val NOTE_ID = 2

    class Info(val version: String, val page: String, val apk: String?)

    private fun prefs(ctx: Context) = StatusBarOverlay.prefs(ctx)

    fun fromPlay(ctx: Context): Boolean = try {
        val pm = ctx.packageManager
        val src = if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(ctx.packageName).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(ctx.packageName)
        src == "com.android.vending"
    } catch (_: Exception) { false }

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("update_check", false) && !fromPlay(ctx)

    fun current(ctx: Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0" } catch (_: Exception) { "0" }

    /** Is `a` nieuwer dan `b`? Vergelijkt de getallen in "v0.4.1" een voor een. */
    fun newer(a: String, b: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').split('.', '-', '+').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** Blokkerend: de nieuwste release, of null bij een fout. Alleen op een achtergrondthread aanroepen. */
    private fun fetch(ctx: Context): Info? = try {
        val url = prefs(ctx).getString("update_url", null) ?: API   // update_url: alleen om te testen
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "TaskBarStatsMobile/" + current(ctx))
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        var apk: String? = null
        j.optJSONArray("assets")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { if (it.optString("name").endsWith(".apk")) apk = it.optString("browser_download_url") } }
        Info(j.getString("tag_name"), j.optString("html_url"), apk)
    } catch (_: Exception) { null }

    private val main = Handler(Looper.getMainLooper())

    /** Op verzoek ("Nu controleren"): geeft (nieuwe versie of null, gelukt?) terug op de hoofdthread. */
    fun checkNow(ctx: Context, done: (Info?, Boolean) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val i = fetch(app)
            prefs(app).edit().putLong("update_last", System.currentTimeMillis()).apply()
            main.post { done(i?.takeIf { newer(it.version, current(app)) }, i != null) }
        }.start()
    }

    /** Automatisch: hooguit 1x per dag, en een nieuwe versie wordt maar één keer gemeld. */
    fun checkIfDue(ctx: Context, found: (Info) -> Unit) {
        val app = ctx.applicationContext
        if (!enabled(app)) return
        if (System.currentTimeMillis() - prefs(app).getLong("update_last", 0) < DAY) return
        checkNow(app) { info, _ ->
            if (info != null && prefs(app).getString("update_notified", "") != info.version) {
                prefs(app).edit().putString("update_notified", info.version).putString("update_pv", info.version)
                    .putString("update_pp", info.page).putString("update_pa", info.apk).apply()
                found(info)
            }
        }
    }

    /** Een gemelde maar nog niet bekeken nieuwe versie (blijft bewaard tot de gebruiker de melding in de app sluit). */
    fun pending(ctx: Context): Info? = prefs(ctx).getString("update_pv", null)?.let {
        Info(it, prefs(ctx).getString("update_pp", "") ?: "", prefs(ctx).getString("update_pa", null))
    }?.takeIf { newer(it.version, current(ctx)) }

    fun clearPending(ctx: Context) = prefs(ctx).edit().remove("update_pv").remove("update_pp").remove("update_pa").apply()

    /** Opent de APK-link (de browser downloadt, daarna installeert Android na jouw bevestiging) of anders de releasepagina. */
    fun open(ctx: Context, i: Info) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.apk ?: i.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Eenmalige melding (vanuit de live monitor) als de app niet open is. */
    fun notify(ctx: Context, i: Info) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(NOTE_ID, android.app.Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(ctx.getString(R.string.update_available, i.version))
            .setContentText(ctx.getString(R.string.update_tap)).setContentIntent(open).setAutoCancel(true).build())
    }
}
