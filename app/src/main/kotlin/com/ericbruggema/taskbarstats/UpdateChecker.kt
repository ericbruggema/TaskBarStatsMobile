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
 * "Downloaden en installeren" haalt de APK van de GitHub-release op, controleert naam, versie en handtekening en opent
 * dan het installatiescherm van Android; installeren kan alleen na jouw bevestiging (Android laat niets stil installeren).
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

    /** Opent de releasepagina (of de APK-link) in de browser: de terugvaloptie. */
    fun openInBrowser(ctx: Context, i: Info) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.apk ?: i.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toast(ctx: Context, res: Int) = main.post { android.widget.Toast.makeText(ctx, res, android.widget.Toast.LENGTH_LONG).show() }

    /**
     * Zelf bijwerken: download de APK, controleer hem en open het installatiescherm. Zonder toestemming "apps van deze
     * bron installeren" opent eerst dat Android-scherm; tik daarna nog een keer. Valt terug op de browser bij een fout.
     */
    fun open(ctx: Context, i: Info) {
        val app = ctx.applicationContext
        val apk = i.apk
        if (apk == null || !apk.startsWith("https://")) { openInBrowser(app, i); return }
        if (!app.packageManager.canRequestPackageInstalls()) {
            toast(app, R.string.update_allow)
            app.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        toast(app, R.string.update_downloading)
        Thread {
            val file = try { download(app, apk) } catch (_: Exception) { null }
            if (file == null || !verify(app, file, i.version)) { file?.delete(); toast(app, R.string.update_bad); main.post { openInBrowser(app, i) }; return@Thread }
            main.post {
                val uri = androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".files", file)
                app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }.start()
    }

    private fun download(ctx: Context, url: String): java.io.File {
        val dir = java.io.File(ctx.cacheDir, "updates").also { it.mkdirs(); it.listFiles()?.forEach { f -> f.delete() } }
        val file = java.io.File(dir, "update.apk")
        val c = URL(url).openConnection() as HttpURLConnection   // volgt de GitHub-doorverwijzing (https naar https)
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "TaskBarStatsMobile/" + current(ctx))
        c.inputStream.use { inp -> file.outputStream().use { inp.copyTo(it) } }
        return file
    }

    /** Is dit echt onze app, nieuwer, en met dezelfde handtekening? (Android zou een andere sleutel ook weigeren.) */
    private fun verify(ctx: Context, f: java.io.File, version: String): Boolean = try {
        if (f.length() < 1_000_000) false else {
            val pm = ctx.packageManager
            val flags = if (android.os.Build.VERSION.SDK_INT >= 28) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else 0
            val arch = pm.getPackageArchiveInfo(f.path, flags)
            val mine = pm.getPackageInfo(ctx.packageName, flags)
            fun sigs(p: android.content.pm.PackageInfo?) = if (android.os.Build.VERSION.SDK_INT >= 28)
                p?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() else emptySet()
            arch != null && arch.packageName == ctx.packageName && newer(arch.versionName ?: "0", current(ctx)) &&
                newer(version, current(ctx)) && sigs(arch) == sigs(mine)
        }
    } catch (_: Exception) { false }

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
