package com.ericbruggema.taskbarstats

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Eén momentopname van alle metingen (onveranderlijk, net als MetricsSnapshot op Windows). */
data class Snapshot(
    val cpuPercent: Float? = null,
    val memUsed: Long = 0, val memTotal: Long = 1,
    val rxBps: Float = 0f, val txBps: Float = 0f,
    val storageUsed: Long = 0, val storageTotal: Long = 1,
    val tempC: Float? = null,
    val thermalStatus: Int = 0,
    val headroom: Float? = null,
    val pingMs: Int? = null,
    val pingFailed: Boolean = false,
    val memHist: List<Float> = emptyList(),
    val rxHist: List<Float> = emptyList(),
    val txHist: List<Float> = emptyList(),
    val pingHist: List<Float> = emptyList(),
    val tempHist: List<Float> = emptyList(),
    val cpuHist: List<Float> = emptyList(),
    val netKind: Int = NET_NONE,
    val wifiRssi: Int? = null, val wifiMbps: Int? = null, val wifiMhz: Int? = null,
    val uptimeMs: Long = 0,
    /** Dataverbruik over [dataDays] dagen; null zonder toegang tot gebruiksgegevens. */
    val dataWifi: Long? = null, val dataMobile: Long? = null, val dataDays: Int = 1,
) {
    // nooit buiten 0-100, wat de sensor ook meldt (een balk of getal boven 100% is altijd fout)
    val memPercent get() = (memUsed * 100f / memTotal.coerceAtLeast(1)).coerceIn(0f, 100f)
    val storagePercent get() = (storageUsed * 100f / storageTotal.coerceAtLeast(1)).coerceIn(0f, 100f)
}

const val NET_NONE = 0
const val NET_WIFI = 1
const val NET_MOBILE = 2
const val NET_OTHER = 3

/** Gemiddelden per minuut (maximaal 24 uur), zolang de sampler draait. */
data class MinuteHistory(
    val mem: List<Float> = emptyList(), val rx: List<Float> = emptyList(),
    val tx: List<Float> = emptyList(), val ping: List<Float> = emptyList(),
    val cpu: List<Float> = emptyList(),
)

/**
 * Bemonstert alles wat een gewone Android-app mag lezen. Geen accu (altijd zichtbaar in de statusbalk),
 * CPU alleen als /proc/stat leesbaar is (sinds Android 8 meestal niet zonder Shizuku/adb).
 * Draait op een eigen thread; de UI leest alleen [snapshot].
 */
object Sampler {
    var snapshot by mutableStateOf(Snapshot())
        private set

    var minutes by mutableStateOf(MinuteHistory())
        private set
    private const val HISTORY = 60
    private const val MINUTES = 1440
    private val main = Handler(Looper.getMainLooper())
    private var users = 0
    @Volatile private var running = false
    private var thread: Thread? = null
    private var pingThread: Thread? = null
    @Volatile private var lastPing: Int? = null
    @Volatile private var lastPingFailed = false

    private val mem = ArrayDeque<Float>(); private val rx = ArrayDeque<Float>(); private val tx = ArrayDeque<Float>()
    private val pingH = ArrayDeque<Float>(); private val tempH = ArrayDeque<Float>(); private val cpuH = ArrayDeque<Float>()

    /** Elke gebruiker (scherm, service) meldt zich aan; de threads lopen zolang er iemand is. */
    @Synchronized fun acquire(ctx: Context) {
        users++
        if (running) return
        running = true
        val app = ctx.applicationContext
        StatsRenderer.freeWord = app.getString(R.string.free_word)
        StatsRenderer.dataWord = app.getString(R.string.data_word_mobile)
        StatsRenderer.connWords = arrayOf(R.string.conn_offline, R.string.conn_wifi, R.string.conn_mobile, R.string.conn_other).map { app.getString(it) }.toTypedArray()
        thread = Thread({ loop(app) }, "sampler").also { it.isDaemon = true; it.start() }
        pingThread = Thread({ pingLoop() }, "ping").also { it.isDaemon = true; it.start() }
    }

    @Synchronized fun release() {
        if (--users > 0) return
        users = 0; running = false
    }

    private val mMem = ArrayDeque<Float>(); private val mRx = ArrayDeque<Float>(); private val mTx = ArrayDeque<Float>()
    private val mPing = ArrayDeque<Float>(); private val mCpu = ArrayDeque<Float>()
    private val acc = FloatArray(5); private val accN = IntArray(5)

    private fun push(q: ArrayDeque<Float>, v: Float) { q.addLast(v); while (q.size > HISTORY) q.removeFirst() }

