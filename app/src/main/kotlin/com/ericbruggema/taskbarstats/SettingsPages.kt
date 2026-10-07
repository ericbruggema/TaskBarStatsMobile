package com.ericbruggema.taskbarstats

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/** Pagina's binnen Instellingen: het overzicht (hub) en de categorieën die daaruit openen. */
internal const val SP_HUB = 0
internal const val SP_STRIP = 1
internal const val SP_NOTIF = 2
internal const val SP_ITEMS = 3
internal const val SP_WIDGETS = 4
internal const val SP_TILES = 5
internal const val SP_THEME = 6
internal const val SP_PERMS = 7
internal const val SP_UPDATES = 8
internal const val SP_CAMERA = 9
internal const val SP_ABOUT = 10
internal const val SP_FONT = 11
internal const val SP_EDIT = 12

/** Tabblad "Instellingen" in de onderbalk. */
internal const val TAB_SETTINGS = 8

internal var settingsPage by mutableIntStateOf(SP_HUB)

/** Telt op als een instelling het voorbeeld bovenaan moet verversen. */
private var previewTick by mutableIntStateOf(0)

/** Opent een tabblad; de oude nummers 2 (Widget), 3 (Tegels) en 7 (Rechten) wijzen nu naar een instellingenpagina. */
internal fun openTab(i: Int) {
    when (i) {
        2 -> { currentTab = TAB_SETTINGS; settingsPage = SP_STRIP }
        3 -> { currentTab = TAB_SETTINGS; settingsPage = SP_TILES }
        7 -> { currentTab = TAB_SETTINGS; settingsPage = SP_PERMS }
        else -> currentTab = i
    }
}

/** Terugknop: één stap omhoog binnen Instellingen. Geeft true als er iets te sluiten viel. */
internal fun settingsBack(): Boolean {
    if (currentTab != TAB_SETTINGS || settingsPage == SP_HUB) return false
    settingsPage = when (settingsPage) { SP_CAMERA -> SP_STRIP; SP_EDIT -> SP_THEME; else -> SP_HUB }
    return true
}

@Composable
internal fun SettingsTab(s: Snapshot) {
    when (settingsPage) {
        SP_STRIP -> StripPage()
        SP_CAMERA -> CameraPage()
        SP_NOTIF -> NotifPage(s)
        SP_ITEMS -> ItemsPage()
        SP_WIDGETS -> WidgetsPage(s)
        SP_TILES -> WrappedPage { TilesTab() }
        SP_THEME -> ThemePage(s)
        SP_PERMS -> WrappedPage { PermissionsTab() }
        SP_UPDATES -> UpdatesPage()
        SP_ABOUT -> AboutPage()
        SP_FONT -> FontPage(s)
        SP_EDIT -> ThemeEditPage()
        else -> Hub()
    }
}

// ---------- bouwstenen ----------

@Composable
private fun BackBar(title: String, back: Int = SP_HUB) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹", color = Fg, fontSize = 30.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { settingsPage = back }.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Text(title, color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Een categoriepagina: terugknop met titel, optioneel een voorbeeld dat bovenaan blijft staan, daaronder de bediening. */
@Composable
private fun Page(title: String, back: Int = SP_HUB, fixed: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar(title, back)
        if (fixed != null) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)) { fixed() }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = PAGE_BOTTOM),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

/** Een bestaande pagina met eigen kop (Tegels, Rechten) onder een terugknop. */
@Composable
private fun WrappedPage(body: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar(stringResource(R.string.nav_settings))
        Box(Modifier.weight(1f)) { body() }
    }
}

@Composable
private fun SectionNote(text: String) = Text(text, color = Dim, fontSize = 14.sp)

@Composable
private fun BtnCard(text: String, accent: Boolean = false, color: Color? = null, onClick: () -> Unit) = Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(containerColor = color ?: if (accent) CMem else Card, contentColor = if (accent || color != null) Bg else Fg),
) { Text(text) }

