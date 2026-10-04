package com.ericbruggema.taskbarstats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun toast(ctx: Context, id: Int) = Toast.makeText(ctx, id, Toast.LENGTH_SHORT).show()

private fun openApp(ctx: Context, pkg: String) {
    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (i != null) ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else toast(ctx, R.string.d_no_launch)
}

internal fun openAppInfo(ctx: Context, pkg: String) =
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

internal fun openStorageSettings(ctx: Context) =
    ctx.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(label, color = Dim, fontSize = 12.sp)
        Text(value, color = Fg, fontSize = 14.sp)
    }
}

@Composable
private fun ActionButton(text: String, accent: Boolean = false, onClick: () -> Unit) = Button(
    onClick = onClick, modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(containerColor = if (accent) CMem else Bg, contentColor = if (accent) Bg else Fg),
) { Text(text) }

private fun userName(uid: String): String {
    val n = uid.toIntOrNull() ?: return uid
    return when {
        n == 0 -> "root"; n == 1000 -> "system"; n == 2000 -> "shell"
        n >= 10000 -> "u${n / 100000}_a${n % 100000 - 10000}"
        else -> uid
    }
}

/** Details en acties voor één proces: openen, naar voorgrond, app-info, geforceerd stoppen, proces beëindigen. */
@Composable
internal fun ProcessDialog(p: AppData.Proc, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val detail by produceState<AppData.Detail?>(null, p.pid) { value = withContext(Dispatchers.IO) { AppData.processDetail(p.pid) } }
    var confirm by remember { mutableStateOf(0) }       // 1 = geforceerd stoppen, 2 = proces beëindigen
    val d = detail
    val v = d?.values.orEmpty()
    AlertDialog(
        onDismissRequest = onClose, containerColor = Card, titleContentColor = Fg,
        title = { Column { Text(p.label, fontWeight = FontWeight.Bold); Text("${p.name}  ·  PID ${p.pid}", color = Dim, fontSize = 13.sp) } },
        text = {
            Column {
              Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                if (d == null) Text(stringResource(R.string.d_loading), color = Dim, fontSize = 14.sp)
                else {
                    Field(stringResource(R.string.d_cpu_mem), String.format("%.1f%%  ·  %s", p.cpu, Fmt.bytes(p.rssKb * 1024)))
                    Field(stringResource(R.string.d_cmdline), v["cmdline"].orEmpty())
                    Field(stringResource(R.string.d_started_by), listOfNotNull(v["parent"]?.takeIf { it.isNotBlank() }, v["ppid"]?.let { "PID $it" }).joinToString("  ·  "))
                    Field(stringResource(R.string.d_user), userName(v["uid"].orEmpty()))
                    Field(stringResource(R.string.d_age), v["age"]?.toLongOrNull()?.let { uptimeText(it * 1000) }.orEmpty())
                    Field(stringResource(R.string.d_state), listOf(v["state"], v["threads"]?.let { "$it threads" }).filterNotNull().joinToString("  ·  "))
                    Field(stringResource(R.string.d_memory), listOfNotNull(v["rss"]?.let { "RSS $it" }, v["vm"]?.let { "virtual $it" }).joinToString("  ·  "))
                    val host = AppData.hostingNames(v["hosting"].orEmpty()).map { stringResource(hostRes(it)) }
                    Field(stringResource(R.string.d_hosting), host.joinToString(", "))
                    if (d.parts.isNotEmpty()) {
                        Text(stringResource(R.string.d_parts), color = Dim, fontSize = 12.sp)
                        for (part in d.parts) Text(part, color = Fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    if (v.isEmpty()) Text(stringResource(R.string.d_none), color = Dim, fontSize = 14.sp)
                }
              }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (confirm != 0) {
                        Text(if (confirm == 1) stringResource(R.string.d_confirm_force, p.label) else stringResource(R.string.d_confirm_kill, p.pid), color = CTemp, fontSize = 14.sp)
                        ActionButton(stringResource(R.string.d_yes), accent = true) {
                            val kind = confirm; confirm = 0
                            scope.launch {
                                val rc = withContext(Dispatchers.IO) { if (kind == 1) ShizukuCpu.runAction(1, p.pkg) else ShizukuCpu.runAction(2, p.pid.toString()) }
                                toast(ctx, if (rc == 0) R.string.d_done else R.string.d_failed); if (rc == 0) onClose()
                            }
                        }
                        ActionButton(stringResource(R.string.d_cancel)) { confirm = 0 }
                    } else {
                        if (p.pkg != null) {
                            ActionButton(stringResource(R.string.d_open_app), accent = true) { openApp(ctx, p.pkg) }
                            ActionButton(stringResource(R.string.d_app_info)) { openAppInfo(ctx, p.pkg) }
                            ActionButton(stringResource(R.string.d_force_stop)) { confirm = 1 }
                        }
                        ActionButton(stringResource(R.string.d_end_process)) { confirm = 2 }
                        val cmd = v["cmdline"].orEmpty().ifBlank { p.name }
                        ActionButton(stringResource(R.string.d_copy_cmd)) {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("cmdline", cmd)); toast(ctx, R.string.d_copied)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.d_close), color = CMem) } },
    )
}

private fun hostRes(name: String) = when (name) {
    "system" -> R.string.host_system; "persistent" -> R.string.host_persistent; "backup" -> R.string.host_backup
    "instrumentation" -> R.string.host_instrumentation; "activity" -> R.string.host_activity; "receiver" -> R.string.host_receiver
    "provider" -> R.string.host_provider; "started service" -> R.string.host_service; "foreground service" -> R.string.host_fgservice
    else -> R.string.host_bound
}

/** Opslag van één app: onderdelen, cache en de routes om de cache te wissen. */
@Composable
internal fun StorageDialog(s: AppData.Sized, onClose: () -> Unit, onCleared: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose, containerColor = Card, titleContentColor = Fg,
        title = { Column { Text(s.label, fontWeight = FontWeight.Bold); Text(s.pkg, color = Dim, fontSize = 13.sp) } },
        text = {
            Column {
                Field(stringResource(R.string.stor_total), Fmt.bytes(s.bytes))
                Field(stringResource(R.string.stor_app), Fmt.bytes(s.app))
                Field(stringResource(R.string.stor_data), Fmt.bytes(s.data))
                Field(stringResource(R.string.stor_cache), Fmt.bytes(s.cache))
                Text(stringResource(R.string.stor_cache_hint), color = Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(stringResource(R.string.stor_open_info), accent = true) { openAppInfo(ctx, s.pkg) }
                    if (ShizukuCpu.state == ShizukuCpu.State.ACTIVE) ActionButton(stringResource(R.string.stor_clear_all)) {
                        scope.launch {
                            val rc = withContext(Dispatchers.IO) { ShizukuCpu.runAction(3, null) }
                            toast(ctx, if (rc == 0) R.string.stor_cleared else R.string.d_failed); if (rc == 0) { onCleared(); onClose() }
                        }
                    }
                    ActionButton(stringResource(R.string.stor_open_settings)) { openStorageSettings(ctx) }
                    ActionButton(stringResource(R.string.d_open_app)) { openApp(ctx, s.pkg) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.d_close), color = CMem) } },
    )
}
