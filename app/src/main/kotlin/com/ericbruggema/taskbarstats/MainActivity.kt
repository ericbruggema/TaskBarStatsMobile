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

/** Het getoonde tabblad; hoger dan de tabbladen zelf zodat andere schermen er naartoe kunnen verwijzen. */
internal var currentTab by mutableIntStateOf(0)

class MainActivity : ComponentActivity() {
    fun goToTab(i: Int) { currentTab = i }

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
        currentTab = intent.getIntExtra("tab", 0)
        setContent { if (showSetup) SetupScreen() else App() }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (intent.hasExtra("tab")) currentTab = intent.getIntExtra("tab", 0) }
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
    val cutoutMod = if (StatusBarOverlay.avoidCutout(appCtx)) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().then(cutoutMod)) {
        if (tab != 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.tab_dashboard, R.string.tab_cockpit, R.string.tab_widget, R.string.tab_tiles, R.string.tab_apps, R.string.tab_history, R.string.tab_alerts, R.string.tab_permissions).forEachIndexed { i, id ->
                val sel = i == tab
                Text(
                    stringResource(id),
                    color = if (sel) Bg else Fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) CMem else Card)
                        .clickable { currentTab = i }.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        when (tab) {
            0 -> Dashboard(s)
            1 -> Cockpit(s) { currentTab = 0 }
            2 -> WidgetTab(s)
            3 -> TilesTab()
            4 -> AppsTab()
            5 -> HistoryTab()
            6 -> AlertsTab()
            else -> PermissionsTab()
        }
    }
}

@Composable
private fun Dashboard(s: Snapshot) {
    val ids = Tiles.visible()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = PAGE_BOTTOM), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        "ping" -> Tile(stringResource(R.string.ping), s.pingMs?.let { "$it ms" } ?: "\u2014",
            if (s.pingFailed) stringResource(R.string.timeout) else "1.1.1.1", CPing, s.pingHist, null, modifier)
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
        "ping" -> cells += { mod -> Big(stringResource(R.string.ping), s.pingMs?.let { "$it ms" } ?: "\u2014", "1.1.1.1", CPing, s.pingHist, null, mod) }
        "temp" -> cells += { mod -> Big(stringResource(R.string.temperature), s.tempC?.let { String.format("%.0f \u00B0C", it) } ?: "\u2014", thermalName(s.thermalStatus), CTemp, s.tempHist, null, mod) }
        "wifi" -> cells += { mod -> Big(stringResource(R.string.tile_connection), connName(s), connDetail(s), CNet, emptyList(), null, mod) }
        "uptime" -> cells += { mod -> Big(stringResource(R.string.tile_uptime), uptimeText(s.uptimeMs), "", CMem, emptyList(), null, mod) }
        "wifidata" -> cells += { mod -> val (v, sub) = dataText(s, true); Big(stringResource(R.string.tile_wifidata), v, sub, CNet, emptyList(), null, mod) }
        "mobiledata" -> cells += { mod -> val (v, sub) = dataText(s, false); Big(stringResource(R.string.tile_mobiledata), v, sub, CUp, emptyList(), null, mod) }
        "cpu" -> cells += { mod -> Big(stringResource(R.string.cpu), s.cpuPercent?.let { Fmt.percent(it) } ?: "\u2014", if (s.cpuPercent == null) stringResource(R.string.cpu_unavailable) else "", CCpu, s.cpuHist, 100f, mod) }
    }
    // zonder systeembalken ligt de inhoud anders deels achter de selfiecamera (bovenin of opzij in landschap)
    val cutoutMod = if (StatusBarOverlay.avoidCutout(LocalContext.current)) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier
    Column(Modifier.fillMaxSize().then(cutoutMod).clickable { onExit() }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in cells.chunked(2)) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { it(Modifier.weight(1f)) }
        }
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