@Composable
private fun HubRow(title: String, summary: String, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
            .pointerInput(onLong) { detectTapGestures(onLongPress = { onLong?.invoke() }, onTap = { onClick() }) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (summary.isNotEmpty()) Text(summary, color = Dim, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        Text("›", color = Dim, fontSize = 22.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

// ---------- overzicht ----------

@Composable
private fun Hub() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_EXPRESSION") resumeTick
    val on = stringResource(R.string.set_on); val off = stringResource(R.string.set_off)
    val stripOn = StatusBarOverlay.enabled(ctx) && StatusBarOverlay.canDraw(ctx)
    val missing = listOf(
        ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        StatusBarOverlay.canDraw(ctx), AppData.hasUsageAccess(ctx),
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName),
    ).count { !it }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = PAGE_BOTTOM),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.nav_settings), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        HubRow(stringResource(R.string.set_strip), if (stripOn) on else off) { settingsPage = SP_STRIP }
        HubRow(stringResource(R.string.set_notif), "") { settingsPage = SP_NOTIF }
        HubRow(stringResource(R.string.set_items), stringResource(R.string.set_items_n, StatusItems.selected(ctx).size)) { settingsPage = SP_ITEMS }
        HubRow(stringResource(R.string.widgets_title), "") { settingsPage = SP_WIDGETS }
        HubRow(stringResource(R.string.tab_tiles), "") { settingsPage = SP_TILES }
        var longs by remember { mutableIntStateOf(0) }
        val unlockedMsg = stringResource(R.string.theme_unlocked, Themes.secret.name)
        HubRow(stringResource(R.string.theme_title), Themes.current.name, onLong = {
            longs++
            if (longs >= 5 && !Themes.secretOn) { Themes.unlockSecret(ctx); android.widget.Toast.makeText(ctx, unlockedMsg, android.widget.Toast.LENGTH_LONG).show() }
        }) { settingsPage = SP_THEME }
        val fam = listOf(R.string.font_f0, R.string.font_f1, R.string.font_f2, R.string.font_f3, R.string.font_f4, R.string.font_f5)
        HubRow(stringResource(R.string.set_font), stringResource(fam[Fonts.family]) + " \u00B7 " + Fonts.scale + "%") { settingsPage = SP_FONT }
        HubRow(stringResource(R.string.tab_permissions), if (missing == 0) stringResource(R.string.set_all_ok) else stringResource(R.string.set_missing, missing)) { settingsPage = SP_PERMS }
        if (!UpdateChecker.fromPlay(ctx)) HubRow(stringResource(R.string.update_title), if (UpdateChecker.enabled(ctx)) on else off) { settingsPage = SP_UPDATES }
        HubRow(stringResource(R.string.setup_again), "") { showSetup = true }
        HubRow(stringResource(R.string.set_about), UpdateChecker.current(ctx)) { settingsPage = SP_ABOUT }
    }
}

// ---------- statusbalkstrook ----------

/** Voorbeeld van de statusbalk; blijft bovenaan staan zodat je schuifregelaars en keuzes meteen ziet werken. */
@Composable
private fun LivePreview() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = previewTick
    val density = ctx.resources.displayMetrics.density
    val detected = StatusBarOverlay.detected(LocalView.current)
    val camDp = (if (StatusBarOverlay.camAuto(ctx) && detected != null) detected.width() else StatusBarOverlay.camWidth(ctx)) / density
    StripPreview(
        StatusBarOverlay.position(ctx), StatusBarOverlay.avoidCutout(ctx), camDp,
        StatusBarOverlay.camMargin(ctx) / density, StatusBarOverlay.camShift(ctx) / density, StatusBarOverlay.nudge(ctx).toFloat(),
    ) {
        StatusBarOverlay.prefs(ctx).edit().putInt("overlay_pos", it).apply(); StatusBarOverlay.refresh(ctx); previewTick++
    }
}

