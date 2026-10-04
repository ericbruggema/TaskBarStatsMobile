package com.ericbruggema.taskbarstats

import java.util.Locale

object Fmt {
    fun bytes(b: Long): String {
        val gb = b / 1_073_741_824.0
        return if (gb >= 1) String.format(Locale.getDefault(), "%.1f GB", gb)
        else String.format(Locale.getDefault(), "%.0f MB", b / 1_048_576.0)
    }

    /** Snelheid zoals het Windows-widget: KB/s of MB/s. */
    fun rate(bps: Float): String = when {
        bps >= 1_048_576f -> String.format(Locale.getDefault(), "%.1f MB/s", bps / 1_048_576f)
        bps >= 102_400f -> String.format(Locale.getDefault(), "%.0f KB/s", bps / 1024f)
        bps >= 1024f -> String.format(Locale.getDefault(), "%.1f KB/s", bps / 1024f)
        else -> String.format(Locale.getDefault(), "%.0f B/s", bps)
    }

    /** Superkort voor de statusbalk: "850B", "9.4K", "397K", "2.0M", "112M". Nooit "0K" bij echt verkeer. */
    fun rateShort(bps: Float): String = when {
        bps >= 104_857_600f -> String.format(Locale.getDefault(), "%.0fM", bps / 1_048_576f)
        bps >= 1_048_576f -> String.format(Locale.getDefault(), "%.1fM", bps / 1_048_576f)
        bps >= 102_400f -> String.format(Locale.getDefault(), "%.0fK", bps / 1024f)
        bps >= 1024f -> String.format(Locale.getDefault(), "%.1fK", bps / 1024f)
        else -> String.format(Locale.getDefault(), "%.0fB", bps)
    }

    fun percent(v: Float) = String.format(Locale.getDefault(), "%.0f%%", v)
}
