package com.ericbruggema.taskbarstats

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale

/** Extra ruimte onderaan elke scrollbare pagina, zodat de laatste knoppen niet achter de navigatiebalk of een pop-upmenu verdwijnen. */
internal val PAGE_BOTTOM = 120.dp
internal val Bg get() = Color(StatsRenderer.BG)
internal val Card get() = Color(StatsRenderer.CARD)
internal val Fg get() = Color(StatsRenderer.FG)
internal val Dim get() = Color(StatsRenderer.DIM)
internal val CMem get() = Color(StatsRenderer.MEM)
internal val CNet get() = Color(StatsRenderer.NET)
internal val CUp get() = Color(StatsRenderer.UP)
internal val CDisk get() = Color(StatsRenderer.DISK)
internal val CPing get() = Color(StatsRenderer.PING)
internal val CTemp get() = Color(StatsRenderer.tone(0xFFFF8A65.toInt()))
internal val CCpu get() = Color(StatsRenderer.CPU)

/** Verborgen: een ping van precies deze waarde kleurt de pingtegel goud. */
internal const val GOLD_PING_MS = 1
internal val GoldColor = Color(0xFFFFD700)

/** Het getoonde tabblad; hoger dan de tabbladen zelf zodat andere schermen er naartoe kunnen verwijzen. */
internal var currentTab by mutableIntStateOf(0)

class MainActivity : ComponentActivity() {
    fun goToTab(i: Int) { openTab(i) }

    /** Kiest een Windows-themabestand (.json) en neemt de kleuren over. */
    val importTheme = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val pal = try { Themes.parse(contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }) } catch (_: Exception) { null }
        if (pal != null) { Themes.setCustom(this, pal); StatsWidget.update(this) }
        else android.widget.Toast.makeText(this, R.string.theme_import_failed, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Eerste start: eerst het keuzescherm (dat vraagt zelf om meldingen); anders meteen de meldingstoestemming
        showSetup = !StatusBarOverlay.prefs(this).getBoolean("setup_done", false)
        if (!showSetup && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        // Overlay of statusbalkiconen aan = de live-service meteen hervatten
        if (!showSetup && ((StatusBarOverlay.enabled(this) && StatusBarOverlay.canDraw(this)) || StatusItems.iconsOn(this)) && !MonitorService.running) MonitorService.start(this)
        Themes.load(this)
        Tiles.load(this)
        ShizukuCpu.init(this)
        openTab(intent.getIntExtra("tab", 0))
        setContent { Fonts.Scope { if (showSetup) SetupScreen() else App() } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (intent.hasExtra("tab")) openTab(intent.getIntExtra("tab", 0)) }
    override fun onResume() { super.onResume(); resumeTick++ }
    override fun onStart() { super.onStart(); Sampler.acquire(this); ShizukuCpu.refresh() }
    override fun onStop() { Sampler.release(); super.onStop() }
}

@Composable
private fun App() {
    val tab = currentTab
    val s = Sampler.snapshot
    val act = LocalContext.current as ComponentActivity
    androidx.compose.runtime.SideEffect {
        // lichte thema's: donkere statusbalk-iconen
        androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView).isAppearanceLightStatusBars =
            androidx.core.graphics.ColorUtils.calculateLuminance(StatsRenderer.BG) > 0.5
    }
    val appCtx = LocalContext.current
    var update by remember { mutableStateOf(UpdateChecker.pending(appCtx)) }
    androidx.compose.runtime.LaunchedEffect(Unit) { UpdateChecker.checkIfDue(appCtx) { update = it } }
    update?.let { u ->
        androidx.compose.material3.AlertDialog(
            containerColor = Card, titleContentColor = Fg, textContentColor = Dim,
            onDismissRequest = { UpdateChecker.clearPending(appCtx); update = null },
            title = { Text(stringResource(R.string.update_available, u.version)) },
            text = { Text(stringResource(R.string.update_text)) },
            confirmButton = { androidx.compose.material3.TextButton(colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = CMem), onClick = { UpdateChecker.open(appCtx, u); UpdateChecker.clearPending(appCtx); update = null }) { Text(stringResource(R.string.update_download)) } },
            dismissButton = { androidx.compose.material3.TextButton(colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Fg), onClick = { UpdateChecker.clearPending(appCtx); update = null }) { Text(stringResource(R.string.update_later)) } },
        )
    }
    androidx.activity.compose.BackHandler(enabled = tab == TAB_SETTINGS && settingsPage != SP_HUB) { settingsBack() }
    val cutoutMod = if (StatusBarOverlay.avoidCutout(appCtx)) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().then(cutoutMod)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Dashboard(s)
                1 -> Cockpit(s) { currentTab = 0 }
                4 -> AppsTab()
                5 -> HistoryTab()
                6 -> AlertsTab()
                else -> SettingsTab(s)
            }
        }
        if (tab != 1) NavBar(tab)
    }
}

