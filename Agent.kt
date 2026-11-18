package com.disiplinai.ai

import android.content.Context
import com.disiplinai.data.ChatMsg
import com.disiplinai.data.Goal
import com.disiplinai.data.PlanTask
import com.disiplinai.data.Store
import com.disiplinai.logic.Habits
import com.disiplinai.logic.PolicyEngine
import com.disiplinai.logic.UsageTracker
import com.disiplinai.logic.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** Semua "kecerdasan" agent: menyusun rencana, mengevaluasi, memecah target, ngobrol. */
object Agent {
    private const val PERSONA =
        "Kamu adalah 'Disiplin AI', pelatih disiplin pribadi yang tegas, jujur, dan tidak basa-basi, " +
        "tetapi benar-benar peduli pada pemilik HP ini. Bicara Bahasa Indonesia yang santai namun tegas. " +
        "Jangan menawar atau memberi pembenaran saat pengguna malas; arahkan ke langkah terkecil yang bisa " +
        "dikerjakan SEKARANG. Jangan memberi nasihat medis; untuk olahraga sesuaikan dengan level dan " +
        "suruh berhenti bila ada nyeri tajam, pusing, atau sesak. Jawab ringkas (maks sekitar 150 kata) " +
        "kecuali diminta rinci. Jangan mengarang data yang tidak ada di konteks."

