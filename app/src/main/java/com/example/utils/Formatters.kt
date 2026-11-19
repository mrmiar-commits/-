package com.example.utils

import com.google.firebase.Timestamp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val enSymbols = DecimalFormatSymbols(Locale.US)
    private val intFormat = DecimalFormat("#,##0", enSymbols)
    private val gramFormat = DecimalFormat("#,##0.##", enSymbols)
    private val usdFormat = DecimalFormat("#,##0.00", enSymbols)
    private val percentFormat = DecimalFormat("0.0#", enSymbols)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)

    fun toPersianDigits(input: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(persianDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizeAsciiDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                '٫', '،' -> sb.append('.')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseDoubleSafe(input: String): Double {
        return normalizeAsciiDigits(input).replace(",", "").trim().toDoubleOrNull() ?: 0.0
    }

    fun formatToman(amount: Double, showUnit: Boolean = true): String {
        val sign = if (amount < 0) "-" else ""
        val formatted = toPersianDigits(intFormat.format(abs(amount)))
        return if (showUnit) "$sign$formatted تومان" else "$sign$formatted"
    }

    fun formatUsd(amount: Double): String {
        return "$${usdFormat.format(amount)}"
    }

    fun formatGrams(grams: Double, showUnit: Boolean = true): String {
        val formatted = toPersianDigits(gramFormat.format(grams))
        return if (showUnit) "$formatted گرم" else formatted
    }

    fun formatPercent(pct: Double): String {
        val sign = if (pct > 0) "+" else ""
        return "$sign${toPersianDigits(percentFormat.format(pct))}%"
    }

    fun formatTimestamp(ts: Timestamp?): String {
        val date = ts?.toDate() ?: Date()
        return toPersianDigits(dateTimeFormat.format(date))
    }

    fun formatMillis(millis: Long): String {
        return toPersianDigits(dateTimeFormat.format(Date(millis)))
    }

    fun formatTimeOnly(millis: Long): String {
        return toPersianDigits(timeFormat.format(Date(millis)))
    }
}