/** Onderbalk met vijf vaste plekken; Instellingen opent het overzicht met categorieën. */
@Composable
private fun NavBar(tab: Int) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Fg.copy(alpha = 0.25f)))
        Row(Modifier.fillMaxWidth().background(Card).padding(top = 8.dp, bottom = 6.dp)) {
            for ((id, label) in listOf(0 to R.string.nav_live, 4 to R.string.tab_apps, 5 to R.string.tab_history, 6 to R.string.tab_alerts, TAB_SETTINGS to R.string.nav_settings)) {
                val sel = tab == id
                val col = if (sel) CMem else Fg.copy(alpha = 0.75f)
                Column(
                    Modifier.weight(1f).clickable { if (id == TAB_SETTINGS && sel) settingsPage = SP_HUB else currentTab = id },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) CMem.copy(alpha = 0.22f) else Color.Transparent).padding(horizontal = 18.dp, vertical = 3.dp)) {
                        NavIcon(id, col)
                    }
                    Text(
                        stringResource(label), color = col, fontSize = 12.sp, maxLines = 1,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Kleine pictogrammen voor de onderbalk, getekend zonder extra bibliotheek. */
@Composable
private fun NavIcon(id: Int, c: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val sw = 2.2.dp.toPx(); val st = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        when (id) {
            0 -> { // live: hartslaglijn
                val p = Path(); p.moveTo(w * .08f, w * .55f); p.lineTo(w * .30f, w * .55f); p.lineTo(w * .42f, w * .2f); p.lineTo(w * .58f, w * .85f); p.lineTo(w * .70f, w * .45f); p.lineTo(w * .92f, w * .45f)
                drawPath(p, c, style = st)
            }
            4 -> { // apps: 2x2 blokjes
                val s = w * .34f; val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                for (x in listOf(.1f, .56f)) for (y in listOf(.1f, .56f)) drawRoundRect(c, Offset(w * x, w * y), androidx.compose.ui.geometry.Size(s, s), r)
            }
            5 -> { // historie: staafjes
                for ((i, h) in listOf(.4f, .7f, .55f, .9f).withIndex()) drawLine(c, Offset(w * (.15f + i * .23f), w * .92f), Offset(w * (.15f + i * .23f), w * (.92f - h * .8f)), sw * 1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            6 -> { // meldingen: bel
                val p = Path(); p.moveTo(w * .2f, w * .74f); p.lineTo(w * .8f, w * .74f); p.lineTo(w * .72f, w * .62f); p.lineTo(w * .72f, w * .42f)
                p.cubicTo(w * .72f, w * .16f, w * .28f, w * .16f, w * .28f, w * .42f); p.lineTo(w * .28f, w * .62f); p.close()
                drawPath(p, c, style = st); drawCircle(c, w * .07f, Offset(w * .5f, w * .88f))
            }
            else -> { // instellingen: drie schuifregelaars
                for ((i, k) in listOf(.28f, .62f, .4f).withIndex()) {
                    val y = w * (.22f + i * .28f)
                    drawLine(c, Offset(w * .1f, y), Offset(w * .9f, y), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(Color(StatsRenderer.CARD), w * .13f, Offset(w * k + w * .15f, y)); drawCircle(c, w * .12f, Offset(w * k + w * .15f, y), style = st)
                }
            }
        }
    }
}

@Composable
private fun Dashboard(s: Snapshot) {
    val ids = Tiles.visible()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = PAGE_BOTTOM), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.tab_cockpit) + " \u203A", color = CMem, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Card).clickable { currentTab = 1 }.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        var i = 0
        while (i < ids.size) {
            if (Tiles.isSmall(ids[i]) && i + 1 < ids.size && Tiles.isSmall(ids[i + 1])) {
                val a = ids[i]; val b = ids[i + 1]
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashTile(a, s, Modifier.weight(1f)); DashTile(b, s, Modifier.weight(1f))
                }
                i += 2
            } else { DashTile(ids[i], s, Modifier); i++ }
        }
    }
}