    private fun contextBlock(ctx: Context): String {
        val s = Store.get(ctx)
        val p = s.profile.value
        val pr = s.progress.value
        val cfg = s.config.value
        val today = Util.today()
        val usage = UsageTracker.todayMinutes(ctx, 0)
        val sb = StringBuilder()
        sb.appendLine("Tanggal: $today (${Util.dayName(today)}), jam sekarang ${Util.fmt(Util.nowMin())}")
        sb.appendLine("Nama: ${p.name.ifBlank { "(belum diisi)" }}")
        sb.appendLine("Bangun ${Util.fmt(p.wakeMin)}, tidur ${Util.fmt(p.sleepMin)}")
        sb.appendLine("Level kebugaran: ${p.fitnessLevel}; alat: ${p.equipment}; catatan: ${p.notes.ifBlank { "-" }}")
        if (p.goals.isEmpty()) sb.appendLine("Target: (belum ada)")
        else {
            sb.appendLine("Target:")
            p.goals.forEach { g ->
                val left = if (g.deadline.isNotBlank()) Util.daysUntil(g.deadline)?.let { " (sisa $it hari)" } ?: "" else ""
                sb.appendLine("- ${g.title}; deadline ${g.deadline.ifBlank { "-" }}$left")
            }
        }
        sb.appendLine("Jendela fokus (HP terkunci): " + p.focusWindows.joinToString { "${Util.fmt(it.startMin)}-${Util.fmt(it.endMin)}" })
        sb.appendLine("Streak ${pr.streak} hari (terbaik ${pr.best}), pelanggaran beruntun ${pr.strikes}, penalti jatah ${PolicyEngine.penaltyPct(cfg, pr)}%")
        s.records.value.lastOrNull()?.let {
            sb.appendLine("Hari terakhir (${it.date}): ${it.done}/${it.total} tugas, ${if (it.noPlan) "tanpa rencana" else if (it.success) "BERHASIL" else "GAGAL"}")
        }
        if (s.isLight(today)) sb.appendLine("HARI INI ADALAH HARI RINGAN: beban dikurangi, gagal tidak menghukum.")
        if (s.habits.value.isNotEmpty()) {
            sb.appendLine("Kebiasaan rutin pengguna:")
            s.habits.value.forEach { sb.appendLine("- ${it.title} jam ${Util.fmt(it.timeMin)} (${it.durationMin} mnt, ${it.category})") }
        }
        val tasks = s.tasksFor(today)
        if (tasks.isNotEmpty()) {
            sb.appendLine("Tugas hari ini:")
            tasks.forEach { sb.appendLine("- ${Util.fmt(it.timeMin)} ${it.title} [${if (it.done) "selesai" else "belum"}]") }
        }
        val rules = s.rules.value
        if (rules.isNotEmpty()) {
            sb.appendLine("Pemakaian aplikasi dibatasi hari ini:")
            rules.forEach {
                sb.appendLine("- ${it.label}: ${usage[it.pkg] ?: 0} mnt dari jatah ${PolicyEngine.effectiveLimit(it, cfg, pr)} mnt")
            }
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------ rencana harian
    suspend fun generatePlan(ctx: Context, date: String, note: String): List<PlanTask> {
        val store = Store.get(ctx)
        val cfg = store.config.value
        val prompt = withContext(Dispatchers.IO) {
            val p = store.profile.value
            val isToday = date == Util.today()
            val from = if (isToday) maxOf(p.wakeMin, Util.nowMin() + 10) else p.wakeMin
            """
            KONTEKS PENGGUNA:
            ${contextBlock(ctx)}

            TUGAS: Buatkan rencana harian untuk tanggal $date.
            Aturan:
            - Jadwalkan hanya antara ${Util.fmt(from)} dan ${Util.fmt(p.sleepMin)}.
            - 6 sampai 12 tugas, waktunya tidak boleh bertumpuk.
            - Wajib ada: 1 sesi olahraga sesuai level (sebutkan gerakan/jumlah spesifik di judul),
              minimal 1 blok kerja mendalam untuk target prioritas (utamakan yang deadline-nya dekat),
              jeda makan/istirahat singkat, dan 1 "Evaluasi & check-in" 10 menit sebelum jam ${Util.fmt(cfg.checkinMin)}.
            - Isi jendela fokus dengan pekerjaan target, karena HP akan terkunci saat itu.
            - Judul harus spesifik dan terukur, bukan "belajar" tetapi "Belajar bab 3: kerjakan 10 soal".
            - Kebiasaan rutin pengguna (lihat konteks) SUDAH otomatis ditambahkan ke jadwal. Jangan dijadwalkan ulang
              dan jangan bertumpuk dengan jamnya.
            - ${if (store.isLight(date)) "HARI RINGAN: pakai 4-6 tugas saja, olahraga ringan, beban sekitar setengah dari biasanya." else "Hari normal."}
            - Catatan dari pengguna untuk hari ini: ${note.ifBlank { "tidak ada" }}

            Balas HANYA JSON dengan format:
            {"tasks":[{"time":"HH:mm","duration":30,"title":"...","category":"olahraga|belajar|kerja|rutin|target|istirahat"}]}
            """.trimIndent()
        }
        val raw = GeminiClient.generate(ctx, PERSONA, listOf(ChatMsg("user", prompt)), jsonMode = true, timeoutMs = 30_000)
        return parsePlan(raw, date)
    }

    private fun parsePlan(raw: String, date: String): List<PlanTask> {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr: JSONArray = try {
            JSONObject(clean).getJSONArray("tasks")
        } catch (e: Exception) {
            try { JSONArray(clean) } catch (e2: Exception) {
                throw IOException("Format rencana dari AI tidak terbaca. Coba lagi.")
            }
        }
        val now = System.currentTimeMillis()
        val out = ArrayList<PlanTask>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val t = Util.parseTime(o.optString("time")) ?: continue
            val title = o.optString("title").trim()
            if (title.isEmpty()) continue
            out.add(
                PlanTask(
                    id = UUID.randomUUID().toString(), date = date, timeMin = t,
                    durationMin = o.optInt("duration", 30).coerceIn(5, 240),
                    title = title, category = o.optString("category", "umum").ifBlank { "umum" },
                    createdAt = now
                )
            )
        }
        return out.sortedBy { it.timeMin }
    }

    // ------------------------------------------------------------------ evaluasi harian
    suspend fun evaluateDay(ctx: Context): String {
        val cfg = Store.get(ctx).config.value
        val prompt = withContext(Dispatchers.IO) {
            """
            KONTEKS PENGGUNA:
            ${contextBlock(ctx)}

            TUGAS: Evaluasi hari ini dengan jujur. Format:
            1) Penilaian singkat (1-2 kalimat, apa adanya, sebut angka tugas selesai dan pemakaian aplikasi).
            2) Apa yang gagal dan kemungkinan penyebabnya (berdasarkan data, jangan menebak berlebihan).
            3) Tepat 3 perintah konkret untuk besok.
            """.trimIndent()
        }
        return GeminiClient.generate(ctx, PERSONA, listOf(ChatMsg("user", prompt)))
    }

    // ------------------------------------------------------------------ obrolan
    suspend fun chat(ctx: Context, history: List<ChatMsg>): String {
        val cfg = Store.get(ctx).config.value
        val system = withContext(Dispatchers.IO) { PERSONA + "\n\nKONTEKS PENGGUNA SAAT INI:\n" + contextBlock(ctx) }
        return GeminiClient.generate(ctx, system, history.takeLast(20))
    }

    // ------------------------------------------------------------------ pecah target
    suspend fun breakGoal(ctx: Context, goal: Goal): String {
        val cfg = Store.get(ctx).config.value
        val prompt = withContext(Dispatchers.IO) {
            """
            KONTEKS PENGGUNA:
            ${contextBlock(ctx)}

            TUGAS: Pecah target ini menjadi rencana kerja realistis.
            Target: ${goal.title}
            Deadline: ${goal.deadline.ifBlank { "tidak ditentukan (usulkan yang masuk akal)" }}
            Catatan: ${goal.notes.ifBlank { "-" }}

            Format (teks biasa, ringkas):
            - Definisi "selesai" yang terukur (1 kalimat)
            - Milestone per minggu sampai deadline (maks 8 baris)
            - Kebiasaan harian minimum (apa yang harus dilakukan tiap hari, durasinya)
            - Langkah pertama yang bisa dikerjakan dalam 10 menit hari ini
            """.trimIndent()
        }
        return GeminiClient.generate(ctx, PERSONA, listOf(ChatMsg("user", prompt)))
    }

    // ------------------------------------------------------------------ tinjauan mingguan
    suspend fun weeklyReview(ctx: Context): String {
        val prompt = withContext(Dispatchers.IO) {
            val st = Store.get(ctx)
            val recs = st.records.value.takeLast(7)
            val wk = UsageTracker.weekly(ctx)
            val sb = StringBuilder()
            recs.forEach {
                sb.appendLine("- ${it.date} (${Util.dayName(it.date)}): " +
                    if (it.noPlan) "tanpa rencana" else "${it.done}/${it.total} tugas, ${if (it.success) "berhasil" else if (it.light) "hari ringan" else "gagal"}")
            }
            st.rules.value.forEach { r ->
                val total = wk.sumOf { it[r.pkg] ?: 0 }
                sb.appendLine("Pemakaian 7 hari ${r.label}: $total menit (jatah dasar ${r.dailyLimitMin}/hari)")
            }
            """
            KONTEKS PENGGUNA:
            ${contextBlock(ctx)}

            DATA 7 HARI TERAKHIR:
            $sb

            TUGAS: Buat tinjauan mingguan yang jujur. Format:
            1) Skor minggu ini (angka 0-100 beserta alasan singkat berdasar data).
            2) Pola yang terlihat (hari paling lemah, aplikasi paling bocor). Jangan menebak di luar data.
            3) Satu hal yang harus dipertahankan.
            4) Tepat 3 perubahan konkret untuk minggu depan (boleh menyarankan penyesuaian jatah aplikasi atau jam fokus).
            """.trimIndent()
        }
        val text = GeminiClient.generate(ctx, PERSONA, listOf(ChatMsg("user", prompt)))
        Store.get(ctx).setReview(Util.today() + "\n" + text)
        return text
    }
}
