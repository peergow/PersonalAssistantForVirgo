package com.disiplinai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.disiplinai.R
import com.disiplinai.ui.MainActivity

object Notifier {
    private const val CH = "disiplin_reminders"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null) {
            val ch = NotificationChannel(CH, "Pengingat disiplin", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Pengingat tugas, check-in malam, dan peringatan"
            nm.createNotificationChannel(ch)
        }
    }

    fun show(
        ctx: Context, id: Int, title: String, text: String,
        actions: List<NotificationCompat.Action> = emptyList()
    ) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
        actions.forEach { b.addAction(it) }
        val nm = NotificationManagerCompat.from(ctx)
        if (nm.areNotificationsEnabled()) {
            try { nm.notify(id, b.build()) } catch (e: SecurityException) { /* izin belum diberikan */ }
        }
    }
}
