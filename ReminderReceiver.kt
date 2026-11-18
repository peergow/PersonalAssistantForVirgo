package com.disiplinai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.disiplinai.R
import com.disiplinai.ai.Agent
import com.disiplinai.data.Store
import com.disiplinai.logic.Discipline
import com.disiplinai.logic.Habits
import com.disiplinai.logic.Perms
import com.disiplinai.logic.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val store = Store.get(ctx)
        val id = i.getStringExtra("id")
        val task = id?.let { tid -> store.tasks.value.firstOrNull { it.id == tid } }

        when (i.action) {
            Reminders.ACT_TASK -> {
                if (task == null || task.done) return
                Notifier.show(
                    ctx, task.id.hashCode(), "Sekarang: ${task.title}",
                    "${Util.fmt(task.timeMin)} · ${task.durationMin} menit. Kerjakan sekarang, jangan buka HP untuk hal lain.",
                    listOf(
                        NotificationCompat.Action.Builder(
                            R.drawable.ic_notif, "Selesai", Reminders.doneIntent(ctx, task.id)
                        ).build()
                    )
                )
                Reminders.scheduleNag(ctx, task)
            }

            Reminders.ACT_NAG -> {
                if (task == null || task.done) return
                Notifier.show(
                    ctx, task.id.hashCode() + 1, "Sudah 10 menit! ${task.title}",
                    "Belum ditandai selesai. Mulai sekarang, atau akui gagal saat check-in."
                )
            }

            Reminders.ACT_DONE -> {
                if (task == null) return
                store.setDone(task.id, true)
                Reminders.cancelTask(ctx, task)
                NotificationManagerCompat.from(ctx).cancel(task.id.hashCode())
                NotificationManagerCompat.from(ctx).cancel(task.id.hashCode() + 1)
            }

            Reminders.ACT_CHECKIN -> {
                val t = store.tasksFor(Util.today())
                val msg = if (t.isEmpty()) "Hari ini tidak ada rencana. Itu sudah kegagalan kecil. Buat rencana besok sekarang."
                else "${t.count { it.done }} dari ${t.size} tugas selesai. Buka app, tandai dengan jujur, lalu minta evaluasi."
                Notifier.show(ctx, 9001, "Check-in malam", msg)
                if (!Perms.accessibilityEnabled(ctx)) {
                    Notifier.show(ctx, 9003, "Pemblokir MATI", "Layanan aksesibilitas Disiplin AI nonaktif. Aktifkan lagi di Setelan.")
                }
                Reminders.scheduleAll(ctx)
            }

            Reminders.ACT_MORNING -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Discipline.finalizeOverdue(ctx)
                        Habits.materialize(ctx)
                        Reminders.scheduleAll(ctx)
                        val today = Util.today()
                        if (store.tasksFor(today).none { !Habits.isHabitTask(it) } && store.config.value.apiKey.isNotBlank()) {
                            try {
                                val tasks = Agent.generatePlan(ctx, today, "Rencana otomatis pagi hari.")
                                store.replaceUndone(today, tasks)
                                Habits.materialize(ctx)
                                Reminders.scheduleAll(ctx)
                                Notifier.show(ctx, 9002, "Rencana hari ini siap", "${tasks.size} tugas. Buka app dan mulai yang pertama.")
                            } catch (e: Exception) {
                                Notifier.show(ctx, 9002, "Bangun. Saatnya mulai", "Rencana otomatis gagal (${e.message?.take(80)}). Buat rencana manual di app.")
                            }
                        } else {
                            Notifier.show(ctx, 9002, "Bangun. Saatnya mulai", "Buka rencana hari ini dan kerjakan yang pertama.")
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
