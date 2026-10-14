package com.ioi.youtube.domain.util

object Formatters {

    fun formatCount(count: Long): String = when {
        count < 0 -> "0"
        count < 1_000 -> "$count"
        count < 1_000_000 -> "%.1fK".format(count / 1_000.0)
        count < 1_000_000_000 -> "%.1fM".format(count / 1_000_000.0)
        else -> "%.1fB".format(count / 1_000_000_000.0)
    }

    fun formatViews(count: Long): String =
        if (count <= 0) "No views" else "${formatCount(count)} views"

    fun formatDuration(totalSeconds: Long): String {
        val safeSeconds = if (totalSeconds < 0) 0 else totalSeconds
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val seconds = safeSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
