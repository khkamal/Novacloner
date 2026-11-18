package com.example.utils

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {
    fun formatTimestamp(timestampMillis: Long): String {
        val formatter = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault()
        )
        return formatter.format(Date(timestampMillis))
    }

    fun formatShortDate(timestampMillis: Long): String {
        val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "Restricted by Android"
        if (bytes < 1024L) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(4)
        val unit = "KMGT"[exp - 1]
        val value = bytes / 1024.0.pow(exp.toDouble())
        return String.format(Locale.getDefault(), "%.1f %sB", value, unit)
    }
}
