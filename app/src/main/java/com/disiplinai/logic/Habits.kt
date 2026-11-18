package com.disiplinai.logic

import android.content.Context
import com.disiplinai.data.PlanTask
import com.disiplinai.data.Store
import java.time.LocalDate

object Habits {
    const val PREFIX = "h_"

    /** Pastikan kebiasaan yang jatuh pada [date] muncul sebagai tugas. Aman dipanggil berulang. */
    fun materialize(ctx: Context, date: String = Util.today()) {
        val s = Store.get(ctx)
        val dow = LocalDate.parse(date).dayOfWeek.value
        val existing = s.tasksFor(date).map { it.id }.toSet()
        s.habits.value.filter { dow in it.days }.forEach { h ->
            val id = "$PREFIX${h.id}_$date"
            if (id !in existing) {
                s.addTask(
                    PlanTask(id, date, h.timeMin, h.durationMin, h.title, h.category, false, System.currentTimeMillis())
                )
            }
        }
    }

    fun isHabitTask(t: PlanTask) = t.id.startsWith(PREFIX)
}