@Composable
private fun DashTile(id: String, s: Snapshot, modifier: Modifier) {
    when (id) {
        "mem" -> Tile(stringResource(R.string.memory), Fmt.percent(s.memPercent),
            stringResource(R.string.used_of, Fmt.bytes(s.memUsed), Fmt.bytes(s.memTotal)), CMem, s.memHist, 100f, modifier)
        "net" -> NetTile(s, modifier)
        "storage" -> Tile(stringResource(R.string.storage), Fmt.percent(s.storagePercent),
            stringResource(R.string.free_fmt, Fmt.bytes(s.storageTotal - s.storageUsed)), CDisk, emptyList(), null, modifier, bar = s.storagePercent / 100f)
        "ping" -> { val gold = s.pingMs == GOLD_PING_MS
            Tile(stringResource(R.string.ping), if (gold) "\u2605 1 ms \u2605" else s.pingMs?.let { "$it ms" } ?: "\u2014",
                if (gold) stringResource(R.string.ping_gold) else if (s.pingFailed) stringResource(R.string.timeout) else "1.1.1.1", if (gold) GoldColor else CPing, s.pingHist, null, modifier) }
        "temp" -> Tile(stringResource(R.string.temperature), s.tempC?.let { String.format("%.0f \u00B0C", it) } ?: "\u2014",
            thermalName(s.thermalStatus), CTemp, s.tempHist, null, modifier)
        "cpu" -> CpuTile(s)
        "wifi" -> Tile(stringResource(R.string.tile_connection), connName(s), connDetail(s), CNet, emptyList(), null, modifier)
        "uptime" -> Tile(stringResource(R.string.tile_uptime), uptimeText(s.uptimeMs), "", CMem, emptyList(), null, modifier, valueSize = 22)
        "wifidata" -> { val (v, sub) = dataText(s, true); Tile(stringResource(R.string.tile_wifidata), v, sub, CNet, emptyList(), null, modifier, valueSize = 26) }
        "mobiledata" -> { val (v, sub) = dataText(s, false); Tile(stringResource(R.string.tile_mobiledata), v, sub, CUp, emptyList(), null, modifier, valueSize = 26) }
    }
}

@Composable
internal fun connName(s: Snapshot) = stringResource(when (s.netKind) {
    NET_WIFI -> R.string.conn_wifi; NET_MOBILE -> R.string.conn_mobile; NET_OTHER -> R.string.conn_other; else -> R.string.conn_offline
})

/** Signaal, linksnelheid en band van het wifi-netwerk, voor zover Android die vrijgeeft. */
internal fun connDetail(s: Snapshot): String = listOfNotNull(
    s.wifiRssi?.let { "$it dBm" }, s.wifiMbps?.let { "$it Mbps" },
    s.wifiMhz?.let { if (it >= 5925) "6 GHz" else if (it >= 4900) "5 GHz" else "2.4 GHz" },
).joinToString("  ·  ")

internal fun uptimeText(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 1440) "${m / 1440} d ${m % 1440 / 60} h ${m % 60} min" else if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

@Composable
private fun thermalName(st: Int) = stringResource(when (st) {
    0 -> R.string.thermal_none; 1 -> R.string.thermal_light; 2 -> R.string.thermal_moderate
    3 -> R.string.thermal_severe; 4 -> R.string.thermal_critical; 5 -> R.string.thermal_emergency
    else -> R.string.thermal_shutdown
})

@Composable
private fun CpuTile(s: Snapshot) {
    if (s.cpuPercent != null) Tile(stringResource(R.string.cpu), Fmt.percent(s.cpuPercent), "", CCpu, s.cpuHist, 100f)
    else {
        val st = ShizukuCpu.state
        val hint = stringResource(when (st) {
            ShizukuCpu.State.NOT_INSTALLED -> R.string.shizuku_not_installed
            ShizukuCpu.State.NOT_RUNNING -> R.string.shizuku_not_running
            ShizukuCpu.State.NEEDS_PERMISSION -> R.string.shizuku_needs_permission
            else -> R.string.shizuku_connecting
        })
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp)) {
            Text(stringResource(R.string.cpu), color = CCpu, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(stringResource(R.string.cpu_unavailable), color = Fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.cpu_unavailable_hint), color = Dim, fontSize = 13.sp)
            Text(hint, color = Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            if (st == ShizukuCpu.State.NEEDS_PERMISSION) Button(
                onClick = { ShizukuCpu.requestPermission() }, modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CMem, contentColor = Bg),
            ) { Text(stringResource(R.string.shizuku_grant)) }
        }
    }
}

