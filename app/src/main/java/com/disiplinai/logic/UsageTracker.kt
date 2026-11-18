package com.disiplinai.logic

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process

object UsageTracker {
    private var cacheAt = 0L
    private var cache: Map<String, Int> = emptyMap()
    private var weekAt = 0L
    private var weekCache: List<Map<String, Int>> = emptyList()

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Menit pemakaian per paket sejak 00:00 hari ini. Di-cache beberapa detik. */
    @Synchronized
    fun todayMinutes(ctx: Context, maxAgeMs: Long = 4000): Map<String, Int> {
        val now = System.currentTimeMillis()
        if (now - cacheAt < maxAgeMs) return cache
        cache = try { compute(ctx, Util.startOfToday(), now) } catch (e: Exception) { emptyMap() }
        cacheAt = now
        return cache
    }

    /** 7 hari terakhir (indeks 0 = 6 hari lalu, indeks 6 = hari ini): menit per paket. */
    @Synchronized
    fun weekly(ctx: Context): List<Map<String, Int>> {
        val now = System.currentTimeMillis()
        if (weekCache.isNotEmpty() && now - weekAt < 60_000) return weekCache
        weekCache = try {
            (6 downTo 0).map { i ->
                val start = Util.startOfDay(-i.toLong())
                val end = if (i == 0) now else Util.startOfDay(-i.toLong() + 1)
                compute(ctx, start, end)
            }
        } catch (e: Exception) { emptyList() }
        weekAt = now
        return weekCache
    }

    private fun compute(ctx: Context, from: Long, to: Long): Map<String, Int> {
        if (!hasPermission(ctx)) return emptyMap()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from, to) ?: return emptyMap()
        val e = UsageEvents.Event()
        val startAt = HashMap<String, Long>()
        val depth = HashMap<String, Int>()
        val total = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            val s = startAt.remove(pkg) ?: return
            depth.remove(pkg)
            if (at > s) total[pkg] = (total[pkg] ?: 0L) + (at - s)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                1 -> { // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                    val d = depth[pkg] ?: 0
                    if (d == 0) startAt[pkg] = e.timeStamp
                    depth[pkg] = d + 1
                }
                2 -> { // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
                    val d = (depth[pkg] ?: 1) - 1
                    if (d <= 0) close(pkg, e.timeStamp) else depth[pkg] = d
                }
                16 -> startAt.keys.toList().forEach { close(it, e.timeStamp) } // layar mati
            }
        }
        startAt.keys.toList().forEach { close(it, to) }
        return total.mapValues { (it.value / 60_000L).toInt() }
    }

    /** Daftar aplikasi yang punya ikon launcher: (paket, label). */
    fun launchableApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != ctx.packageName }
            .sortedBy { it.second.lowercase() }
    }
}
