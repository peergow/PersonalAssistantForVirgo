package com.disiplinai.logic

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.disiplinai.service.BlockerService

data class PermItem(
    val id: String,
    val title: String,
    val desc: String,
    val ok: Boolean,
    val required: Boolean,
    val intent: Intent
)

object Perms {
    fun accessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(ctx, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun status(ctx: Context): List<PermItem> {
        val pkg = ctx.packageName
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        return listOf(
            PermItem(
                "usage", "Akses data penggunaan",
                "Wajib. Untuk menghitung berapa lama tiap aplikasi dipakai.",
                UsageTracker.hasPermission(ctx), true,
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            ),
            PermItem(
                "access", "Layanan aksesibilitas",
                "Wajib. Untuk mendeteksi & memblokir aplikasi. Jika tombol abu-abu (Android 13+, APK dari luar Play Store): " +
                    "Info Aplikasi > titik tiga > Izinkan pengaturan terbatas.",
                accessibilityEnabled(ctx), true,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            ),
            PermItem(
                "notif", "Notifikasi",
                "Untuk pengingat tugas dan check-in.",
                NotificationManagerCompat.from(ctx).areNotificationsEnabled(), false,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            ),
            PermItem(
                "alarm", "Alarm tepat waktu",
                "Agar pengingat berbunyi persis di menitnya.",
                exactOk, false,
                if (Build.VERSION.SDK_INT >= 31)
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg"))
                else Intent(Settings.ACTION_SETTINGS)
            ),
            PermItem(
                "battery", "Abaikan optimasi baterai",
                "Agar sistem tidak mematikan layanan pemblokir. Di Xiaomi/Oppo/Vivo aktifkan juga Autostart.",
                pm.isIgnoringBatteryOptimizations(pkg), false,
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        )
    }
}
