package com.disiplinai.data

import android.content.Context
import com.disiplinai.logic.Util
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

/** Penyimpanan lokal sederhana (SharedPreferences + JSON) dengan StateFlow untuk UI. */
class Store private constructor(ctx: Context) {
    private val sp = ctx.getSharedPreferences("disiplin_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private inline fun <reified T> load(key: String, def: T): T =
        try {
            sp.getString(key, null)?.let { json.decodeFromString<T>(it) } ?: def
        } catch (e: Exception) { def }

    private inline fun <reified T> save(key: String, v: T) {
        sp.edit().putString(key, json.encodeToString(v)).apply()
    }

    private val _profile = MutableStateFlow(load("profile", Profile()))
    private val _config = MutableStateFlow(load("config", Config()))
    private val _progress = MutableStateFlow(load("progress", Progress()))
    private val _rules = MutableStateFlow(load("rules", emptyList<AppRule>()))
    private val _tasks = MutableStateFlow(load("tasks", emptyList<PlanTask>()))
    private val _records = MutableStateFlow(load("records", emptyList<DayRecord>()))
    private val _pending = MutableStateFlow(load("pending", emptyList<PendingChange>()))
    private val _chat = MutableStateFlow(load("chat", emptyList<ChatMsg>()))
    private val _habits = MutableStateFlow(load("habits", emptyList<Habit>()))
    private val _light = MutableStateFlow(load("light_days", emptyList<String>()))
    private val _review = MutableStateFlow(sp.getString("review", "") ?: "")
    private val _focusEnd = MutableStateFlow(sp.getLong("focus_end", 0L))

    val profile: StateFlow<Profile> get() = _profile
    val config: StateFlow<Config> get() = _config
    val progress: StateFlow<Progress> get() = _progress
    val rules: StateFlow<List<AppRule>> get() = _rules
    val tasks: StateFlow<List<PlanTask>> get() = _tasks
    val records: StateFlow<List<DayRecord>> get() = _records
    val pending: StateFlow<List<PendingChange>> get() = _pending
    val chat: StateFlow<List<ChatMsg>> get() = _chat
    val habits: StateFlow<List<Habit>> get() = _habits
    val lightDays: StateFlow<List<String>> get() = _light
    val review: StateFlow<String> get() = _review
    val focusEnd: StateFlow<Long> get() = _focusEnd

    // ---------- profil / config / progres ----------
    @Synchronized fun updateProfile(f: (Profile) -> Profile) {
        val v = f(_profile.value); _profile.value = v; save("profile", v)
    }
    @Synchronized fun updateConfig(f: (Config) -> Config) {
        val v = f(_config.value); _config.value = v; save("config", v)
    }
    @Synchronized fun updateProgress(f: (Progress) -> Progress) {
        val v = f(_progress.value); _progress.value = v; save("progress", v)
    }

    // ---------- aturan aplikasi ----------
    private fun setRules(v: List<AppRule>) { _rules.value = v; save("rules", v) }
    private fun setPending(v: List<PendingChange>) { _pending.value = v; save("pending", v) }

    /** @return true jika langsung berlaku, false jika ditunda (pelonggaran saat mode strict). */
    @Synchronized fun requestRuleChange(new: AppRule): Boolean {
        val old = _rules.value.firstOrNull { it.pkg == new.pkg }
        val loosen = old != null && (
            new.dailyLimitMin > old.dailyLimitMin ||
            (old.blockInFocus && !new.blockInFocus) ||
            (old.blockAtSleep && !new.blockAtSleep) ||
            (old.requireCategory.isNotBlank() && new.requireCategory.isBlank())
        )
        if (loosen && _config.value.strictMode) {
            val eff = System.currentTimeMillis() + _config.value.loosenDelayMin * 60_000L
            setPending(_pending.value.filter { it.pkg != new.pkg } + PendingChange(new.pkg, new, eff))
            return false
        }
        setRules(_rules.value.filter { it.pkg != new.pkg } + new)
        setPending(_pending.value.filter { it.pkg != new.pkg })
        return true
    }

    /** @return true jika langsung terhapus, false jika ditunda. */
    @Synchronized fun requestRemove(pkg: String): Boolean {
        if (_config.value.strictMode) {
            val eff = System.currentTimeMillis() + _config.value.loosenDelayMin * 60_000L
            setPending(_pending.value.filter { it.pkg != pkg } + PendingChange(pkg, null, eff))
            return false
        }
        setRules(_rules.value.filter { it.pkg != pkg })
        setPending(_pending.value.filter { it.pkg != pkg })
        return true
    }

    @Synchronized fun cancelPending(pkg: String) { setPending(_pending.value.filter { it.pkg != pkg }) }

    @Synchronized fun applyDuePending() {
        val now = System.currentTimeMillis()
        val due = _pending.value.filter { it.effectiveAt <= now }
        if (due.isNotEmpty()) {
            var r = _rules.value
            due.forEach { p -> r = r.filter { it.pkg != p.pkg } + listOfNotNull(p.newRule) }
            setRules(r)
            setPending(_pending.value - due.toSet())
        }
        val c = _config.value
        if (c.strictOffAt in 1..now) updateConfig { it.copy(strictMode = false, strictOffAt = 0L) }
    }

    @Synchronized fun requestStrictOff() {
        if (_config.value.strictOffAt > 0) return
        val eff = System.currentTimeMillis() + maxOf(5, _config.value.loosenDelayMin) * 60_000L
        updateConfig { it.copy(strictOffAt = eff) }
    }
    @Synchronized fun cancelStrictOff() { updateConfig { it.copy(strictOffAt = 0L) } }

    // ---------- tugas ----------
    private fun setTasks(v: List<PlanTask>) { _tasks.value = v; save("tasks", v) }

    fun tasksFor(date: String): List<PlanTask> =
        _tasks.value.filter { it.date == date }.sortedBy { it.timeMin }

    /** Ganti semua tugas yang belum selesai pada [date]. Mengembalikan tugas yang dibuang. */
    @Synchronized fun replaceUndone(date: String, new: List<PlanTask>): List<PlanTask> {
        val removed = _tasks.value.filter { it.date == date && !it.done }
        val cutoff = Util.dateOffset(-30)
        val keep = _tasks.value.filter { !(it.date == date && !it.done) && it.date >= cutoff }
        setTasks(keep + new)
        return removed
    }

    @Synchronized fun addTask(t: PlanTask) { setTasks(_tasks.value + t) }
    @Synchronized fun deleteTask(id: String) { setTasks(_tasks.value.filter { it.id != id }) }
    @Synchronized fun setDone(id: String, done: Boolean) {
        setTasks(_tasks.value.map { if (it.id == id) it.copy(done = done) else it })
    }

    // ---------- kebiasaan ----------
    @Synchronized fun upsertHabit(h: Habit) {
        val v = _habits.value.filter { it.id != h.id } + h
        _habits.value = v; save("habits", v)
    }
    @Synchronized fun deleteHabit(id: String) {
        val v = _habits.value.filter { it.id != id }
        _habits.value = v; save("habits", v)
    }

    // ---------- hari ringan (1x per 7 hari) ----------
    fun isLight(date: String): Boolean = date in _light.value
    fun canUseLightDay(): Boolean = _light.value.none { it >= Util.dateOffset(-6) }
    @Synchronized fun useLightDay(): Boolean {
        if (!canUseLightDay()) return false
        val v = (_light.value + Util.today()).takeLast(20)
        _light.value = v; save("light_days", v)
        return true
    }

    // ---------- riwayat ----------
    @Synchronized fun addRecord(r: DayRecord) {
        val v = (_records.value.filter { it.date != r.date } + r).sortedBy { it.date }.takeLast(120)
        _records.value = v; save("records", v)
    }

    // ---------- chat & review ----------
    @Synchronized fun appendChat(m: ChatMsg) {
        val v = (_chat.value + m).takeLast(60); _chat.value = v; save("chat", v)
    }
    @Synchronized fun clearChat() { _chat.value = emptyList(); save("chat", emptyList<ChatMsg>()) }
    fun setReview(text: String) { _review.value = text; sp.edit().putString("review", text).apply() }

    // ---------- sesi fokus ----------
    fun startFocus(minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        _focusEnd.value = end; sp.edit().putLong("focus_end", end).apply()
    }
    fun cancelFocus() { _focusEnd.value = 0L; sp.edit().putLong("focus_end", 0L).apply() }

    // ---------- statistik blokir ----------
    fun recordBlock() {
        val k = "blocks_" + Util.today()
        sp.edit().putInt(k, sp.getInt(k, 0) + 1).apply()
    }
    fun blocksOn(date: String): Int = sp.getInt("blocks_$date", 0)
    fun blocksToday(): Int = blocksOn(Util.today())

    // ---------- backup ----------
    private val backupKeys = listOf(
        "profile", "config", "progress", "rules", "tasks", "records",
        "pending", "chat", "habits", "light_days", "review"
    )

    /** JSON backup. API key sengaja tidak ikut diekspor. */
    @Synchronized fun exportJson(): String {
        val o = JSONObject()
        backupKeys.forEach { k ->
            val v = if (k == "config") json.encodeToString(_config.value.copy(apiKey = "")) else sp.getString(k, null)
            if (v != null) o.put(k, v)
        }
        return o.toString(2)
    }

    @Synchronized fun importJson(text: String) {
        val o = JSONObject(text)
        val myKey = _config.value.apiKey
        val e = sp.edit()
        backupKeys.forEach { k -> if (o.has(k)) e.putString(k, o.getString(k)) }
        e.apply()
        _profile.value = load("profile", Profile())
        _config.value = load("config", Config()).copy(apiKey = myKey)
        save("config", _config.value)
        _progress.value = load("progress", Progress())
        _rules.value = load("rules", emptyList<AppRule>())
        _tasks.value = load("tasks", emptyList<PlanTask>())
        _records.value = load("records", emptyList<DayRecord>())
        _pending.value = load("pending", emptyList<PendingChange>())
        _chat.value = load("chat", emptyList<ChatMsg>())
        _habits.value = load("habits", emptyList<Habit>())
        _light.value = load("light_days", emptyList<String>())
        _review.value = sp.getString("review", "") ?: ""
    }

    companion object {
        @Volatile private var inst: Store? = null
        fun get(ctx: Context): Store = inst ?: synchronized(this) {
            inst ?: Store(ctx.applicationContext).also { inst = it }
        }
    }
}