    private fun loop(ctx: Context) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val data = ctx.filesDir
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val wm = ctx.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        var minuteStart = SystemClock.elapsedRealtime()
        var lastRx = TrafficStats.getTotalRxBytes(); var lastTx = TrafficStats.getTotalTxBytes()
        var lastT = SystemClock.elapsedRealtime()
        var cpuPrev: LongArray? = null
        var dataAt = 0L; var dataFor = 0; var dataW: Long? = null; var dataM: Long? = null
        while (running) {
            val now = SystemClock.elapsedRealtime()
            val dt = ((now - lastT) / 1000f).coerceAtLeast(0.2f)
            val rxNow = TrafficStats.getTotalRxBytes(); val txNow = TrafficStats.getTotalTxBytes()
            val rxBps = if (lastRx >= 0 && rxNow >= 0) (rxNow - lastRx) / dt else 0f
            val txBps = if (lastTx >= 0 && txNow >= 0) (txNow - lastTx) / dt else 0f
            lastRx = rxNow; lastTx = txNow; lastT = now

            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val st = StatFs(data.path)
            val total = st.totalBytes; val free = st.availableBytes

            val bat: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
            val headroom = if (android.os.Build.VERSION.SDK_INT >= 30)
                pm.getThermalHeadroom(10).takeIf { !it.isNaN() } else null

            val raw = readCpuRaw()
            val p = cpuPrev
            val cpu = if (raw != null && p != null && raw[1] > p[1])
                (100f * (1f - (raw[0] - p[0]).toFloat() / (raw[1] - p[1]))).coerceIn(0f, 100f) else null
            cpuPrev = raw

            push(mem, ((mi.totalMem - mi.availMem) * 100f / mi.totalMem).coerceIn(0f, 100f))
            push(rx, rxBps); push(tx, txBps)
            lastPing?.let { push(pingH, it.toFloat()) }
            temp?.let { push(tempH, it) }
            cpu?.let { push(cpuH, it) }

            // verbinding: type, en bij wifi signaal, snelheid en frequentie
            val caps = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (_: Exception) { null }
            val kind = when {
                caps == null -> NET_NONE
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NET_WIFI
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NET_MOBILE
                else -> NET_OTHER
            }
            @Suppress("DEPRECATION")
            val wi = if (kind == NET_WIFI) try { wm.connectionInfo } catch (_: Exception) { null } else null

            // gemiddelden per minuut voor het historiescherm
            acc[0] += mem.last(); acc[1] += rxBps; acc[2] += txBps; accN[0]++; accN[1]++; accN[2]++
            lastPing?.let { acc[3] += it; accN[3]++ }
            cpu?.let { acc[4] += it; accN[4]++ }
            if (now - minuteStart >= 60_000) {
                minuteStart = now
                fun avg(i: Int) = if (accN[i] > 0) acc[i] / accN[i] else null
                avg(0)?.let { push2(mMem, it) }; avg(1)?.let { push2(mRx, it) }; avg(2)?.let { push2(mTx, it) }
                avg(3)?.let { push2(mPing, it) }; avg(4)?.let { push2(mCpu, it) }
                acc.fill(0f); accN.fill(0)
                val mh = MinuteHistory(mMem.toList(), mRx.toList(), mTx.toList(), mPing.toList(), mCpu.toList())
                main.post { minutes = mh }
            }

            // dataverbruik is zwaarder om op te vragen: elke 30 s, of meteen als de periode verandert
            val days = Tiles.dataDays
            if (now - dataAt > 30_000 || days != dataFor) {
                dataAt = now; dataFor = days
                if (AppData.hasUsageAccess(ctx)) AppData.totals(ctx, days).let { dataW = it.first; dataM = it.second } else { dataW = null; dataM = null }
            }

            val s = Snapshot(
                cpuPercent = cpu,
                dataWifi = dataW, dataMobile = dataM, dataDays = days,
                memUsed = (mi.totalMem - mi.availMem).coerceIn(0, mi.totalMem), memTotal = mi.totalMem,
                rxBps = rxBps, txBps = txBps,
                storageUsed = (total - free).coerceIn(0, total), storageTotal = total,
                tempC = temp,
                thermalStatus = if (android.os.Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else 0,
                headroom = headroom,
                pingMs = lastPing, pingFailed = lastPingFailed,
                memHist = mem.toList(), rxHist = rx.toList(), txHist = tx.toList(),
                pingHist = pingH.toList(), tempHist = tempH.toList(), cpuHist = cpuH.toList(),
                netKind = kind,
                wifiRssi = wi?.rssi?.takeIf { it > -127 && it < 0 },
                wifiMbps = wi?.linkSpeed?.takeIf { it > 0 },
                wifiMhz = wi?.frequency?.takeIf { it > 0 },
                uptimeMs = SystemClock.elapsedRealtime(),
            )
            main.post { snapshot = s }
            try { Thread.sleep(1000) } catch (_: InterruptedException) { }
        }
    }

    /** Ruwe tellers [idle, totaal] uit /proc/stat; als Android dat blokkeert (de normale situatie) via Shizuku, anders null. */
    private fun push2(q: ArrayDeque<Float>, v: Float) { q.addLast(v); while (q.size > MINUTES) q.removeFirst() }

    private fun readCpuRaw(): LongArray? = try {
        val line = try { File("/proc/stat").bufferedReader().use { it.readLine() } } catch (_: Exception) { ShizukuCpu.readProcStat() }
        val p = line?.trim()?.split(Regex("\\s+"))?.drop(1)?.map { it.toLong() }
        if (p == null) null else longArrayOf(p[3] + p[4], p.sum())
    } catch (_: Exception) { null }

    private fun pingLoop() {
        while (running) {
            val t0 = SystemClock.elapsedRealtime()
            try {
                Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 1500) }
                lastPing = (SystemClock.elapsedRealtime() - t0).toInt(); lastPingFailed = false
            } catch (_: Exception) { lastPing = null; lastPingFailed = true }
            try { Thread.sleep(3000) } catch (_: InterruptedException) { }
        }
    }
}
