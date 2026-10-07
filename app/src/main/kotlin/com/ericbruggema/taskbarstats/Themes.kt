package com.ericbruggema.taskbarstats

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Vier kleuren, zoals in een Windows-thema van TaskbarStats (TextColor, BackgroundColor, AccentColor, WarnColor). */
data class Palette(val name: String, val text: Int, val bg: Int, val accent: Int, val warn: Int)

/**
 * Dezelfde meegeleverde thema's als de Windows-versie (alleen de kleuren) en import van een Windows-themabestand
 * (.json uit %AppData%\TaskbarStats\themes of een export uit Instellingen > Thema's).
 */
object Themes {
    private fun p(name: String, text: String, bg: String, accent: String, warn: String) =
        Palette(name, Color.parseColor(text), Color.parseColor(bg), Color.parseColor(accent), Color.parseColor(warn))

    val all = listOf(
        p("TaskbarStats", "#E8EEF2", "#101820", "#4FC3F7", "#FFB74D"),
        p("Default", "#FFFFFF", "#141414", "#0A84FF", "#FF9500"),
        p("Dark", "#E0E0E0", "#000000", "#0A84FF", "#FF9F0A"),
        p("Light", "#1C1C1E", "#F2F2F7", "#0A64D6", "#E07B00"),
        p("Black and white", "#FFFFFF", "#000000", "#7A7A7A", "#C8C8C8"),
        p("Love", "#FFE4EE", "#2B0A1A", "#FF4D8D", "#FFB3C7"),
        p("CGA", "#FFFFFF", "#000000", "#55FFFF", "#FFFF55"),
        p("Matrix", "#00FF41", "#000A00", "#00CC33", "#B6FF00"),
        p("Amber", "#FFB000", "#0A0500", "#FF8C00", "#FFD060"),
        p("Game Boy", "#9BBC0F", "#0F380F", "#8BAC0F", "#CADC9F"),
        p("Dracula", "#F8F8F2", "#282A36", "#BD93F9", "#FFB86C"),
        p("Ocean", "#E6F7FF", "#06202B", "#00B4D8", "#FFD166"),
        p("Sunset", "#FFF1E0", "#1A0B2E", "#FF7E5F", "#FEB47B"),
        p("Forest", "#E8F5E9", "#10201A", "#66BB6A", "#FFCA28"),
        p("Neon", "#E6FBFF", "#05060A", "#00E5FF", "#FFD60A"),
    )

    /** Geheim thema: pas zichtbaar nadat je het ontgrendeld hebt (zie Instellingen). */
    val secret = p("Synthwave", "#F8E8FF", "#1A0033", "#FF2BD6", "#00F0FF")

    var secretOn by mutableStateOf(false)
        private set

    fun unlockSecret(ctx: Context) { secretOn = true; StatusBarOverlay.prefs(ctx).edit().putBoolean("secret_theme", true).apply() }

    var current by mutableStateOf(all[0])
        private set

    /** Door de gebruiker geïmporteerd thema (blijft in de lijst staan zolang het gekozen is). */
    var custom by mutableStateOf<Palette?>(null)
        private set

    fun load(ctx: Context) {
        val pr = StatusBarOverlay.prefs(ctx)
        secretOn = pr.getBoolean("secret_theme", false)
        val name = pr.getString("theme", null) ?: return
        if (name == "custom") {
            val c = pr.getString("theme_custom", null)?.split("|") ?: return
            if (c.size == 5) { custom = Palette(c[0], c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].toInt()); current = custom!! }
        } else (all + secret).firstOrNull { it.name == name }?.let { current = it }
    }

    fun select(ctx: Context, pal: Palette) {
        current = pal
        val e = StatusBarOverlay.prefs(ctx).edit()
        if (pal === custom) {
            e.putString("theme", "custom")
            e.putString("theme_custom", "${pal.name}|${pal.text}|${pal.bg}|${pal.accent}|${pal.warn}")
        } else e.putString("theme", pal.name)
        e.apply()
    }

    /** Zet de eerdere toestand terug (na ongedaan maken in de editor). */
    fun restore(ctx: Context, cur: Palette, cust: Palette?) { custom = cust; select(ctx, cur) }

    fun setCustom(ctx: Context, pal: Palette) { custom = pal; select(ctx, pal) }

    /** Leest een Windows-thema (JSON); null als er geen bruikbare kleuren in staan. */
    fun parse(json: String): Palette? = try {
        val o = JSONObject(json)
        fun c(key: String) = Color.parseColor(o.getString(key))
        Palette(o.optString("Name", "Imported").ifBlank { "Imported" }, c("TextColor"), c("BackgroundColor"), c("AccentColor"), c("WarnColor"))
    } catch (_: Exception) { null }

    /** Mengt twee kleuren (0 = a, 1 = b). */
    fun mix(a: Int, b: Int, t: Float) = androidx.core.graphics.ColorUtils.blendARGB(a, b, t)
}
