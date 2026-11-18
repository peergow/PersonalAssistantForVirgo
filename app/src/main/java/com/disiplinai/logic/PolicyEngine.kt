package com.disiplinai.logic

import android.content.Context
import com.disiplinai.data.AppRule
import com.disiplinai.data.Config
import com.disiplinai.data.Profile
import com.disiplinai.data.Progress
import com.disiplinai.data.Store

/** Otak penegak disiplin. Murni aturan, tanpa AI, jadi tetap jalan walau internet/kuota API mati. */
object PolicyEngine {
    /** [freeAt] = kapan blokir berakhir (ms epoch), 0 jika tidak ditentukan waktu. */
    data class Verdict(val blocked: Boolean, val reason: String = "", val freeAt: Long = 0L)

    fun penaltyPct(cfg: Config, p: Progress): Int =
        minOf(cfg.maxPenaltyPct, p.strikes * cfg.penaltyPerStrikePct)

    fun effectiveLimit(rule: AppRule, cfg: Config, p: Progress): Int {
        if (rule.dailyLimitMin <= 0) return 0
        val v = rule.dailyLimitMin * (100 - penaltyPct(cfg, p)) / 100
        return maxOf(5, v)
    }

    fun inSleep(nowMin: Int, p: Profile): Boolean =
        if (p.sleepMin > p.wakeMin) nowMin >= p.sleepMin || nowMin < p.wakeMin
        else nowMin in p.sleepMin until p.wakeMin

    private fun nextAt(min: Int): Long {
        val t = Util.millisAt(Util.today(), min)
        return if (t > System.currentTimeMillis()) t else Util.millisAt(Util.dateOffset(1), min)
    }

    fun check(ctx: Context, pkg: String): Verdict {
        val store = Store.get(ctx)
        store.applyDuePending()
        val rule = store.rules.value.firstOrNull { it.pkg == pkg } ?: return Verdict(false)
        val now = System.currentTimeMillis()
        val profile = store.profile.value
        val cfg = store.config.value
        val nowMin = Util.nowMin()

        if (store.focusEnd.value > now) {
            val end = java.time.Instant.ofEpochMilli(store.focusEnd.value)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            return Verdict(true, "Sesi fokus aktif sampai %02d:%02d.".format(end.hour, end.minute), store.focusEnd.value)
        }
        if (rule.blockAtSleep && inSleep(nowMin, profile)) {
            return Verdict(
                true, "Jam tidur (${Util.fmt(profile.sleepMin)}-${Util.fmt(profile.wakeMin)}). Letakkan HP.",
                nextAt(profile.wakeMin)
            )
        }
        if (rule.blockInFocus) {
            val w = profile.focusWindows.firstOrNull { nowMin in it.startMin until it.endMin }
            if (w != null) {
                return Verdict(
                    true, "${w.label} (${Util.fmt(w.startMin)}-${Util.fmt(w.endMin)}).",
                    Util.millisAt(Util.today(), w.endMin)
                )
            }
        }
        if (rule.requireCategory.isNotBlank()) {
            val ok = store.tasksFor(Util.today()).any { it.done && it.category.equals(rule.requireCategory, true) }
            if (!ok) {
                return Verdict(true, "Selesaikan dulu 1 tugas \"${rule.requireCategory}\" hari ini untuk membuka ${rule.label}.")
            }
        }
        val limit = effectiveLimit(rule, cfg, store.progress.value)
        if (limit == 0) return Verdict(true, "Aplikasi ini diblokir total.")
        val used = UsageTracker.todayMinutes(ctx)[pkg] ?: 0
        if (used >= limit) {
            return Verdict(true, "Jatah hari ini habis: $used dari $limit menit.", Util.millisAt(Util.dateOffset(1), 0))
        }
        return Verdict(false)
    }
}
