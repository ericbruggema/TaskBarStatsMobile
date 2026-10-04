package com.ericbruggema.taskbarstats

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Moet het eerste-start-scherm getoond worden? Gezet in [MainActivity]; ook te openen via de Widget-tab. */
internal var showSetup by mutableStateOf(false)

/** Meerkeuze van de onderdelen die in de statusbalk komen; wordt direct bewaard. */
@Composable
internal fun ItemsPicker(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (id in StatusItems.ALL) {
            val on = id in selected
            val f = StatusItems.face(id, Snapshot())
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) CMem else Card)
                    .clickable { onChange(if (on) selected - id else selected + id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (on) "✓" else "+", color = if (on) Bg else Dim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(StatusItems.nameRes(id)), color = if (on) Bg else Fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SwitchCard(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = CMem, checkedThumbColor = Bg))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(hint, color = Dim, fontSize = 13.sp)
            }
        }
        if (on) extra()
    }
}

/** Eerste start: kies welke onderdelen je wilt zien en waar, dan start de monitor meteen. */
@Composable
internal fun SetupScreen() {
    val ctx = LocalContext.current
    val act = ctx as ComponentActivity
    var items by remember { mutableStateOf(StatusItems.selected(ctx).toSet()) }
    var icons by remember { mutableStateOf(true) }
    var overlay by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(StatusBarOverlay.position(ctx)) }
    @Suppress("UNUSED_EXPRESSION") resumeTick
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.setup_title), color = Fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.setup_intro), color = Dim, fontSize = 14.sp)

        Text(stringResource(R.string.setup_items), color = Fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(R.string.setup_items_hint), color = Dim, fontSize = 13.sp)
        ItemsPicker(items) { items = it }

        Text(stringResource(R.string.setup_where), color = Fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        SwitchCard(stringResource(R.string.setup_icons), stringResource(R.string.setup_icons_hint), icons, { icons = it })
        SwitchCard(stringResource(R.string.setup_overlay), stringResource(R.string.setup_overlay_hint), overlay, {
            overlay = it
            if (it && !StatusBarOverlay.canDraw(ctx)) ctx.startActivity(StatusBarOverlay.permissionIntent(ctx))
        }) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.pos_left to StatusBarOverlay.LEFT, R.string.pos_right to StatusBarOverlay.RIGHT).forEach { (id, i) ->
                        val sel = i == pos
                        Text(
                            stringResource(id), color = if (sel) Bg else Fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) CMem else Bg)
                                .clickable { pos = i }.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                if (!StatusBarOverlay.canDraw(ctx)) {
                    Text(stringResource(R.string.setup_overlay_perm), color = CTemp, fontSize = 13.sp)
                    Button(
                        onClick = { ctx.startActivity(StatusBarOverlay.permissionIntent(ctx)) },
                        colors = ButtonDefaults.buttonColors(containerColor = CMem, contentColor = Bg),
                    ) { Text(stringResource(R.string.btn_open_settings)) }
                }
            }
        }

        Button(
            enabled = items.isNotEmpty() && (icons || overlay),
            onClick = {
                StatusItems.setSelected(ctx, items)
                StatusBarOverlay.prefs(ctx).edit()
                    .putBoolean("icons", icons).putBoolean("overlay", overlay && StatusBarOverlay.canDraw(ctx))
                    .putInt("overlay_pos", pos).putBoolean("setup_done", true).apply()
                if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                MonitorService.start(ctx)
                showSetup = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = CMem, contentColor = Bg),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.setup_start)) }
        Text(
            stringResource(R.string.setup_skip), color = Dim, fontSize = 14.sp,
            modifier = Modifier.clickable {
                StatusBarOverlay.prefs(ctx).edit().putBoolean("setup_done", true).apply(); showSetup = false
            }.padding(vertical = 8.dp),
        )
    }
}
