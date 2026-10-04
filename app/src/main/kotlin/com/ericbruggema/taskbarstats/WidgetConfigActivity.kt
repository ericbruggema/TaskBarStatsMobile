package com.ericbruggema.taskbarstats

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Instelscherm van één widget: welke cellen en hoe doorzichtig (wordt door de launcher geopend bij het plaatsen en bij "opnieuw instellen"). */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        Themes.load(this)
        val start = WidgetOptions.load(this, widgetId)
        val kind = WidgetKind.of(this, widgetId)
        setContent {
            var cells by remember { mutableStateOf(start.cells) }
            var opacity by remember { mutableIntStateOf(start.opacity) }
            var graphs by remember { mutableStateOf(start.graphs) }
            val opts = WidgetOptions(cells, opacity, graphs)
            val s = Sampler.snapshot
            val bmp = remember(s, cells, opacity, graphs) { StatsRenderer.render(s, 800, (800 * kind.aspect).toInt().coerceAtLeast(130), opts) }
            Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.widget_config_title), color = Fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                // lichte achtergrond onder het voorbeeld, zodat de doorzichtigheid zichtbaar is
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(8.dp)) {
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
                for (key in WidgetOptions.ALL_CELLS) Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = key in cells,
                        onCheckedChange = { on -> val n = if (on) cells + key else cells - key; if (n.isNotEmpty()) cells = n },
                        colors = SwitchDefaults.colors(checkedTrackColor = CMem, checkedThumbColor = Bg),
                    )
                    Text(cellName(key), color = Fg, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = graphs, onCheckedChange = { graphs = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = CMem, checkedThumbColor = Bg),
                    )
                    Text(stringResource(R.string.widget_graphs), color = Fg, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
                Text(stringResource(R.string.widget_opacity) + " " + opacity + "%", color = Dim, fontSize = 14.sp)
                Slider(
                    value = opacity.toFloat(), onValueChange = { opacity = it.toInt() }, valueRange = 20f..100f,
                    colors = SliderDefaults.colors(thumbColor = CMem, activeTrackColor = CMem),
                )
                Button(
                    onClick = { save(opts) },
                    colors = ButtonDefaults.buttonColors(containerColor = CMem, contentColor = Bg),
                ) { Text(stringResource(R.string.widget_save)) }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun cellName(key: String) = stringResource(when (key) {
        "mem" -> R.string.memory; "net" -> R.string.network; "cpu" -> R.string.cpu
        "disk" -> R.string.storage; "data" -> R.string.cell_data; "temp" -> R.string.temperature; "conn" -> R.string.tile_connection; "uptime" -> R.string.tile_uptime; else -> R.string.ping
    })

    private fun save(opts: WidgetOptions) {
        opts.save(this, widgetId)
        StatsWidget.update(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    override fun onStart() { super.onStart(); Sampler.acquire(this) }
    override fun onStop() { Sampler.release(); super.onStop() }
}