@Composable
private fun NetTile(s: Snapshot, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp)) {
        Text(stringResource(R.string.network), color = CNet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text("↓ " + stringResource(R.string.download), color = Dim, fontSize = 12.sp)
                Text(Fmt.rate(s.rxBps), color = Fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text("↑ " + stringResource(R.string.upload), color = Dim, fontSize = 12.sp)
                Text(Fmt.rate(s.txBps), color = Fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.padding(top = 8.dp)) { Spark(s.rxHist, CNet, null, 56.dp); Spark(s.txHist, CUp, null, 56.dp, shared = s.rxHist) }
    }
}

@Composable
internal fun Tile(
    label: String, value: String, sub: String, color: Color, hist: List<Float>, fixedMax: Float?,
    modifier: Modifier = Modifier, bar: Float? = null, valueSize: Int = 30,
) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp)) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(value, color = Fg, fontSize = valueSize.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        if (sub.isNotEmpty()) Text(sub, color = Dim, fontSize = 13.sp)
        if (hist.size > 1) Spark(hist, color, fixedMax, 48.dp, Modifier.padding(top = 8.dp))
        if (bar != null) Canvas(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp)) {
            drawRoundRect(Fg.copy(alpha = 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            drawRoundRect(color, size = androidx.compose.ui.geometry.Size(size.width * (if (bar.isNaN()) 0f else bar.coerceIn(0f, 1f)), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        }
    }
}

@Composable
internal fun Spark(hist: List<Float>, color: Color, fixedMax: Float?, h: Dp, modifier: Modifier = Modifier, shared: List<Float>? = null) {
    Canvas(modifier.fillMaxWidth().height(h)) {
        if (hist.size < 2) return@Canvas
        val max = fixedMax ?: (((hist + (shared ?: emptyList())).max()) * 1.2f).coerceAtLeast(1f)
        val path = Path()
        hist.forEachIndexed { i, v ->
            val x = size.width * i / (hist.size - 1)
            val y = size.height * (1f - (v / max).coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
    }
}

/** Fullscreen cockpit: grote cijfers, scherm blijft aan; tik om terug te gaan (bedoeld voor een oude telefoon op een standaard). */
@Composable
private fun Cockpit(s: Snapshot, onExit: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val window = (LocalContext.current as ComponentActivity).window
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        // Echt fullscreen: systeembalken weg (tik/veeg vanaf de rand haalt ze tijdelijk terug)
        val ctl = androidx.core.view.WindowCompat.getInsetsController(window, view)
        ctl.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { view.keepScreenOn = false; ctl.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    val cells = mutableListOf<@Composable (Modifier) -> Unit>()
    for (id in Tiles.visible()) when (id) {
        "mem" -> cells += { mod -> Big(stringResource(R.string.memory), Fmt.percent(s.memPercent), Fmt.bytes(s.memUsed), CMem, s.memHist, 100f, mod) }
        "net" -> {
            cells += { mod -> Big("\u2193 " + stringResource(R.string.download), Fmt.rate(s.rxBps), "", CNet, s.rxHist, null, mod) }
            cells += { mod -> Big("\u2191 " + stringResource(R.string.upload), Fmt.rate(s.txBps), "", CUp, s.txHist, null, mod) }
        }
        "storage" -> cells += { mod -> Big(stringResource(R.string.storage), Fmt.percent(s.storagePercent), stringResource(R.string.free_fmt, Fmt.bytes(s.storageTotal - s.storageUsed)), CDisk, emptyList(), null, mod) }
        "ping" -> cells += { mod -> val gold = s.pingMs == GOLD_PING_MS
            Big(stringResource(R.string.ping), if (gold) "\u2605 1 ms \u2605" else s.pingMs?.let { "$it ms" } ?: "\u2014", if (gold) stringResource(R.string.ping_gold) else "1.1.1.1", if (gold) GoldColor else CPing, s.pingHist, null, mod) }
        "temp" -> cells += { mod -> Big(stringResource(R.string.temperature), s.tempC?.let { String.format("%.0f \u00B0C", it) } ?: "\u2014", thermalName(s.thermalStatus), CTemp, s.tempHist, null, mod) }
        "wifi" -> cells += { mod -> Big(stringResource(R.string.tile_connection), connName(s), connDetail(s), CNet, emptyList(), null, mod) }
        "uptime" -> cells += { mod -> Big(stringResource(R.string.tile_uptime), uptimeText(s.uptimeMs), "", CMem, emptyList(), null, mod) }
        "wifidata" -> cells += { mod -> val (v, sub) = dataText(s, true); Big(stringResource(R.string.tile_wifidata), v, sub, CNet, emptyList(), null, mod) }
        "mobiledata" -> cells += { mod -> val (v, sub) = dataText(s, false); Big(stringResource(R.string.tile_mobiledata), v, sub, CUp, emptyList(), null, mod) }
        "cpu" -> cells += { mod -> Big(stringResource(R.string.cpu), s.cpuPercent?.let { Fmt.percent(it) } ?: "\u2014", if (s.cpuPercent == null) stringResource(R.string.cpu_unavailable) else "", CCpu, s.cpuHist, 100f, mod) }
    }
    // zonder systeembalken ligt de inhoud anders deels achter de selfiecamera (bovenin of opzij in landschap)
    val cutoutMod = if (StatusBarOverlay.avoidCutout(LocalContext.current)) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier
    // verborgen: drie snelle tikken = de tegels "checken in" een voor een, als een vertrekbord; één of twee tikken sluiten de cockpit
    var taps by remember { mutableIntStateOf(0) }
    var tapStamp by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var checkin by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(tapStamp) {
        if (tapStamp == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(450)
        if (taps >= 3) checkin++ else onExit()
        taps = 0
    }
    androidx.compose.runtime.LaunchedEffect(checkin) {
        if (checkin == 0) return@LaunchedEffect
        shown = 0
        for (i in 1..cells.size) { kotlinx.coroutines.delay(230); shown = i }
        shown = Int.MAX_VALUE
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(cutoutMod).clickable { taps++; tapStamp++ }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            cells.chunked(2).forEachIndexed { r, row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEachIndexed { c, cell ->
                        val a by androidx.compose.animation.core.animateFloatAsState(if (r * 2 + c < shown) 1f else 0f, androidx.compose.animation.core.tween(280), label = "checkin")
                        cell(Modifier.weight(1f).graphicsLayer { rotationX = (1f - a) * 90f; alpha = a; cameraDistance = 14f * density })
                    }
                }
            }
        }
        // discreet way back to the bottom bar (tapping anywhere also works)
        Text(
            "✕  " + stringResource(R.string.nav_live), color = Fg.copy(alpha = 0.7f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).clip(RoundedCornerShape(50))
                .background(Bg.copy(alpha = 0.8f)).clickable { onExit() }.padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun Big(label: String, value: String, sub: String, color: Color, hist: List<Float>, fixedMax: Float?, modifier: Modifier) {
    Column(modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp)) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(value, color = Fg, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        if (sub.isNotEmpty()) Text(sub, color = Dim, fontSize = 14.sp)
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            if (hist.size > 1) Spark(hist, color, fixedMax, 120.dp, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** Waarde en onderschrift van een datategel: verbruik over de gekozen periode, of een hint zonder toegang tot gebruiksgegevens. */
@Composable
private fun dataText(s: Snapshot, wifi: Boolean): Pair<String, String> {
    val v = if (wifi) s.dataWifi else s.dataMobile
    return if (v == null) "\u2014" to stringResource(R.string.data_needs_access)
    else Fmt.bytes(v) to stringResource(when (s.dataDays) { 1 -> R.string.period_today; 7 -> R.string.period_7; else -> R.string.period_30 })
}

@Composable
private fun tileName(id: String) = stringResource(when (id) {
    "mem" -> R.string.memory; "net" -> R.string.network; "storage" -> R.string.storage
    "ping" -> R.string.ping; "temp" -> R.string.temperature; "wifi" -> R.string.tile_connection
    "uptime" -> R.string.tile_uptime; "wifidata" -> R.string.tile_wifidata; "mobiledata" -> R.string.tile_mobiledata; else -> R.string.cpu
})

/** Tegels aan/uit zetten en de volgorde bepalen (geldt voor dashboard en cockpit). */
@Composable
internal fun TilesTab() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = PAGE_BOTTOM), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.tiles_title), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.tiles_hint), color = Dim, fontSize = 14.sp)
        Text(stringResource(R.string.data_period), color = Dim, fontSize = 14.sp)
        val periods = listOf(1, 7, 30)
        Chips(listOf(stringResource(R.string.period_today), stringResource(R.string.period_7), stringResource(R.string.period_30)), periods.indexOf(Tiles.dataDays)) { Tiles.setDataDays(ctx, periods[it]) }
        val order = Tiles.order
        order.forEachIndexed { idx, id ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Switch(
                    checked = id !in Tiles.hidden, onCheckedChange = { Tiles.toggle(ctx, id) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = CMem, checkedThumbColor = Bg),
                )
                Text(tileName(id), color = if (id in Tiles.hidden) Dim else Fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp).weight(1f))
                for ((label, delta, on) in listOf(Triple("\u25B2", -1, idx > 0), Triple("\u25BC", 1, idx < order.lastIndex)))
                    Text(label, color = if (on) Fg else Dim.copy(alpha = 0.3f), fontSize = 18.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = on) { Tiles.move(ctx, id, delta) }
                            .padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        Button(onClick = { Tiles.reset(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Fg)) {
            Text(stringResource(R.string.tiles_reset))
        }
    }
}
