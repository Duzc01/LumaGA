package com.bugenzhao.mnga.util

import android.content.Context
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Date formatting, ported from `Utilities/DateFormatters.swift`. */
object DateFormatters {

    /** "<x minutes ago"-style relative time; "Just now" under 60 s. */
    fun timeAgo(context: Context, date: Date, now: Date = Date()): String {
        val diff = (now.time - date.time) / 1000
        if (diff < 60) return L.str(context, "Just now")
        if (diff < 0) return L.str(context, "Just now")
        return DateUtils.getRelativeTimeSpanString(
            date.time,
            now.time,
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    /** Locale-aware long date + short time, e.g. "2026年8月17日 上午10:24". */
    fun detailed(context: Context, date: Date): String {
        val dateFormat = android.text.format.DateFormat.getLongDateFormat(context)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }

    /** ISO `yyyy-MM-dd` in the current timezone. */
    fun currentDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** Automatic strategy: relative for recent, detailed when older than 30 days. */
    fun automatic(context: Context, date: Date, now: Date = Date()): String {
        val thirtyDays = 30L * 24 * 3600 * 1000
        return if (now.time - date.time > thirtyDays) detailed(context, date)
        else timeAgo(context, date, now)
    }
}