@Composable
private fun WidgetTab(s: Snapshot) {
    val ctx = LocalContext.current
    var live by remember { mutableStateOf(MonitorService.running) }
    val bmp = remember(s) { StatsRenderer.render(s, 800, 340) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = PAGE_BOTTOM), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.widget_title), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.widget_hint), color = Dim, fontSize = 14.sp)
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        Button(
            onClick = { if (live) MonitorService.stop(ctx) else MonitorService.start(ctx); live = !live },
            colors = ButtonDefaults.buttonColors(containerColor = CMem, contentColor = Bg),
        ) { Text(stringResource(if (live) R.string.stop_monitor else R.string.start_monitor)) }
        var ov by remember { mutableStateOf(StatusBarOverlay.enabled(ctx) && StatusBarOverlay.canDraw(ctx)) }
        var pos by remember { mutableIntStateOf(StatusBarOverlay.position(ctx)) }
        Text(stringResource(R.string.statusbar_title), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.statusbar_hint), color = Dim, fontSize = 14.sp)
        Button(
            onClick = {
                if (!ov && !StatusBarOverlay.canDraw(ctx)) { ctx.startActivity(StatusBarOverlay.permissionIntent(ctx)); return@Button }
                ov = !ov
                StatusBarOverlay.prefs(ctx).edit().putBoolean("overlay", ov).apply()
                if (ov && !MonitorService.running) { MonitorService.start(ctx); live = true }
                if (!ov) StatusBarOverlay.remove(ctx)
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (ov) CNet else Card, contentColor = if (ov) Bg else Fg),
        ) { Text(stringResource(if (ov) R.string.statusbar_on else R.string.statusbar_off)) }
        var dx by remember { mutableIntStateOf(StatusBarOverlay.nudge(ctx)) }
        var camOn by remember { mutableStateOf(StatusBarOverlay.avoidCutout(ctx)) }
        var camAuto by remember { mutableStateOf(StatusBarOverlay.camAuto(ctx)) }
        var camW by remember { mutableIntStateOf(StatusBarOverlay.camWidth(ctx)) }
        var camM by remember { mutableIntStateOf(StatusBarOverlay.camMargin(ctx)) }
        var camS by remember { mutableIntStateOf(StatusBarOverlay.camShift(ctx)) }
        val rootView = androidx.compose.ui.platform.LocalView.current
        val detected = StatusBarOverlay.detected(rootView)
        val density = ctx.resources.displayMetrics.density
        if (ov) {
            Text(stringResource(R.string.overlay_notice_hint), color = Dim, fontSize = 14.sp)
            Button(onClick = {
                try { ctx.startActivity(StatusBarOverlay.overlayNoticeIntent(ctx)) }
                catch (_: Exception) {
                    try { ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Fg)) { Text(stringResource(R.string.overlay_notice_button)) }
            Text(stringResource(R.string.strip_preview_hint), color = Dim, fontSize = 14.sp)
            val camDp = (if (camAuto && detected != null) detected.width() else camW) / density
            StripPreview(pos, camOn, camDp, camM / density, camS / density, dx.toFloat()) {
                pos = it; StatusBarOverlay.prefs(ctx).edit().putInt("overlay_pos", it).apply(); StatusBarOverlay.refresh(ctx)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, step) in listOf("◀" to -8, "▶" to 8)) Text(
                    label, color = Fg, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Card)
                        .clickable { dx = (dx + step).coerceIn(-150, 150); StatusBarOverlay.prefs(ctx).edit().putInt("overlay_dx", dx).apply(); StatusBarOverlay.refresh(ctx) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        PrefSwitch(stringResource(R.string.avoid_cutout), stringResource(R.string.avoid_cutout_hint), "avoid_cutout", true) { camOn = it }
        if (camOn) {
            PrefSwitch(stringResource(R.string.cam_auto), stringResource(R.string.cam_auto_hint), "cam_auto", true) { camAuto = it }
            val auto = camAuto && detected != null
            if (auto) Text(stringResource(R.string.cam_detected, detected!!.width()), color = Dim, fontSize = 14.sp)
            else {
                if (camAuto) Text(stringResource(R.string.cam_none), color = Dim, fontSize = 14.sp)
                PrefSlider(stringResource(R.string.cam_width), "cam_w", 100, 0..400, "px") { camW = it }
            }
            PrefSlider(stringResource(R.string.cam_margin), "cam_m", 8, 0..120, "px") { camM = it }
            PrefSlider(stringResource(R.string.cam_shift), "cam_s", 0, -300..300, "px") { camS = it }
        }
        PrefSwitch(stringResource(R.string.strip_autohide), stringResource(R.string.strip_autohide_hint), "strip_autohide")
        Text(stringResource(R.string.notif_opts_title), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        PrefSwitch(stringResource(R.string.notif_only_online), stringResource(R.string.notif_only_online_hint), "notif_online_only")
        var picked by remember { mutableStateOf(StatusItems.selected(ctx).toSet()) }
        Text(stringResource(R.string.items_title), color = Dim, fontSize = 14.sp)
        ItemsPicker(picked) { picked = it; StatusItems.setSelected(ctx, it); StatusBarOverlay.refresh(ctx) }
        PrefSwitch(stringResource(R.string.setup_icons), stringResource(R.string.setup_icons_hint), "icons")
        PrefSwitch(stringResource(R.string.rotate_icon), stringResource(R.string.rotate_icon_hint), "rotate")
        Button(onClick = { showSetup = true }, colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Fg)) {
            Text(stringResource(R.string.setup_again))
        }
        if (!UpdateChecker.fromPlay(ctx)) {
            Text(stringResource(R.string.update_title), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            PrefSwitch(stringResource(R.string.update_check), stringResource(R.string.update_check_hint), "update_check")
            var upMsg by remember { mutableStateOf<String?>(null) }
            var upInfo by remember { mutableStateOf<UpdateChecker.Info?>(null) }
            val upCtx = LocalContext.current
            val upNone = stringResource(R.string.update_none, UpdateChecker.current(ctx))
            val upFail = stringResource(R.string.update_failed)
            Button(onClick = {
                upMsg = "…"
                UpdateChecker.checkNow(ctx) { i, ok -> upInfo = i; upMsg = if (!ok) upFail else if (i == null) upNone else upCtx.getString(R.string.update_available, i.version) }
            }, colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Fg)) { Text(stringResource(R.string.update_now)) }
            upMsg?.let { Text(it, color = Dim, fontSize = 14.sp) }
            upInfo?.let { i -> Button(onClick = { UpdateChecker.open(ctx, i) }, colors = ButtonDefaults.buttonColors(containerColor = CMem, contentColor = Bg)) { Text(stringResource(R.string.update_download)) } }
        }
        Text(stringResource(R.string.theme_title), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (pal in listOfNotNull(Themes.custom) + Themes.all) {
                val sel = Themes.current === pal
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) CMem else Card)
                        .clickable { Themes.select(ctx, pal); StatsWidget.update(ctx) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(Color(pal.accent)))
                    Text(pal.name, color = if (sel) Bg else Fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Button(
            onClick = { (ctx as MainActivity).importTheme.launch(arrayOf("*/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Fg),
        ) { Text(stringResource(R.string.theme_import)) }
        val mgr = AppWidgetManager.getInstance(ctx)
        Text(stringResource(R.string.widgets_title), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.widgets_hint), color = Dim, fontSize = 14.sp)
        // galerij: elk soort als plaatje (live, met de beginwaarden van dat soort); tik erop om het op het beginscherm te zetten
        val cols = mapOf(WidgetKind.MINI to 1, WidgetKind.DUO to 2, WidgetKind.SMALL to 2, WidgetKind.STRIP to 4, WidgetKind.DASHBOARD to 4, WidgetKind.LARGE to 4)
        for (kind in listOf(WidgetKind.MINI, WidgetKind.DUO, WidgetKind.SMALL, WidgetKind.STRIP, WidgetKind.DASHBOARD, WidgetKind.LARGE)) {
            val pic = remember(s, kind) { StatsRenderer.render(s, 480, (480 * kind.aspect).toInt().coerceAtLeast(90), kind.defaults) }
            // elk soort is één grote knop: kader, plaatje en een duidelijke "+ Toevoegen"
            val add = {
                if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(ComponentName(ctx, kind.cls), null, null)
                else android.widget.Toast.makeText(ctx, R.string.widget_pin_unsupported, android.widget.Toast.LENGTH_LONG).show()
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).border(1.5.dp, CMem.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .clickable { add() }.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    pic.asImageBitmap(), stringResource(R.string.add_widget_kind, stringResource(kind.label)),
                    Modifier.fillMaxWidth((cols.getValue(kind) / 4f).coerceAtLeast(0.3f)).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.FillWidth,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(kind.label), color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.add_short), color = Bg, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(CMem).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
        // per geplaatste widget een instellingenknop (de launcher opent het instelscherm bij vastgezette widgets zelf niet)
        var n = 0
        for (kind in WidgetKind.entries) for (id in kind.ids(ctx)) {
            n++; val nr = n
            Button(
                onClick = { ctx.startActivity(Intent(ctx, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)) },
                colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Fg),
            ) { Text(stringResource(R.string.widget_settings_n, nr) + " (" + stringResource(kind.label) + ")") }
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
private fun TilesTab() {
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
