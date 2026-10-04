package com.ericbruggema.taskbarstats

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/** Gegevens per app: verkeer, opslag, gebruik, autostart en processen. Alles hier draait op een achtergrondthread. */
object AppData {
    class Traffic(val label: String, val rx: Long, val tx: Long) { val total get() = rx + tx }
    class Sized(val label: String, val pkg: String, val bytes: Long, val app: Long, val data: Long, val cache: Long)
    class Used(val label: String, val ms: Long)
    class Auto(val label: String, val pkg: String, val system: Boolean)
    class Proc(val pid: Int, val name: String, val label: String, val pkg: String?, val rssKb: Long, val cpu: Float)
    class Day(val start: Long, val wifi: Long, val mobile: Long)

    // --- Gebruikerstoegang (speciale toestemming, staat in Instellingen) ---

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opent de pagina "Toegang tot gebruiksgegevens" meteen op deze app (valt terug op de lijst). */
    fun usageIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}")).also {
            if (it.resolveActivity(ctx.packageManager) == null) it.data = null
        }

    private fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (_: Exception) { pkg }

    fun dayStart(daysAgo: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    // --- Dataverbruik per dag (wifi en mobiel) ---

    private fun device(ctx: Context, type: Int, from: Long, to: Long): Long = try {
        val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val b = nsm.querySummaryForDevice(type, null, from, to)
        b.rxBytes + b.txBytes
    } catch (_: Exception) { 0L }

    /** Wifi- en mobiel verbruik (bytes) vanaf het begin van vandaag min [days]-1 dagen tot nu. */
    fun totals(ctx: Context, days: Int): Pair<Long, Long> {
        val from = dayStart(days - 1); val to = System.currentTimeMillis()
        return device(ctx, ConnectivityManager.TYPE_WIFI, from, to) to device(ctx, ConnectivityManager.TYPE_MOBILE, from, to)
    }

    /** De laatste [days] dagen, nieuwste eerst. */
    fun daily(ctx: Context, days: Int = 7): List<Day> = (0 until days).map { d ->
        val from = dayStart(d)
        val to = if (d == 0) System.currentTimeMillis() else dayStart(d - 1)
        Day(from, device(ctx, ConnectivityManager.TYPE_WIFI, from, to), device(ctx, ConnectivityManager.TYPE_MOBILE, from, to))
    }

    // --- Verkeer per app ---

    fun traffic(ctx: Context, days: Int): List<Traffic> {
        val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val from = dayStart(days - 1); val to = System.currentTimeMillis()
        val rx = HashMap<Int, Long>(); val tx = HashMap<Int, Long>()
        for (type in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) try {
            val st = nsm.querySummary(type, null, from, to)
            val b = NetworkStats.Bucket()
            while (st.hasNextBucket()) {
                st.getNextBucket(b)
                rx[b.uid] = (rx[b.uid] ?: 0L) + b.rxBytes; tx[b.uid] = (tx[b.uid] ?: 0L) + b.txBytes
            }
            st.close()
        } catch (_: Exception) { }
        val pm = ctx.packageManager
        // meerdere uid's kunnen dezelfde naam hebben (gedeelde uid): per naam optellen
        val byLabel = HashMap<String, LongArray>()
        for (uid in rx.keys) {
            val name = when (uid) {
                NetworkStats.Bucket.UID_REMOVED -> ctx.getString(R.string.uid_removed)
                NetworkStats.Bucket.UID_TETHERING -> ctx.getString(R.string.uid_tethering)
                0 -> "Android"
                2000 -> ctx.getString(R.string.uid_shell)
                else -> pm.getPackagesForUid(uid)?.firstOrNull()?.let { label(ctx, it) } ?: ctx.getString(R.string.uid_system)
            }
            val a = byLabel.getOrPut(name) { LongArray(2) }
            a[0] += rx[uid] ?: 0L; a[1] += tx[uid] ?: 0L
        }
        return byLabel.map { Traffic(it.key, it.value[0], it.value[1]) }.filter { it.total > 0 }.sortedByDescending { it.total }.take(30)
    }

    // --- Opslag per app ---

    /** Laatste lijst, zodat het scherm meteen iets toont terwijl de lijst opnieuw wordt opgebouwd. */
    @Volatile var lastStorage: List<Sized>? = null

    fun storage(ctx: Context): List<Sized> {
        val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val pm = ctx.packageManager
        // per app een aparte (trage) binder-aanroep: parallel uitvoeren
        val found = pm.getInstalledApplications(0).parallelStream().map { ai ->
            try {
                val st = ssm.queryStatsForPackage(android.os.storage.StorageManager.UUID_DEFAULT, ai.packageName,
                    android.os.UserHandle.getUserHandleForUid(ai.uid))
                val total = st.appBytes + st.dataBytes + st.cacheBytes
                if (total > 0) Sized(ai.loadLabel(pm).toString(), ai.packageName, total, st.appBytes, st.dataBytes, st.cacheBytes) else null
            } catch (_: Exception) { null }
        }.collect(java.util.stream.Collectors.toList())
        return found.filterNotNull().sortedByDescending { it.bytes }.take(60).also { lastStorage = it }
    }

    // --- Meest gebruikte apps (schermtijd) ---

    fun mostUsed(ctx: Context, days: Int): List<Used> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val from = dayStart(days - 1)
        val ms = HashMap<String, Long>()
        for (u in usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, System.currentTimeMillis()))
            if (u.totalTimeInForeground > 0) ms[u.packageName] = (ms[u.packageName] ?: 0L) + u.totalTimeInForeground
        return ms.map { Used(label(ctx, it.key), it.value) }.sortedByDescending { it.ms }.take(25)
    }

    // --- Apps die bij het opstarten van het toestel meedraaien (BOOT_COMPLETED-ontvangers) ---

    fun autostart(ctx: Context): List<Auto> {
        val pm = ctx.packageManager
        val pkgs = LinkedHashSet<String>()
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED))
            for (r in pm.queryBroadcastReceivers(Intent(action), 0)) pkgs += r.activityInfo.packageName
        return pkgs.mapNotNull { p ->
            try {
                val ai = pm.getApplicationInfo(p, 0)
                Auto(ai.loadLabel(pm).toString(), p, ai.flags and ApplicationInfo.FLAG_SYSTEM != 0)
            } catch (_: PackageManager.NameNotFoundException) { null }
        }.sortedWith(compareBy({ it.system }, { it.label.lowercase() }))
    }

    // --- Processen (alleen met Shizuku: gewone apps zien sinds Android 7 geen andere processen meer) ---

    fun processes(ctx: Context): List<Proc>? {
        val text = ShizukuCpu.topProcesses() ?: return null
        val out = ArrayList<Proc>()
        var started = false
        for (line in text.lines()) {
            val t = line.trim()
            if (t.startsWith("PID")) { started = true; continue }
            if (!started || t.isEmpty()) continue
            // PID USER PR NI VIRT RES SHR S %CPU %MEM TIME+ ARGS
            val c = t.split(Regex("\\s+"), limit = 12)
            if (c.size < 12) continue
            val pid = c[0].toIntOrNull() ?: continue
            val name = c[11].substringBefore(' ')
            if (name == "top" || name.startsWith("[")) continue   // top zelf en kernel-threads
            val pkg = name.substringBefore(':')
            val real = pkg.contains('.') && exists(ctx, pkg)
            out += Proc(pid, name, if (real) label(ctx, pkg) else name, if (real) pkg else null, parseKb(c[5]), c[8].replace(',', '.').toFloatOrNull() ?: 0f)
        }
        return out.sortedByDescending { it.cpu }
    }

    private fun exists(ctx: Context, pkg: String) = try { ctx.packageManager.getApplicationInfo(pkg, 0); true } catch (_: Exception) { false }

    /** Details van een proces uit [ShizukuCpu.processDetails]: sleutel -> waarde, plus de onderdelen die het draaiende houden. */
    class Detail(val values: Map<String, String>, val parts: List<String>)

    fun processDetail(pid: Int): Detail? {
        val text = ShizukuCpu.processDetails(pid) ?: return null
        val m = LinkedHashMap<String, String>(); val parts = ArrayList<String>()
        for (l in text.lines()) {
            val k = l.substringBefore('\t', ""); val v = l.substringAfter('\t', "")
            if (k.isEmpty()) continue
            if (k == "part") parts += v else m[k] = v
        }
        return Detail(m, parts)
    }

    /** Wat het proces draaiende houdt, uit de bitmasker van de activiteitenmanager (Android 14). */
    fun hostingNames(hex: String): List<String> {
        val v = hex.removePrefix("0x").toLongOrNull(16) ?: return emptyList()
        val names = listOf("system", "persistent", "backup", "instrumentation", "activity", "receiver", "provider", "started service", "foreground service", "bound service")
        return names.filterIndexed { i, _ -> v and (1L shl i) != 0L }
    }

    /** "181M", "5.4M", "820K", "1.2G" -> KB */
    private fun parseKb(s: String): Long {
        val n = s.dropLast(1).replace(',', '.').toDoubleOrNull() ?: return 0
        return when (s.last()) { 'G' -> n * 1_048_576; 'M' -> n * 1024; 'K' -> n; else -> s.toDoubleOrNull()?.div(1024) ?: 0.0 }.toLong()
    }
}
