package com.ericbruggema.taskbarstats

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.system.exitProcess

/** Draait in een door Shizuku gestart proces met shell-rechten; alleen daar is /proc/stat leesbaar. */
class StatsUserService() : IStatsService.Stub() {
    override fun destroy() { exitProcess(0) }
    override fun readProcStat(): String? = try {
        File("/proc/stat").bufferedReader().use { it.readLine() }
    } catch (_: Exception) { null }
    override fun topProcesses(): String? = try {
        val p = ProcessBuilder("top", "-b", "-n", "1", "-m", "70").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().use { it.readText() }.also { p.waitFor() }
    } catch (_: Exception) { null }

    private fun read(path: String): String = try { File(path).readText() } catch (_: Exception) { "" }
    private fun cmdline(pid: Int) = read("/proc/$pid/cmdline").replace('\u0000', ' ').trim()

    override fun processDetails(pid: Int): String? = try {
        val out = StringBuilder()
        fun put(k: String, v: String) { if (v.isNotBlank()) out.append(k).append('\t').append(v.trim()).append('\n') }
        put("cmdline", cmdline(pid))
        val st = read("/proc/$pid/status").lines().associate { it.substringBefore(':') to it.substringAfter(':').trim() }
        put("state", st["State"] ?: ""); put("threads", st["Threads"] ?: ""); put("rss", st["VmRSS"] ?: ""); put("vm", st["VmSize"] ?: "")
        val ppid = (st["PPid"] ?: "").toIntOrNull()
        put("ppid", ppid?.toString() ?: ""); if (ppid != null) put("parent", cmdline(ppid).substringBefore(' '))
        put("uid", (st["Uid"] ?: "").substringBefore('\t').substringBefore(' '))
        // leeftijd: starttijd (tikken sinds opstarten, veld 22 van stat) tegenover de uptime
        val stat = read("/proc/$pid/stat").substringAfterLast(')').trim().split(' ')
        val start = stat.getOrNull(19)?.toLongOrNull(); val up = read("/proc/uptime").substringBefore(' ').toDoubleOrNull()
        if (start != null && up != null) put("age", ((up - start / 100.0).toLong()).toString())
        // wat de activiteitenmanager over dit proces weet: hoe het draait (activiteit, service, ontvanger, provider)
        val p = ProcessBuilder("dumpsys", "activity", "processes").redirectErrorStream(true).start()
        val lines = p.inputStream.bufferedReader().use { it.readLines() }; p.waitFor()
        val i = lines.indexOfFirst { it.contains("ProcessRecord{") && Regex("[ {]$pid:").containsMatchIn(it) }
        if (i >= 0) {
            var n = 0
            for (j in i + 1 until lines.size) {
                val l = lines[j]
                if (l.contains("ProcessRecord{") && l.trimStart().startsWith("*")) break
                val t = l.trim()
                when {
                    t.startsWith("currentHostingComponentTypes=") -> put("hosting", t.substringAfter('=').substringBefore(' '))
                    t.startsWith("lastActivityTime") -> put("since", t.substringAfter("startUpTime=").substringBefore(' '))
                    t.startsWith("packageList=") -> put("packages", t.substringAfter('='))
                    t.startsWith("- ServiceRecord{") || t.startsWith("- ActivityRecord{") || t.startsWith("- ReceiverList{") ->
                        if (n++ < 8) put("part", t.removePrefix("- ").replace(Regex("^(\\w+)\\{[0-9a-f]+ "), "$1 "))
                }
            }
        }
        out.toString()
    } catch (_: Exception) { null }

    override fun runAction(kind: Int, arg: String?): Int = try {
        val cmd = when {
            kind == 1 && arg != null && Regex("[A-Za-z0-9_.]+").matches(arg) -> arrayOf("am", "force-stop", arg)
            kind == 2 && arg != null && Regex("[0-9]+").matches(arg) -> arrayOf("kill", "-9", arg)
            kind == 3 -> arrayOf("pm", "trim-caches", "999G")
            else -> null
        }
        if (cmd == null) -1 else ProcessBuilder(*cmd).redirectErrorStream(true).start().let { p -> p.inputStream.readBytes(); p.waitFor() }
    } catch (_: Exception) { -1 }
}

/**
 * Optionele CPU%-bron: Android blokkeert /proc/stat voor gewone apps, maar met de Shizuku-app (eenmalig
 * gestart via Draadloos foutopsporen of adb) mag deze app het via [StatsUserService] wel lezen.
 */
object ShizukuCpu {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, CONNECTING, ACTIVE }

    var state by mutableStateOf(State.NOT_RUNNING)
        private set

    @Volatile private var service: IStatsService? = null
    private var bound = false
    private var inited = false
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IStatsService.Stub.asInterface(binder); main.post { refresh() }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null; bound = false; main.post { refresh() }
        }
    }

    fun init(ctx: Context) {
        if (!inited) {
            inited = true
            app = ctx.applicationContext
            Shizuku.addBinderReceivedListenerSticky { main.post { refresh() } }
            Shizuku.addBinderDeadListener { service = null; bound = false; main.post { refresh() } }
            Shizuku.addRequestPermissionResultListener { _, _ -> main.post { refresh() } }
        }
        refresh()
    }

    private fun installed() = try {
        app.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true
    } catch (_: Exception) { false }

    fun refresh() {
        if (!inited) return
        state = when {
            service != null -> State.ACTIVE
            !Shizuku.pingBinder() -> if (installed()) State.NOT_RUNNING else State.NOT_INSTALLED
            Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.NEEDS_PERMISSION
            else -> { bind(); State.CONNECTING }
        }
    }

    fun requestPermission() { if (Shizuku.pingBinder()) Shizuku.requestPermission(1) }

    private fun bind() {
        if (bound) return
        bound = true
        val args = Shizuku.UserServiceArgs(ComponentName(app.packageName, StatsUserService::class.java.name))
            .daemon(false).processNameSuffix("stats").version(1)
        try { Shizuku.bindUserService(args, conn) } catch (_: Exception) { bound = false }
    }

    /** Eerste regel van /proc/stat via Shizuku, of null als de verbinding er niet is. */
    fun readProcStat(): String? = try { service?.readProcStat() } catch (_: Exception) { null }

    /** Ruwe uitvoer van top (alle processen, op CPU gesorteerd) via Shizuku; null zonder verbinding. */
    fun topProcesses(): String? = try { service?.topProcesses() } catch (_: Exception) { null }
    fun processDetails(pid: Int): String? = try { service?.processDetails(pid) } catch (_: Exception) { null }
    fun runAction(kind: Int, arg: String?): Int = try { service?.runAction(kind, arg) ?: -1 } catch (_: Exception) { -1 }
}
