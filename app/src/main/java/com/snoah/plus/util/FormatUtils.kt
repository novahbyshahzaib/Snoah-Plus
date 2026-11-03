package com.snoah.plus.util

import java.util.Locale

object FormatUtils {
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    fun formatCount(count: Int, singular: String, plural: String): String {
        return "$count ${if (count == 1) singular else plural}"
    }

    fun initials(text: String): String {
        val words = text.trim().split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return "♪"
        val first = words[0].first().uppercaseChar()
        val second = if (words.size > 1) words[1].first().uppercaseChar() else null
        return if (second != null) "$first$second" else "$first"
    }
}
