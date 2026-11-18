package com.disiplinai.data

import kotlinx.serialization.Serializable

@Serializable
data class Goal(
    val id: String,
    val title: String,
    val deadline: String = "",
    val notes: String = ""
)

@Serializable
data class TimeWindow(val startMin: Int, val endMin: Int, val label: String)

@Serializable
data class Profile(
    val name: String = "",
    val wakeMin: Int = 5 * 60 + 30,
    val sleepMin: Int = 22 * 60 + 30,
    val fitnessLevel: String = "pemula",
    val equipment: String = "tanpa alat",
    val notes: String = "",
    val goals: List<Goal> = emptyList(),
    val focusWindows: List<TimeWindow> = listOf(
        TimeWindow(9 * 60, 12 * 60, "Fokus pagi"),
        TimeWindow(14 * 60, 16 * 60, "Fokus siang")
    )
)

@Serializable
data class AppRule(
    val pkg: String,
    val label: String,
    val dailyLimitMin: Int,
    val blockInFocus: Boolean = true,
    val blockAtSleep: Boolean = true,
    /** Jika diisi (mis. "olahraga"), app terkunci sampai ada 1 tugas kategori itu yang selesai hari ini. */
    val requireCategory: String = ""
)

@Serializable
data class PlanTask(
    val id: String,
    val date: String,
    val timeMin: Int,
    val durationMin: Int,
    val title: String,
    val category: String = "umum",
    val done: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class Habit(
    val id: String,
    val title: String,
    val category: String = "rutin",
    val timeMin: Int,
    val durationMin: Int = 30,
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7) // ISO: 1 = Senin ... 7 = Minggu
)

@Serializable
data class DayRecord(
    val date: String,
    val total: Int,
    val done: Int,
    val success: Boolean,
    val noPlan: Boolean = false,
    val light: Boolean = false
)

@Serializable
data class PendingChange(val pkg: String, val newRule: AppRule?, val effectiveAt: Long)

@Serializable
data class ChatMsg(val role: String, val text: String)

@Serializable
data class Config(
    val apiKey: String = "",
    val model: String = "",                       // kosong = pilih otomatis dari daftar model
    val modelCandidates: List<String> = emptyList(),
    val loosenDelayMin: Int = 10,
    val penaltyPerStrikePct: Int = 20,
    val maxPenaltyPct: Int = 60,
    val successThresholdPct: Int = 70,
    val checkinMin: Int = 21 * 60 + 30,
    val strictMode: Boolean = false,
    val strictOffAt: Long = 0L,
    val onboarded: Boolean = false,
    val theme: String = "dark"                    // dark | light | system
)

@Serializable
data class Progress(
    val streak: Int = 0,
    val best: Int = 0,
    val strikes: Int = 0,
    val lastFinalized: String = ""
)
