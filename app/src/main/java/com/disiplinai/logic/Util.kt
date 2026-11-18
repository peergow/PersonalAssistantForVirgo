package com.disiplinai.logic

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object Util {
    fun today(): String = LocalDate.now().toString()
    fun dateOffset(days: Long): String = LocalDate.now().plusDays(days).toString()
    fun nowMin(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    fun fmt(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun parseTime(s: String): Int? {
        val p = s.trim().replace('.', ':').split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun millisAt(date: String, min: Int): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(min.toLong()).toInstant().toEpochMilli()

    fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun startOfDay(offsetDays: Long): Long =
        LocalDate.now().plusDays(offsetDays).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun dayName(date: String): String = try {
        when (LocalDate.parse(date).dayOfWeek.value) {
            1 -> "Senin"; 2 -> "Selasa"; 3 -> "Rabu"; 4 -> "Kamis"
            5 -> "Jumat"; 6 -> "Sabtu"; else -> "Minggu"
        }
    } catch (e: Exception) { "" }

    fun daysUntil(date: String): Long? = try {
        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(date))
    } catch (e: Exception) { null }
}
