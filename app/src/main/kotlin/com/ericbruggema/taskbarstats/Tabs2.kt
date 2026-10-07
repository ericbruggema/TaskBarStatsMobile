package com.ericbruggema.taskbarstats

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Telt op bij elke terugkeer naar de app (bijv. uit Instellingen), zodat toestemmingen opnieuw gelezen worden. */
internal var resumeTick by mutableIntStateOf(0)

private val Good get() = CDisk
private val Warn get() = CTemp

// ---------- kleine bouwstenen ----------

@Composable
internal fun Chips(labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l ->
            Text(
                l, color = if (i == sel) Bg else Fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (i == sel) CMem else Card)
                    .clickable { onSel(i) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun H(text: String) = Text(text, color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun Hint(text: String) = Text(text, color = Dim, fontSize = 14.sp)

@Composable
private fun Btn(text: String, accent: Boolean = true, onClick: () -> Unit) = Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(containerColor = if (accent) CMem else Card, contentColor = if (accent) Bg else Fg),
) { Text(text) }

@Composable
private fun TwoCol(left: String, right: String, sub: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(left, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Text(right, color = Dim, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
        if (sub != null) Text(sub, color = Dim, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun BarRow(left: String, right: String, frac: Float, color: Color, sub: String? = null, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(left, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Text(right, color = Dim, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
        if (sub != null) Text(sub, color = Dim, fontSize = 12.sp, maxLines = 1)
        Canvas(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp)) {
            drawRoundRect(Fg.copy(alpha = 0.15f), cornerRadius = CornerRadius(size.height / 2))
            drawRoundRect(color, size = Size(size.width * frac.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(size.height / 2))
        }
    }
}

/** Kaart die uitlegt welke toestemming ontbreekt en de gebruiker er met één tik naartoe brengt. */
@Composable
private fun NeedCard(title: String, text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Warn, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text, color = Fg, fontSize = 14.sp)
        Btn(button, onClick = onClick)
    }
}

private class Holder<T>(val value: T)

/** Laadt gegevens op een achtergrondthread (opnieuw na terugkeer uit Instellingen); [every] > 0 ververst periodiek. */
@Composable
@Suppress("ProduceStateDoesNotAssignValue") // valse melding: de waarde wordt in de lus gezet
private fun <T> Loaded(key: Any, every: Long = 0, load: (Context) -> T, initial: T? = null, content: @Composable (T) -> Unit) {
    val ctx = LocalContext.current
    val h by produceState<Holder<T>?>(null, key, resumeTick) {
        value = initial?.let { Holder(it) }
        while (true) {
            value = Holder(withContext(Dispatchers.IO) { load(ctx) })
            if (every <= 0) break
            delay(every)
        }
    }
    val v = h
    if (v == null) Hint(stringResource(R.string.loading)) else content(v.value)
}

private fun fmtDur(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

// ---------- Apps ----------

@Composable
internal fun AppsTab() {
    val ctx = LocalContext.current
    var sec by remember { mutableIntStateOf(0) }
    var days by remember { mutableIntStateOf(1) }
    var dataDays by remember { mutableIntStateOf(7) }
    var byCache by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var storageSel by remember { mutableStateOf<AppData.Sized?>(null) }
    var procSel by remember { mutableStateOf<AppData.Proc?>(null) }
    var sys by remember { mutableStateOf(false) }
    val names = listOf(R.string.apps_data, R.string.apps_traffic, R.string.apps_storage, R.string.apps_usage, R.string.apps_autostart, R.string.apps_procs)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Chips(names.map { stringResource(it) }, sec) { sec = it }
        val needsAccess = sec in 0..3
        if (needsAccess && !AppData.hasUsageAccess(ctx)) {
            @Suppress("UNUSED_EXPRESSION") resumeTick
            NeedCard(stringResource(R.string.perm_usage_name), stringResource(R.string.need_usage), stringResource(R.string.btn_open_settings)) {
                ctx.startActivity(AppData.usageIntent(ctx))
            }
            return@Column
        }
        if (sec == 1 || sec == 3) Chips(
            listOf(stringResource(R.string.range_today), stringResource(R.string.range_7), stringResource(R.string.range_30)),
            when (days) { 1 -> 0; 7 -> 1; else -> 2 }) { days = listOf(1, 7, 30)[it] }
        when (sec) {
            0 -> Loaded("data$dataDays", 0, { AppData.daily(it, dataDays) }) { rows ->
                val fmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
                Chips(listOf(stringResource(R.string.range_7), stringResource(R.string.range_30)), if (dataDays == 7) 0 else 1) { dataDays = if (it == 0) 7 else 30 }
                H(stringResource(R.string.apps_data)); Hint(stringResource(R.string.data_hint))
                TwoCol(stringResource(R.string.total_word), "Wi-Fi ${Fmt.bytes(rows.sumOf { it.wifi })}  ·  ${stringResource(R.string.mobile_word)} ${Fmt.bytes(rows.sumOf { it.mobile })}")
                for (d in rows) TwoCol(fmt.format(Date(d.start)),
                    "Wi-Fi ${Fmt.bytes(d.wifi)}  ·  ${stringResource(R.string.mobile_word)} ${Fmt.bytes(d.mobile)}")
            }
            1 -> Loaded("traffic$days", 0, { AppData.traffic(it, days) }) { rows ->
                H(stringResource(R.string.apps_traffic))
                if (rows.isEmpty()) Hint(stringResource(R.string.empty_list))
                val max = rows.maxOfOrNull { it.total }?.toFloat() ?: 1f
                for (r in rows) BarRow(r.label, "↓ ${Fmt.bytes(r.rx)}  ↑ ${Fmt.bytes(r.tx)}", r.total / max, CNet)
            }
            2 -> Loaded("storage$reload", 0, { AppData.storage(it) }, initial = AppData.lastStorage) { rows ->
                H(stringResource(R.string.apps_storage)); Hint(stringResource(R.string.storage_hint))
                val totalCache = rows.sumOf { it.cache }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.stor_cache_total), color = CDisk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(Fmt.bytes(totalCache), color = Fg, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (ShizukuCpu.state == ShizukuCpu.State.ACTIVE) R.string.stor_clear_hint else R.string.stor_clear_hint_plain), color = Dim, fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ShizukuCpu.state == ShizukuCpu.State.ACTIVE) {
                            val scope = androidx.compose.runtime.rememberCoroutineScope()
                            Btn(stringResource(R.string.stor_clear_all)) {
                                scope.launch {
                                    val rc = withContext(Dispatchers.IO) { ShizukuCpu.runAction(3, null) }
                                    android.widget.Toast.makeText(ctx, if (rc == 0) R.string.stor_cleared else R.string.d_failed, android.widget.Toast.LENGTH_SHORT).show()
                                    reload++
                                }
                            }
                        }
                        Btn(stringResource(R.string.stor_open_settings), accent = ShizukuCpu.state != ShizukuCpu.State.ACTIVE) { openStorageSettings(ctx) }
                    }
                }
                Chips(listOf(stringResource(R.string.stor_sort_size), stringResource(R.string.stor_sort_cache)), if (byCache) 1 else 0) { byCache = it == 1 }
                val sorted = if (byCache) rows.sortedByDescending { it.cache } else rows
                val max = sorted.maxOfOrNull { if (byCache) it.cache else it.bytes }?.toFloat()?.coerceAtLeast(1f) ?: 1f
                for (r in sorted.take(40)) BarRow(
                    r.label, if (byCache) Fmt.bytes(r.cache) else Fmt.bytes(r.bytes), (if (byCache) r.cache else r.bytes) / max, CDisk,
                    sub = "${stringResource(R.string.stor_app)} ${Fmt.bytes(r.app)}  \u00B7  ${stringResource(R.string.stor_data)} ${Fmt.bytes(r.data)}  \u00B7  ${stringResource(R.string.stor_cache)} ${Fmt.bytes(r.cache)}",
                ) { storageSel = r }
            }
            3 -> Loaded("usage$days", 0, { AppData.mostUsed(it, days) }) { rows ->
                H(stringResource(R.string.apps_usage))
                if (rows.isEmpty()) Hint(stringResource(R.string.empty_list))
                val max = rows.maxOfOrNull { it.ms }?.toFloat() ?: 1f
                for (r in rows) BarRow(r.label, fmtDur(r.ms), r.ms / max, CMem)
            }
            4 -> Loaded("auto", 0, { AppData.autostart(it) }) { rows ->
                H(stringResource(R.string.apps_autostart)); Hint(stringResource(R.string.autostart_hint))
                Chips(listOf(stringResource(R.string.autostart_user), stringResource(R.string.autostart_all)), if (sys) 1 else 0) { sys = it == 1 }
                for (r in rows.filter { sys || !it.system }) TwoCol(r.label, if (r.system) stringResource(R.string.system_word) else "", r.pkg)
            }
            else -> {
                H(stringResource(R.string.apps_procs)); Hint(stringResource(R.string.procs_hint))
                Loaded("procs", 3000, { AppData.processes(it) }) { rows ->
                    if (rows == null) {
                        val hint = stringResource(when (ShizukuCpu.state) {
                            ShizukuCpu.State.NOT_INSTALLED -> R.string.shizuku_not_installed
                            ShizukuCpu.State.NOT_RUNNING -> R.string.shizuku_not_running
                            ShizukuCpu.State.NEEDS_PERMISSION -> R.string.shizuku_needs_permission
                            else -> R.string.shizuku_connecting
                        })
                        NeedCard(stringResource(R.string.perm_shizuku_name), hint, stringResource(R.string.perm_open_tab)) { (ctx as MainActivity).goToTab(PERMISSIONS_TAB) }
                    } else {
                        Hint(stringResource(R.string.procs_tap))
                        for (r in rows.take(30)) TwoCol(r.label, String.format(Locale.getDefault(), "%.1f%%  \u00B7  %s", r.cpu, Fmt.bytes(r.rssKb * 1024)), "PID ${r.pid}") { procSel = r }
                    }
                }
            }
        }
        storageSel?.let { StorageDialog(it, { storageSel = null }) { reload++ } }
        procSel?.let { ProcessDialog(it) { procSel = null } }
    }
}

/** Een aan/uit-instelling in de gedeelde voorkeuren (bijv. voor de melding). */
@Composable
internal fun PrefSwitch(label: String, hint: String?, key: String, default: Boolean = false, onChange: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(StatusBarOverlay.prefs(ctx).getBoolean(key, default)) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = on, onCheckedChange = { on = it; StatusBarOverlay.prefs(ctx).edit().putBoolean(key, it).apply(); onChange(it); StatusBarOverlay.refresh(ctx) },
            colors = SwitchDefaults.colors(checkedTrackColor = CMem, checkedThumbColor = Bg),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(label, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (hint != null) Text(hint, color = Dim, fontSize = 13.sp)
        }
    }
}