@Composable
private fun StripPage() {
    val ctx = LocalContext.current
    var ov by remember { mutableStateOf(StatusBarOverlay.enabled(ctx) && StatusBarOverlay.canDraw(ctx)) }
    var dx by remember { mutableIntStateOf(StatusBarOverlay.nudge(ctx)) }
    val tick = previewTick
    val pos = remember(tick) { StatusBarOverlay.position(ctx) }
    val camOn = remember(tick) { StatusBarOverlay.avoidCutout(ctx) }
    Page(stringResource(R.string.set_strip), fixed = { LivePreview() }) {
        SectionNote(stringResource(R.string.statusbar_hint))
        BtnCard(stringResource(if (ov) R.string.statusbar_on else R.string.statusbar_off), color = if (ov) CNet else null) {
            if (!ov && !StatusBarOverlay.canDraw(ctx)) { ctx.startActivity(StatusBarOverlay.permissionIntent(ctx)); return@BtnCard }
            ov = !ov
            StatusBarOverlay.prefs(ctx).edit().putBoolean("overlay", ov).apply()
            if (ov && !MonitorService.running) MonitorService.start(ctx)
            if (!ov) StatusBarOverlay.remove(ctx)
        }
        SectionNote(stringResource(R.string.strip_preview_hint))
        Chips(listOf(stringResource(R.string.pos_left), stringResource(R.string.pos_center), stringResource(R.string.pos_right)), pos) {
            StatusBarOverlay.prefs(ctx).edit().putInt("overlay_pos", it).apply(); StatusBarOverlay.refresh(ctx); previewTick++
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, step) in listOf("◀" to -8, "▶" to 8)) Text(
                label, color = Fg, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Card)
                    .clickable { dx = (dx + step).coerceIn(-150, 150); StatusBarOverlay.prefs(ctx).edit().putInt("overlay_dx", dx).apply(); StatusBarOverlay.refresh(ctx); previewTick++ }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        PrefSwitch(stringResource(R.string.avoid_cutout), stringResource(R.string.avoid_cutout_hint), "avoid_cutout", true) { previewTick++ }
        if (camOn) HubRow(stringResource(R.string.cam_open), "") { settingsPage = SP_CAMERA }
        PrefSwitch(stringResource(R.string.strip_autohide), stringResource(R.string.strip_autohide_hint), "strip_autohide")
        PrefSwitch(stringResource(R.string.hide_main_icon), stringResource(R.string.hide_main_icon_hint), "hide_main_icon", true)
        if (ov) {
            SectionNote(stringResource(R.string.overlay_notice_hint))
            BtnCard(stringResource(R.string.overlay_notice_button)) {
                try { ctx.startActivity(StatusBarOverlay.overlayNoticeIntent(ctx)) }
                catch (_: Exception) {
                    try { ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                }
            }
        }
    }
}

@Composable
private fun CameraPage() {
    val ctx = LocalContext.current
    val tick = previewTick
    val camAuto = remember(tick) { StatusBarOverlay.camAuto(ctx) }
    val detected = StatusBarOverlay.detected(LocalView.current)
    Page(stringResource(R.string.cam_title), back = SP_STRIP, fixed = { LivePreview() }) {
        PrefSwitch(stringResource(R.string.cam_auto), stringResource(R.string.cam_auto_hint), "cam_auto", true) { previewTick++ }
        if (camAuto && detected != null) SectionNote(stringResource(R.string.cam_detected, detected.width()))
        else {
            if (camAuto) SectionNote(stringResource(R.string.cam_none))
            PrefSlider(stringResource(R.string.cam_width), "cam_w", 100, 0..400, "px") { previewTick++ }
        }
        PrefSlider(stringResource(R.string.cam_margin), "cam_m", 8, 0..120, "px") { previewTick++ }
        PrefSlider(stringResource(R.string.cam_shift), "cam_s", 0, -300..300, "px") { previewTick++ }
    }
}

// ---------- melding en iconen ----------

