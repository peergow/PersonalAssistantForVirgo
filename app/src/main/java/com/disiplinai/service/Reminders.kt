package com.disiplinai.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.disiplinai.data.PlanTask
import com.disiplinai.data.Store
import com.disiplinai.logic.Util

object Reminders {
    const val ACT_TASK = "com.disiplinai.TASK"
    const val ACT_NAG = "com.disiplinai.NAG"
    const val ACT_DONE = "com.disiplinai.DONE"
    const val ACT_CHECKIN = "com.disiplinai.CHECKIN"
    const val ACT_MORNING = "com.disiplinai.MORNING"
    private const val CODE_CHECKIN = 9001
    private const val CODE_MORNING = 9002

    fun scheduleAll(ctx: Context) {
        val store = Store.get(ctx)
        store.tasksFor(Util.today()).filter { !it.done }.forEach { scheduleTask(ctx, it) }
        scheduleDaily(ctx, ACT_CHECKIN, store.config.value.checkinMin, CODE_CHECKIN)
        scheduleDaily(ctx, ACT_MORNING, store.profile.value.wakeMin, CODE_MORNING)
    }

    private fun pi(ctx: Context, action: String, code: Int, id: String? = null): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action)
        if (id != null) i.putExtra("id", id)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun doneIntent(ctx: Context, id: String): PendingIntent = pi(ctx, ACT_DONE, id.hashCode() + 2, id)

    fun scheduleTask(ctx: Context, t: PlanTask) {
        val at = Util.millisAt(t.date, t.timeMin)
        if (at <= System.currentTimeMillis()) return
        setAt(ctx, at, pi(ctx, ACT_TASK, t.id.hashCode(), t.id))
    }

    fun scheduleNag(ctx: Context, t: PlanTask) {
        setAt(ctx, System.currentTimeMillis() + 10 * 60_000L, pi(ctx, ACT_NAG, t.id.hashCode() + 1, t.id))
    }

    fun cancelTask(ctx: Context, t: PlanTask) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, ACT_TASK, t.id.hashCode(), t.id))
        am.cancel(pi(ctx, ACT_NAG, t.id.hashCode() + 1, t.id))
    }

    private fun scheduleDaily(ctx: Context, action: String, min: Int, code: Int) {
        var at = Util.millisAt(Util.today(), min)
        if (at <= System.currentTimeMillis() + 1000) at = Util.millisAt(Util.dateOffset(1), min)
        setAt(ctx, at, pi(ctx, action, code))
    }

    private fun setAt(ctx: Context, millis: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }
}