// ---------- Geschiedenis ----------

@Composable
internal fun HistoryTab() {
    val m = Sampler.minutes
    val n = m.mem.size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H(stringResource(R.string.tab_history)); Hint(stringResource(R.string.history_hint))
        val window = if (n >= 60) "${n / 60} h" else "$n min"
        val sub = if (n < 2) stringResource(R.string.history_collecting) else stringResource(R.string.history_window, window)
        HistTile(stringResource(R.string.memory), m.mem.lastOrNull()?.let { Fmt.percent(it) } ?: "—", sub, CMem, m.mem, 100f)
        HistTile("↓ " + stringResource(R.string.download), m.rx.lastOrNull()?.let { Fmt.rate(it) } ?: "—", sub, CNet, m.rx, null)
        HistTile("↑ " + stringResource(R.string.upload), m.tx.lastOrNull()?.let { Fmt.rate(it) } ?: "—", sub, CUp, m.tx, null)
        HistTile(stringResource(R.string.ping), m.ping.lastOrNull()?.let { "${it.toInt()} ms" } ?: "—", sub, CPing, m.ping, null)
        if (m.cpu.isNotEmpty()) HistTile(stringResource(R.string.cpu), Fmt.percent(m.cpu.last()), sub, CCpu, m.cpu, 100f)
    }
}