@Composable
private fun NotifPage(s: Snapshot) {
    val ctx = LocalContext.current
    var live by remember { mutableStateOf(MonitorService.running) }
    val bmp = remember(s) { StatsRenderer.render(s, 800, 340) }
    val p = StatusBarOverlay.prefs(ctx)
    var mode by remember { mutableIntStateOf(if (p.getBoolean("rotate", false)) 2 else if (p.getBoolean("icons", false)) 1 else 0) }
    Page(stringResource(R.string.set_notif)) {
        SectionNote(stringResource(R.string.widget_hint))
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
        BtnCard(stringResource(if (live) R.string.stop_monitor else R.string.start_monitor), accent = true) {
            if (live) MonitorService.stop(ctx) else MonitorService.start(ctx); live = !live
        }
        Text(stringResource(R.string.icon_mode), color = Fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Chips(listOf(stringResource(R.string.icon_single), stringResource(R.string.icon_separate), stringResource(R.string.icon_rotate)), mode) {
            mode = it
            p.edit().putBoolean("icons", it == 1).putBoolean("rotate", it == 2).apply(); StatusBarOverlay.refresh(ctx)
        }
        SectionNote(stringResource(R.string.icon_mode_hint))
        PrefSwitch(stringResource(R.string.hide_main_icon), stringResource(R.string.hide_main_icon_hint), "hide_main_icon", true)
        PrefSwitch(stringResource(R.string.notif_only_online), stringResource(R.string.notif_only_online_hint), "notif_online_only")
    }
}

@Composable
private fun ItemsPage() {
    val ctx = LocalContext.current
    var picked by remember { mutableStateOf(StatusItems.selected(ctx).toSet()) }
    Page(stringResource(R.string.set_items)) {
        SectionNote(stringResource(R.string.items_title))
        ItemsPicker(picked) { picked = it; StatusItems.setSelected(ctx, it); StatusBarOverlay.refresh(ctx) }
    }
}

// ---------- startschermwidgets ----------

@Composable
private fun WidgetsPage(s: Snapshot) {
    val ctx = LocalContext.current
    val mgr = AppWidgetManager.getInstance(ctx)
    Page(stringResource(R.string.widgets_title)) {
        SectionNote(stringResource(R.string.widgets_hint))
        // galerij: elk soort als plaatje (live, met de beginwaarden van dat soort); tik erop om het op het beginscherm te zetten
        val cols = mapOf(WidgetKind.MINI to 1, WidgetKind.DUO to 2, WidgetKind.SMALL to 2, WidgetKind.STRIP to 4, WidgetKind.DASHBOARD to 4, WidgetKind.LARGE to 4)
        for (kind in listOf(WidgetKind.MINI, WidgetKind.DUO, WidgetKind.SMALL, WidgetKind.STRIP, WidgetKind.DASHBOARD, WidgetKind.LARGE)) {
            val pic = remember(s, kind) { StatsRenderer.render(s, 480, (480 * kind.aspect).toInt().coerceAtLeast(90), kind.defaults) }
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
            BtnCard(stringResource(R.string.widget_settings_n, nr) + " (" + stringResource(kind.label) + ")") {
                ctx.startActivity(Intent(ctx, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            }
        }
    }
}

// ---------- thema ----------

@Composable
private fun ThemePage(s: Snapshot) {
    val ctx = LocalContext.current
    val cur = Themes.current
    val bmp = remember(s, cur) { StatsRenderer.render(s, 800, 340) }
    val pals = listOfNotNull(Themes.custom) + Themes.all + listOfNotNull(Themes.secret.takeIf { Themes.secretOn })
    Page(stringResource(R.string.theme_title)) {
        SectionNote(stringResource(R.string.theme_hint))
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
        for (row in pals.chunked(2)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (pal in row) ThemeCard(pal, pal === cur, Modifier.weight(1f)) { Themes.select(ctx, pal); StatsWidget.update(ctx) }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
        BtnCard(stringResource(R.string.theme_edit), accent = true) { settingsPage = SP_EDIT }
        BtnCard(stringResource(R.string.theme_import)) { (ctx as MainActivity).importTheme.launch(arrayOf("*/*")) }
    }
}

/** Een themakaart: een klein voorbeeld in de kleuren van het thema (achtergrond, tekst, accent en waarschuwing). */
@Composable
private fun ThemeCard(pal: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = Color(pal.bg); val tx = Color(pal.text); val ac = Color(pal.accent); val wn = Color(pal.warn)
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(bg)
            .border(if (selected) 3.dp else 1.dp, if (selected) ac else tx.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .clickable { onClick() }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pal.name, color = tx, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            if (selected) Text("\u2713", color = ac, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("MEM", color = ac, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("42%", color = tx, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text("PING", color = wn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("18 ms", color = tx, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(tx.copy(alpha = 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            drawRoundRect(ac, size = androidx.compose.ui.geometry.Size(size.width * 0.42f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in listOf(ac, wn, tx)) Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(c))
        }
    }
}

// ---------- updates ----------

@Composable
private fun UpdatesPage() {
    val ctx = LocalContext.current
    Page(stringResource(R.string.update_title)) {
        PrefSwitch(stringResource(R.string.update_check), stringResource(R.string.update_check_hint), "update_check")
        var upMsg by remember { mutableStateOf<String?>(null) }
        var upInfo by remember { mutableStateOf<UpdateChecker.Info?>(null) }
        val upNone = stringResource(R.string.update_none, UpdateChecker.current(ctx))
        val upFail = stringResource(R.string.update_failed)
        BtnCard(stringResource(R.string.update_now)) {
            upMsg = "…"
            UpdateChecker.checkNow(ctx) { i, ok -> upInfo = i; upMsg = if (!ok) upFail else if (i == null) upNone else ctx.getString(R.string.update_available, i.version) }
        }
        upMsg?.let { SectionNote(it) }
        upInfo?.let { i -> BtnCard(stringResource(R.string.update_download), accent = true) { UpdateChecker.open(ctx, i) } }
    }
}

// ---------- over ----------

@Composable
private fun AboutPage() {
    val ctx = LocalContext.current
    fun open(url: String) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { } }
    val icon = remember { ctx.packageManager.getApplicationIcon(ctx.packageName).toBitmap(192, 192) }
    Page(stringResource(R.string.set_about)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var taps by remember { mutableIntStateOf(0) }
            var last by remember { mutableLongStateOf(0L) }
            var alive by remember { mutableStateOf(false) }
            val scale = remember { androidx.compose.animation.core.Animatable(1f) }
            val view = LocalView.current
            androidx.compose.runtime.LaunchedEffect(alive) {
                if (alive) repeat(3) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    scale.animateTo(1.28f, androidx.compose.animation.core.tween(110)); scale.animateTo(1f, androidx.compose.animation.core.tween(150))
                    scale.animateTo(1.16f, androidx.compose.animation.core.tween(100)); scale.animateTo(1f, androidx.compose.animation.core.tween(420))
                }
            }
            Image(
                icon.asImageBitmap(), null,
                Modifier.size(84.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(RoundedCornerShape(20.dp)).clickable {
                    val now = System.currentTimeMillis()
                    taps = if (now - last < 1500) taps + 1 else 1; last = now
                    if (taps >= 10) { taps = 0; alive = false; alive = true }
                },
            )
            if (alive) Text(stringResource(R.string.about_alive), color = CMem, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.app_name), color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.about_version, UpdateChecker.current(ctx)), color = CMem, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.about_tagline), color = Dim, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        SectionNote(stringResource(R.string.about_by))
        BtnCard(stringResource(R.string.about_github), accent = true) { open("https://github.com/ericbruggema/TaskBarStatsMobile") }
        BtnCard(stringResource(R.string.about_releases)) { open("https://github.com/ericbruggema/TaskBarStatsMobile/releases/latest") }
        BtnCard(stringResource(R.string.about_privacy)) { open("https://github.com/ericbruggema/TaskBarStatsMobile/blob/main/PRIVACY.md") }
        BtnCard(stringResource(R.string.about_windows)) { open("https://github.com/ericbruggema/TaskbarStats") }
        SectionNote(stringResource(R.string.about_credits))
    }
}

// ---------- thema bewerken ----------

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, thumb: Color, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(shown, color = Dim, fontSize = 14.sp)
        }
        androidx.compose.material3.Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = thumb, activeTrackColor = thumb, inactiveTrackColor = Bg),
        )
    }
}

@Composable
private fun fieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = Fg, unfocusedTextColor = Fg, focusedBorderColor = CMem, unfocusedBorderColor = Dim,
    cursorColor = CMem, focusedLabelColor = CMem, unfocusedLabelColor = Dim,
)

/** Eigen thema maken of een bestaand thema aanpassen; elke wijziging is meteen overal zichtbaar. */
@Composable
private fun ThemeEditPage() {
    val ctx = LocalContext.current
    val origCur = remember { Themes.current }
    val origCustom = remember { Themes.custom }
    val myName = stringResource(R.string.theme_my)
    var pal by remember { mutableStateOf(if (Themes.current === Themes.custom) Themes.current else Themes.current.copy(name = myName)) }
    var which by remember { mutableIntStateOf(0) }
    fun colorOf(w: Int) = when (w) { 0 -> pal.bg; 1 -> pal.text; 2 -> pal.accent; else -> pal.warn }
    val hsv = remember(which) { FloatArray(3).also { android.graphics.Color.colorToHSV(colorOf(which), it) } }
    var h by remember(which) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(which) { mutableFloatStateOf(hsv[1]) }
    var v by remember(which) { mutableFloatStateOf(hsv[2]) }
    var hex by remember(which) { mutableStateOf(String.format("%06X", colorOf(which) and 0xFFFFFF)) }
    fun commit(c: Int) {
        pal = when (which) { 0 -> pal.copy(bg = c); 1 -> pal.copy(text = c); 2 -> pal.copy(accent = c); else -> pal.copy(warn = c) }
        Themes.setCustom(ctx, pal); StatsWidget.update(ctx)
    }
    fun fromHsv() { val c = android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)); hex = String.format("%06X", c and 0xFFFFFF); commit(c) }
    val cur = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)))
    val labels = listOf(R.string.col_bg, R.string.col_text, R.string.col_accent, R.string.col_warn)
    Page(stringResource(R.string.theme_edit_title), back = SP_THEME, fixed = { ThemeCard(pal, true, Modifier.fillMaxWidth()) {} }) {
        androidx.compose.material3.OutlinedTextField(
            value = pal.name, onValueChange = { pal = pal.copy(name = it.take(24)); Themes.setCustom(ctx, pal) }, singleLine = true,
            label = { Text(stringResource(R.string.theme_name)) }, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Chips(labels.map { stringResource(it) }, which) { which = it }
        LabeledSlider(stringResource(R.string.col_hue), h, 0f..360f, "${h.toInt()}\u00B0", cur) { h = it; fromHsv() }
        LabeledSlider(stringResource(R.string.col_sat), sat, 0f..1f, "${(sat * 100).toInt()}%", cur) { sat = it; fromHsv() }
        LabeledSlider(stringResource(R.string.col_val), v, 0f..1f, "${(v * 100).toInt()}%", cur) { v = it; fromHsv() }
        androidx.compose.material3.OutlinedTextField(
            value = hex, singleLine = true, label = { Text(stringResource(R.string.col_hex)) }, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            onValueChange = { t ->
                hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                if (hex.length == 6) {
                    val c = 0xFF000000.toInt() or hex.toInt(16)
                    val a = FloatArray(3); android.graphics.Color.colorToHSV(c, a); h = a[0]; sat = a[1]; v = a[2]; commit(c)
                }
            },
        )
        BtnCard(stringResource(R.string.theme_done), accent = true) { settingsPage = SP_THEME }
        BtnCard(stringResource(R.string.theme_undo)) { Themes.restore(ctx, origCur, origCustom); StatsWidget.update(ctx); settingsPage = SP_THEME }
    }
}

