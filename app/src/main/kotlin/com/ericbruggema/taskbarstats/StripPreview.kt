package com.ericbruggema.taskbarstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Voorbeeld van de statusbalk waarin je de plek van de strook kiest: tik links (naast de klok), in het midden (om de
 * camera) of rechts (voor de iconen). De camera, de vrije ruimte en de strook zijn getekend op schaal van dit toestel.
 * Reageert direct op de schuifregelaars eronder.
 */
@Composable
fun StripPreview(pos: Int, camera: Boolean, camDp: Float, marginDp: Float, shiftDp: Float, nudgeDp: Float, onPos: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val screenDp = LocalContext.current.resources.displayMetrics.let { it.widthPixels / it.density }
    val acc = CMem; val dim = Dim
    Canvas(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures { o -> onPos(if (o.x < size.width / 3f) StatusBarOverlay.LEFT else if (o.x > size.width * 2f / 3f) StatusBarOverlay.RIGHT else StatusBarOverlay.CENTER) }
            },
    ) {
        val s = size.width / screenDp          // pixels per dp van het toestel
        val h = size.height
        drawRect(Color(0xFF0B1118))
        // drie tikzones (links, midden, rechts); de gekozen is gemarkeerd
        val zone = size.width / 3f
        for (i in 0..2) {
            val sel = (i == 0 && pos == StatusBarOverlay.LEFT) || (i == 1 && pos == StatusBarOverlay.CENTER) || (i == 2 && pos == StatusBarOverlay.RIGHT)
            drawRect(if (sel) acc.copy(alpha = 0.14f) else Color.Transparent, Offset(i * zone, 0f), Size(zone, h))
            if (i > 0) drawLine(dim.copy(alpha = 0.25f), Offset(i * zone, h * 0.15f), Offset(i * zone, h * 0.85f), 1.dp.toPx())
        }
        val barH = h * 0.62f; val y0 = (h - barH) / 2f
        // klok + meldingsiconen links, systeemiconen rechts
        drawText(measurer, "12:30", Offset(10.dp.toPx(), y0 + barH * 0.18f), TextStyle(color = Color.White, fontSize = 12.sp))
        for (k in 0..1) drawCircle(Color.White.copy(alpha = 0.55f), 4.dp.toPx(), Offset(58.dp.toPx() + k * 14.dp.toPx(), h / 2f))
        for (k in 0..2) drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(size.width - (14 + k * 16).dp.toPx(), h / 2f - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), CornerRadius(3.dp.toPx()))
        // camera met de extra vrije ruimte
        var camL = 0f; var camR = 0f
        if (camera) {
            val cx = size.width / 2f + shiftDp * s; val r = camDp * s / 2f; val m = marginDp * s
            camL = cx - r - m; camR = cx + r + m
            drawRoundRect(Color(0xFFFF6E5A).copy(alpha = 0.18f), Offset(camL, 0f), Size(camR - camL, h), CornerRadius(8.dp.toPx()))
            drawCircle(Color.Black, r.coerceAtLeast(3.dp.toPx()), Offset(cx, h * 0.5f))
            drawCircle(Color.White.copy(alpha = 0.35f), r.coerceAtLeast(3.dp.toPx()), Offset(cx, h * 0.5f), style = Stroke(1.dp.toPx()))
        }
        // de strook zelf: gekozen plek vol, de andere plekken als schim
        fun pill(x: Float, w: Float, a: Float) { if (w > 8.dp.toPx()) drawStrip(x, y0 + barH * 0.08f, w, barH * 0.84f, a) }
        val gap = 6.dp * 1f
        val leftStart = 112 * s + nudgeDp * s; val rightEnd = size.width - 104 * s + nudgeDp * s
        val total = 150 * s
        for (which in 0..2) {
            val a = if ((which == 0 && pos == StatusBarOverlay.LEFT) || (which == 1 && pos == StatusBarOverlay.CENTER) || (which == 2 && pos == StatusBarOverlay.RIGHT)) 1f else 0.18f
            when (which) {
                0 -> pill(leftStart, if (camera && camL > 0 && leftStart < camR) minOf(total, camL - gap.toPx() - leftStart) else total, a)
                2 -> { val w = if (camera && rightEnd > camL) minOf(total, rightEnd - camR - gap.toPx()) else total; pill(rightEnd - w, w, a) }
                else -> if (camera) {
                    val lw = (camL - gap.toPx() - 70 * s).coerceAtLeast(0f).coerceAtMost(total * 0.45f)
                    pill(camL - gap.toPx() - lw, lw, a)
                    pill(camR + gap.toPx(), (total * 0.55f).coerceAtMost(size.width - camR - 100 * s - gap.toPx()), a)
                } else pill(size.width / 2f - total / 2f + nudgeDp * s, total, a)
            }
        }
    }
}

private fun DrawScope.drawStrip(x: Float, y: Float, w: Float, h: Float, alpha: Float) {
    drawRoundRect(Color.Black.copy(alpha = 0.85f * alpha + 0.1f), Offset(x, y), Size(w, h), CornerRadius(h * 0.25f))
    drawRoundRect(Color.White.copy(alpha = 0.22f * alpha), Offset(x, y), Size(w, h), CornerRadius(h * 0.25f), style = Stroke(1.dp.toPx()))
    // drie gekleurde cijfers als plaatsvervanger voor de waarden
    val colors = listOf(Color(0xFF7BC67E), Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFFBA68C8))
    val n = (w / (22.dp.toPx())).toInt().coerceIn(1, 4); val cell = (w - 8.dp.toPx()) / n
    for (i in 0 until n) {
        val cx = x + 4.dp.toPx() + i * cell + 2.dp.toPx()
        drawRoundRect(colors[i % 4].copy(alpha = alpha), Offset(cx, y + h * 0.22f), Size((cell - 6.dp.toPx()).coerceAtLeast(3.dp.toPx()), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawRoundRect(Color.White.copy(alpha = 0.8f * alpha), Offset(cx, y + h * 0.58f), Size((cell - 6.dp.toPx()).coerceAtLeast(3.dp.toPx()), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

/** Schuifregelaar die een geheel getal in de instellingen bewaart en de strook meteen bijwerkt. */
@Composable
fun PrefSlider(label: String, key: String, default: Int, range: IntRange, unit: String = "dp", onChange: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    var v by remember { mutableFloatStateOf(StatusBarOverlay.prefs(ctx).getInt(key, default).toFloat()) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Fg, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text("${v.toInt()} $unit", color = Dim, fontSize = 14.sp)
        }
        Slider(
            value = v, onValueChange = { v = it; onChange(it.toInt()); StatusBarOverlay.prefs(ctx).edit().putInt(key, it.toInt()).apply(); StatusBarOverlay.refresh(ctx) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = CMem, activeTrackColor = CMem, inactiveTrackColor = Bg),
        )
    }
}
