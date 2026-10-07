package com.ericbruggema.taskbarstats

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

/**
 * Lettertype, tekstgrootte en vet: gelden voor de app zelf, en (lettertype en vet) ook voor de widgets, de melding en de
 * tekststrook in de statusbalk. De grootte verandert alleen de tekst in de app, want de widgets hebben een vaste indeling.
 */
object Fonts {
    /** Android-systeemfamilies; ze bestaan op elk toestel (de fabrikant kan ze een eigen vorm geven). */
    val names = listOf("sans-serif", "sans-serif-condensed", "serif", "monospace", "casual", "cursive")

    var family by mutableIntStateOf(0)
        private set
    /** Procent van de normale tekstgrootte in de app. */
    var scale by mutableIntStateOf(100)
        private set
    var bold by mutableStateOf(false)
        private set

    fun load(ctx: Context) {
        val p = StatusBarOverlay.prefs(ctx)
        family = p.getInt("font_family", 0).coerceIn(0, names.lastIndex)
        scale = p.getInt("font_scale", 100).coerceIn(80, 150)
        bold = p.getBoolean("font_bold", false)
    }

    fun setFamily(ctx: Context, i: Int) { family = i; StatusBarOverlay.prefs(ctx).edit().putInt("font_family", i).apply() }
    fun setScale(ctx: Context, pct: Int) { scale = pct; StatusBarOverlay.prefs(ctx).edit().putInt("font_scale", pct).apply() }
    fun setBold(ctx: Context, on: Boolean) { bold = on; StatusBarOverlay.prefs(ctx).edit().putBoolean("font_bold", on).apply() }
    fun reset(ctx: Context) { setFamily(ctx, 0); setScale(ctx, 100); setBold(ctx, false) }

    /** Voor de getekende tekst (widget, melding, strook): [heavy] = de vetgedrukte labels en waarden. */
    fun typeface(heavy: Boolean): Typeface = Typeface.create(names[family], if (heavy || bold) Typeface.BOLD else Typeface.NORMAL)

    private fun composeFamily(): FontFamily = if (family == 0) FontFamily.Default else FontFamily(Typeface.create(names[family], Typeface.NORMAL))

    /** Zet lettertype, grootte en vet voor alle tekst in de app. */
    @Composable
    fun Scope(content: @Composable () -> Unit) {
        val d = LocalDensity.current
        val dens = remember(d, scale) { Density(d.density, d.fontScale * scale / 100f) }
        CompositionLocalProvider(LocalDensity provides dens) {
            ProvideTextStyle(TextStyle(fontFamily = composeFamily(), fontWeight = if (bold) FontWeight.Bold else null)) { content() }
        }
    }
}