// ---------- lettertype en grootte ----------

@Composable
private fun FontPage(s: Snapshot) {
    val ctx = LocalContext.current
    val bmp = remember(s, Fonts.family, Fonts.bold, Themes.current) { StatsRenderer.render(s, 800, 340) }
    val labels = listOf(R.string.font_f0, R.string.font_f1, R.string.font_f2, R.string.font_f3, R.string.font_f4, R.string.font_f5)
    fun apply() { StatsWidget.update(ctx); if (StatusBarOverlay.enabled(ctx)) StatusBarOverlay.update(ctx, Sampler.snapshot) }
    Page(stringResource(R.string.set_font)) {
        SectionNote(stringResource(R.string.font_hint))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.memory), color = CMem, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("42%", color = Fg, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.font_sample), color = Dim, fontSize = 14.sp)
        }
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
        Text(stringResource(R.string.font_family), color = Fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Chips(labels.map { stringResource(it) }, Fonts.family) { Fonts.setFamily(ctx, it); apply() }
        LabeledSlider(stringResource(R.string.font_size), Fonts.scale.toFloat(), 80f..150f, "${Fonts.scale}%", CMem) { Fonts.setScale(ctx, (it / 5).toInt() * 5) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Switch(
                checked = Fonts.bold, onCheckedChange = { Fonts.setBold(ctx, it); apply() },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = CMem, checkedThumbColor = Bg),
            )
            Text(stringResource(R.string.font_bold), color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
        }
        BtnCard(stringResource(R.string.font_reset)) { Fonts.reset(ctx); apply() }
    }
}
