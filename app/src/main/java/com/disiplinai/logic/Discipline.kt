package com.disiplinai.logic

import android.content.Context
import com.disiplinai.data.DayRecord
import com.disiplinai.data.Store
import java.time.LocalDate

/** Menutup buku hari-hari yang sudah lewat: hitung streak dan penalti. */
object Discipline {
    fun finalizeOverdue(ctx: Context) {
        val s = Store.get(ctx)
        val today = LocalDate.now()
        val last = s.progress.value.lastFinalized
        if (last.isBlank()) {
            s.updateProgress { it.copy(lastFinalized = today.minusDays(1).toString()) }
            return
        }
        var d = LocalDate.parse(last).plusDays(1)
        val floor = today.minusDays(30)
        if (d.isBefore(floor)) d = floor
        while (d.isBefore(today)) {
            finalizeDay(s, d.toString())
            d = d.plusDays(1)
        }
    }

    private fun finalizeDay(s: Store, date: String) {
        val tasks = s.tasksFor(date)
        val cfg = s.config.value
        if (tasks.isEmpty()) {
            s.addRecord(DayRecord(date, 0, 0, success = false, noPlan = true))
            s.updateProgress { it.copy(lastFinalized = date) }
            return
        }
        val done = tasks.count { it.done }
        val ok = done * 100 >= cfg.successThresholdPct * tasks.size
        val light = s.isLight(date)
        if (!ok && light) {
            // Hari ringan: gagal tidak memutus streak dan tidak menambah penalti.
            s.addRecord(DayRecord(date, tasks.size, done, success = false, light = true))
            s.updateProgress { it.copy(lastFinalized = date) }
            return
        }
        s.addRecord(DayRecord(date, tasks.size, done, ok, light = light))
        s.updateProgress {
            if (ok) it.copy(
                streak = it.streak + 1,
                best = maxOf(it.best, it.streak + 1),
                strikes = maxOf(0, it.strikes - 1),
                lastFinalized = date
            ) else it.copy(streak = 0, strikes = it.strikes + 1, lastFinalized = date)
        }
    }
}
