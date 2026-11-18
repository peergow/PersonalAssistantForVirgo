package com.disiplinai.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.disiplinai.ai.Agent
import com.disiplinai.ai.GeminiClient
import com.disiplinai.data.ChatMsg
import com.disiplinai.data.Goal
import com.disiplinai.data.PlanTask
import com.disiplinai.data.Store
import com.disiplinai.logic.Discipline
import com.disiplinai.logic.Habits
import com.disiplinai.logic.UsageTracker
import com.disiplinai.logic.Util
import com.disiplinai.service.Notifier
import com.disiplinai.service.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store: Store = Store.get(app)
    private val ctx get() = getApplication<Application>()
    fun getApp(): Application = getApplication()

    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)
    var usage by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var weekly by mutableStateOf<List<Map<String, Int>>>(emptyList()); private set
    var installed by mutableStateOf<List<Pair<String, String>>>(emptyList()); private set

    fun onAppOpen() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Notifier.ensureChannel(ctx)
                store.applyDuePending()
                Discipline.finalizeOverdue(ctx)
                Habits.materialize(ctx)
                Reminders.scheduleAll(ctx)
            }
            refreshUsage()
            loadWeekly()
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            usage = withContext(Dispatchers.IO) {
                store.applyDuePending()
                UsageTracker.todayMinutes(ctx, 0)
            }
        }
    }

    fun loadWeekly() {
        viewModelScope.launch { weekly = withContext(Dispatchers.IO) { UsageTracker.weekly(ctx) } }
    }

    fun loadInstalled() {
        viewModelScope.launch { installed = withContext(Dispatchers.IO) { UsageTracker.launchableApps(ctx) } }
    }

    private fun launchAgent(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true; error = null; info = null
            try { block() } catch (e: Exception) { error = e.message ?: "Terjadi kesalahan." } finally { busy = false }
        }
    }

    // ---------------- tugas ----------------
    fun toggleTask(t: PlanTask, done: Boolean) {
        store.setDone(t.id, done)
        if (done) Reminders.cancelTask(ctx, t) else Reminders.scheduleTask(ctx, t)
    }

    fun canDelete(t: PlanTask): Boolean =
        !store.config.value.strictMode || System.currentTimeMillis() - t.createdAt < 15 * 60_000L

    fun deleteTask(t: PlanTask) {
        if (!canDelete(t)) return
        Reminders.cancelTask(ctx, t)
        store.deleteTask(t.id)
    }

    fun addTask(timeMin: Int, duration: Int, title: String) {
        val t = PlanTask(
            UUID.randomUUID().toString(), Util.today(), timeMin, duration.coerceIn(5, 240),
            title.trim(), "manual", false, System.currentTimeMillis()
        )
        store.addTask(t)
        Reminders.scheduleTask(ctx, t)
    }

    /** @return true jika berhasil mengaktifkan hari ringan. */
    fun useLightDay(): Boolean = store.useLightDay()

    // ---------------- agent ----------------
    fun generatePlan(note: String) = launchAgent {
        val date = Util.today()
        val strict = store.config.value.strictMode
        val locked = store.tasksFor(date).any {
            !it.done && !Habits.isHabitTask(it) && System.currentTimeMillis() - it.createdAt > 15 * 60_000L
        }
        if (strict && locked) {
            throw IllegalStateException("Mode strict: rencana hari ini terkunci (hanya bisa diganti 15 menit pertama).")
        }
        withContext(Dispatchers.IO) { Habits.materialize(ctx, date) }
        val tasks = Agent.generatePlan(ctx, date, note)
        if (tasks.isEmpty()) throw IllegalStateException("AI tidak mengembalikan tugas. Coba lagi.")
        val removed = store.replaceUndone(date, tasks)
        withContext(Dispatchers.IO) {
            removed.forEach { Reminders.cancelTask(ctx, it) }
            Habits.materialize(ctx, date)
            Reminders.scheduleAll(ctx)
        }
        store.appendChat(
            ChatMsg(
                "agent",
                "Rencana hari ini dibuat (${tasks.size} tugas). Kerjakan yang pertama sekarang, jangan ditunda.\n\n" +
                    tasks.joinToString("\n") { "• ${Util.fmt(it.timeMin)} ${it.title}" }
            )
        )
    }

    fun evaluate() = launchAgent {
        store.appendChat(ChatMsg("user", "Evaluasi hari ini."))
        store.appendChat(ChatMsg("agent", Agent.evaluateDay(ctx)))
    }

    fun weeklyReview() = launchAgent {
        loadWeekly()
        Agent.weeklyReview(ctx)
        info = "Tinjauan mingguan selesai."
    }

    fun send(text: String) {
        if (text.isBlank()) return
        launchAgent {
            store.appendChat(ChatMsg("user", text.trim()))
            store.appendChat(ChatMsg("agent", Agent.chat(ctx, store.chat.value)))
        }
    }

    fun breakGoal(goal: Goal) = launchAgent {
        val text = Agent.breakGoal(ctx, goal)
        store.updateProfile { p -> p.copy(goals = p.goals.map { if (it.id == goal.id) it.copy(notes = text) else it }) }
        info = "Target \"${goal.title}\" sudah dipecah. Lihat catatannya."
    }

    // ---------------- model ----------------
    fun refreshModels() = launchAgent {
        val list = GeminiClient.refreshModels(ctx)
        info = if (list.isEmpty()) "Key valid, tetapi tidak ada model teks yang cocok ditemukan."
        else "Ditemukan ${list.size} model. Dipakai: ${store.config.value.model}"
    }

    fun testGemini() = launchAgent {
        val r = GeminiClient.generate(
            ctx, "Balas sangat singkat.", listOf(ChatMsg("user", "Balas hanya dengan kata: SIAP")), timeoutMs = 30_000
        )
        info = "Berhasil (${store.config.value.model}): ${r.trim().take(40)}"
    }

    // ---------------- backup ----------------
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) }
                }
                error = null; info = "Backup tersimpan (API key tidak ikut diekspor)."
            } catch (e: Exception) { error = "Gagal menyimpan backup: ${e.message}" }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }
                store.importJson(text)
                withContext(Dispatchers.IO) { Reminders.scheduleAll(ctx) }
                error = null; info = "Data berhasil dipulihkan."
            } catch (e: Exception) { error = "Gagal memulihkan: file tidak valid (${e.message})" }
        }
    }
}
