package com.disiplinai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disiplinai.data.DayRecord
import com.disiplinai.data.Habit
import com.disiplinai.logic.Habits
import com.disiplinai.logic.Util
import java.util.UUID

@Composable
fun ProgressScreen(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            ScreenTitle("Progres", "Laporan, target, dan kebiasaan")
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf("Laporan", "Target", "Kebiasaan").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ReportScreen(vm)
                1 -> GoalsScreen(vm)
                else -> HabitsScreen(vm)
            }
        }
    }
}

// ============================================================ LAPORAN
@Composable
private fun ReportScreen(vm: MainViewModel) {
    val store = vm.store
    val records by store.records.collectAsState()
    val rules by store.rules.collectAsState()
    val review by store.review.collectAsState()
    val progress by store.progress.collectAsState()
    LaunchedEffect(Unit) { vm.loadWeekly() }

    val last7 = records.takeLast(7)
    val planned = last7.filter { !it.noPlan }
    val avg = if (planned.isEmpty()) 0 else planned.sumOf { if (it.total == 0) 0 else it.done * 100 / it.total } / planned.size
    val okDays = planned.count { it.success }
    val blocks = (0..6).sumOf { store.blocksOn(Util.dateOffset(-it.toLong())) }
    val leak = rules.map { r -> r to vm.weekly.sumOf { it[r.pkg] ?: 0 } }.sortedByDescending { it.second }.filter { it.second > 0 }.take(5)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionCard("Ringkasan 7 hari") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock("$okDays/${planned.size}", "hari berhasil")
                    StatBlock("$avg%", "rata-rata selesai")
                    StatBlock("$blocks", "godaan diblokir")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock("${progress.streak}", "streak")
                    StatBlock("${progress.best}", "streak terbaik")
                    StatBlock("${progress.strikes}", "gagal beruntun")
                }
            }
        }
        item { Heatmap(records) }
        item {
            SectionCard("Aplikasi paling bocor (7 hari)") {
                if (leak.isEmpty()) Text("Belum ada data pemakaian untuk aplikasi yang dibatasi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val max = (leak.firstOrNull()?.second ?: 1).coerceAtLeast(1)
                leak.forEach { (r, m) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.label); Text("$m mnt")
                    }
                    LinearProgressIndicator(progress = { m.toFloat() / max }, modifier = Modifier.fillMaxWidth(), color = Brand.Warning)
                }
            }
        }
        item {
            SectionCard("Tinjauan mingguan oleh Agent") {
                if (review.isNotBlank()) Text(review) else Text("Belum ada. Minta agent menganalisis datamu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                vm.info?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                Button(onClick = { vm.weeklyReview() }, enabled = !vm.busy) { Text("Buat tinjauan mingguan") }
            }
        }
    }
}

@Composable
private fun Heatmap(records: List<DayRecord>) {
    val byDate = records.associateBy { it.date }
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val weeks = 5
    SectionCard("5 minggu terakhir") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (w in 0 until weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (d in 0 until 7) {
                        val offset = -((weeks - 1 - w) * 7 + (6 - d)).toLong()
                        val date = Util.dateOffset(offset)
                        val r = byDate[date]
                        val color = when {
                            offset > 0 -> Color.Transparent
                            r == null -> empty
                            r.noPlan -> empty
                            r.light && !r.success -> Brand.Warning.copy(alpha = 0.5f)
                            r.total == 0 -> empty
                            r.done * 100 / r.total >= 70 -> Brand.Success
                            r.done * 100 / r.total >= 40 -> Brand.Warning
                            else -> Brand.Danger
                        }
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(color))
                    }
                }
            }
        }
        Text("Hijau ≥70% selesai · kuning 40-69% atau hari ringan · merah <40% · abu-abu tanpa data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================ KEBIASAAN
private val DAY_LABELS = listOf("Sn", "Sl", "Rb", "Km", "Jm", "Sb", "Mg")
private val CATEGORIES = listOf("olahraga", "belajar", "kerja", "rutin")

@Composable
private fun HabitsScreen(vm: MainViewModel) {
    val store = vm.store
    val habits by store.habits.collectAsState()
    val cfg by store.config.collectAsState()
    var editing by remember { mutableStateOf<Habit?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Kebiasaan muncul otomatis sebagai tugas di hari yang dipilih, tanpa perlu dibuat ulang oleh agent.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Button(onClick = {
                editing = Habit(UUID.randomUUID().toString(), "", "olahraga", 6 * 60 + 15, 30)
            }) { Text("+ Kebiasaan baru") }
        }
        if (habits.isEmpty()) item { Text("Belum ada kebiasaan. Mulai dengan satu yang kecil dan konsisten.") }
        items(habits, key = { it.id }) { h ->
            Card(Modifier.fillMaxWidth().clickable { editing = h }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(h.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${Util.fmt(h.timeMin)} · ${h.durationMin} mnt · ${h.category} · " +
                                if (h.days.size == 7) "setiap hari" else h.days.sorted().joinToString(" ") { DAY_LABELS[it - 1] },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!cfg.strictMode) {
                        IconButton(onClick = { store.deleteHabit(h.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Hapus") }
                    }
                }
            }
        }
    }

    editing?.let { h ->
        var title by remember(h.id) { mutableStateOf(h.title) }
        var time by remember(h.id) { mutableIntStateOf(h.timeMin) }
        var dur by remember(h.id) { mutableIntStateOf(h.durationMin) }
        var cat by remember(h.id) { mutableStateOf(h.category) }
        var days by remember(h.id) { mutableStateOf(h.days.toSet()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (h.title.isBlank()) "Kebiasaan baru" else "Ubah kebiasaan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Judul (spesifik)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField("Jam", time, Modifier.weight(1f)) { time = it }
                        NumberField("Menit", dur, Modifier.weight(1f)) { dur = it.coerceIn(5, 240) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CATEGORIES.forEach { c ->
                            if (cat == c) Button(onClick = {}, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(c, style = MaterialTheme.typography.labelSmall) }
                            else OutlinedButton(onClick = { cat = c }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(c, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DAY_LABELS.forEachIndexed { i, l ->
                            val d = i + 1
                            val sel = d in days
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { days = if (sel) days - d else days + d },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(l, style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && days.isNotEmpty(), onClick = {
                    store.upsertHabit(h.copy(title = title.trim(), timeMin = time, durationMin = dur, category = cat, days = days.sorted()))
                    Habits.materialize(vm.getApp())
                    editing = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } }
        )
    }
}