@Composable
private fun HistTile(label: String, value: String, sub: String, color: Color, hist: List<Float>, max: Float?) =
    Tile(label, value, sub, color, hist, max)

// ---------- Meldingen ----------

@Composable
internal fun AlertsTab() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H(stringResource(R.string.alerts_title)); Hint(stringResource(R.string.alerts_hint))
        @Suppress("UNUSED_EXPRESSION") resumeTick
        if (!ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
            NeedCard(stringResource(R.string.perm_notif_name), stringResource(R.string.alerts_notif_off), stringResource(R.string.btn_open_settings)) {
                ctx.startActivity(notificationSettings(ctx))
            }
        for (r in Alerts.rules) {
            var on by remember { mutableStateOf(Alerts.enabled(ctx, r)) }
            var v by remember { mutableIntStateOf(Alerts.threshold(ctx, r)) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = on, onCheckedChange = { on = it; Alerts.setEnabled(ctx, r, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = CMem, checkedThumbColor = Bg),
                )
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(stringResource(r.label), color = if (on) Fg else Dim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("$v${r.unit}", color = Dim, fontSize = 14.sp)
                }
                for ((label, d) in listOf("−" to -r.step, "+" to r.step)) Text(
                    label, color = Fg, fontSize = 20.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                        v = (v + d).coerceIn(r.min, r.max); Alerts.setThreshold(ctx, r, v)
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Hint(stringResource(R.string.alerts_service_note))
    }
}

// ---------- Toestemmingen ----------

internal const val PERMISSIONS_TAB = 7

private fun notificationSettings(ctx: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

@Composable
private fun PermCard(name: String, granted: Boolean?, why: String, actions: List<Pair<String, () -> Unit>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                stringResource(when (granted) { true -> R.string.perm_granted; false -> R.string.perm_not_granted; null -> R.string.perm_optional }),
                color = when (granted) { true -> Good; false -> Warn; null -> Dim }, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }
        Text(why, color = Dim, fontSize = 14.sp)
        if (granted != true) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, a) in actions.withIndex()) Btn(a.first, accent = i == 0, onClick = a.second)
        }
    }
}

@Composable
internal fun PermissionsTab() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_EXPRESSION") resumeTick
    val shizuku = ShizukuCpu.state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H(stringResource(R.string.perm_title)); Hint(stringResource(R.string.perm_hint))

        val notifOn = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        PermCard(stringResource(R.string.perm_notif_name), notifOn, stringResource(R.string.perm_notif_why), buildList {
            if (Build.VERSION.SDK_INT >= 33) add(stringResource(R.string.btn_allow) to {
                (ctx as ComponentActivity).requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            })
            add(stringResource(R.string.btn_open_settings) to { ctx.startActivity(notificationSettings(ctx)) })
        })

        PermCard(stringResource(R.string.perm_overlay_name), StatusBarOverlay.canDraw(ctx), stringResource(R.string.perm_overlay_why),
            listOf(stringResource(R.string.btn_open_settings) to { ctx.startActivity(StatusBarOverlay.permissionIntent(ctx)) }))

        PermCard(stringResource(R.string.perm_usage_name), AppData.hasUsageAccess(ctx), stringResource(R.string.perm_usage_why),
            listOf(stringResource(R.string.btn_open_settings) to { ctx.startActivity(AppData.usageIntent(ctx)) }))

        val ignoring = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
        PermCard(stringResource(R.string.perm_battery_name), ignoring, stringResource(R.string.perm_battery_why),
            listOf(stringResource(R.string.btn_open_settings) to { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }))

        val shizukuWhy = stringResource(when (shizuku) {
            ShizukuCpu.State.NOT_INSTALLED -> R.string.shizuku_not_installed
            ShizukuCpu.State.NOT_RUNNING -> R.string.shizuku_not_running
            ShizukuCpu.State.NEEDS_PERMISSION -> R.string.shizuku_needs_permission
            ShizukuCpu.State.CONNECTING -> R.string.shizuku_connecting
            ShizukuCpu.State.ACTIVE -> R.string.shizuku_active
        })
        PermCard(stringResource(R.string.perm_shizuku_name), if (shizuku == ShizukuCpu.State.ACTIVE) true else false,
            stringResource(R.string.perm_shizuku_why) + "\n\n" + shizukuWhy,
            when (shizuku) {
                ShizukuCpu.State.NOT_INSTALLED -> listOf(stringResource(R.string.btn_get_shizuku) to {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                })
                ShizukuCpu.State.NOT_RUNNING -> listOf(stringResource(R.string.btn_open_shizuku) to {
                    ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { ctx.startActivity(it) }
                })
                else -> listOf(stringResource(R.string.shizuku_grant) to { ShizukuCpu.requestPermission() })
            })

        PermCard(stringResource(R.string.perm_allpkgs_name), true, stringResource(R.string.perm_allpkgs_why), emptyList())
    }
}
