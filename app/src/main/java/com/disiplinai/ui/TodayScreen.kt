package com.disiplinai.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.disiplinai.data.PlanTask
import com.disiplinai.logic.PolicyEngine
import com.disiplinai.logic.Util
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun TodayScreen(vm: MainViewModel, onOpenAgent: () -> Unit) {
    val ctx = LocalContext.current
    val store = vm.store
    val allTasks by store.tasks.collectAsState()
    val records by store.records.collectAsState()
    val progress by store.progress.collectAsState()
    val cfg by store.config.collectAsState()
    val rules by store.rules.collectAsState()
    val profile by store.profile.collectAsState()
    val lightDays by store.lightDays.collectAsState()
    val today = Util.today()
    val tasks = allTasks.filter { it.date == today }.sortedBy { it.timeMin }
    val done = tasks.count { it.done }
    val pct = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
    val next = tasks.firstOrNull { !it.done }
    val isLight = today in lightDays
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            vm.refreshUsage()
            if (n % 15 == 0) vm.loadWeekly()
            n++
            delay(20_000)
        }
    }

    val hour = LocalTime.now().hour
    val greet = when { hour < 11 -> "Selamat pagi"; hour < 15 -> "Selamat siang"; hour < 18 -> "Selamat sore"; else -> "Selamat malam" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenTitle(
                if (profile.name.isBlank()) greet else "$greet, ${profile.name}",
                "${Util.dayName(today)}, $today"
            )
        }

        item { PermissionsPanel(showAll = false) }

        // ---- kartu utama
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(pct, "${(pct * 100).toInt()}%", diameter = 104.dp)
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$done dari ${tasks.size} tugas", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Streak ${progress.streak} hari · terbaik ${progress.best}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        val pen = PolicyEngine.penaltyPct(cfg, progress)
                        Text(
                            if (pen > 0) "Penalti jatah aktif: -$pen%" else "Jatah aplikasi penuh",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (pen > 0) Brand.Danger else Brand.Success
                        )
                        Text("${store.blocksToday()}x godaan diblokir hari ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // ---- tugas berikutnya
        if (next != null) {
            item {
                SectionCard("Berikutnya") {
                    Text(next.title, style = MaterialTheme.typography.titleMedium)
                    Text("${Util.fmt(next.timeMin)} · ${next.durationMin} mnt · ${next.category}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.toggleTask(next, true) }) { Text("Tandai selesai") }
                }
            }
        }

        item { FocusCard(vm) }

        // ---- pemakaian
        item {
            SectionCard("Pemakaian aplikasi dibatasi") {
                if (rules.isEmpty()) Text("Belum ada. Atur di tab Aplikasi.")
                rules.sortedByDescending { vm.usage[it.pkg] ?: 0 }.forEach { r ->
                    val used = vm.usage[r.pkg] ?: 0
                    val limit = PolicyEngine.effectiveLimit(r, cfg, progress)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.label)
                        Text(if (limit == 0) "diblokir" else "$used / $limit mnt", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (limit > 0) {
                        LinearProgressIndicator(
                            progress = { (used.toFloat() / limit).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (used >= limit) Brand.Danger else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (vm.weekly.size == 7 && rules.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("7 hari terakhir (total menit)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WeekBars(vm.weekly.map { day -> rules.sumOf { day[it.pkg] ?: 0 } })
                }
            }
        }

        // ---- rencana
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rencana hari ini", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { showAdd = true }) { Text("+ Tugas") }
            }
        }
        if (tasks.isEmpty()) {
            item {
                SectionCard {
                    Text("Belum ada rencana. Hari tanpa rencana adalah hari yang bocor.")
                    Button(onClick = onOpenAgent) { Text("Buat rencana dengan Agent") }
                }
            }
        }
        items(tasks, key = { it.id }) { t -> TaskRow(t, vm, cfg.strictMode) }

        // ---- hari ringan
        item {
            SectionCard("Hari ringan") {
                if (isLight) {
                    Text("Aktif hari ini: jika gagal, streak dan jatah aplikasi tidak dihukum.", color = Brand.Success)
                } else {
                    Text("Sedang sakit, lembur, atau kacau? Satu kali per 7 hari, gagal hari ini tidak memutus streak. Tugas tetap harus dicoba.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = store.canUseLightDay(), onClick = {
                        val ok = vm.useLightDay()
                        Toast.makeText(ctx, if (ok) "Hari ringan diaktifkan." else "Sudah dipakai dalam 7 hari terakhir.", Toast.LENGTH_SHORT).show()
                    }) { Text("Aktifkan hari ringan") }
                }
            }
        }

        item { HistoryRow(records.takeLast(7)) }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showAdd) AddTaskDialog(onDismiss = { showAdd = false }) { time, dur, title ->
        vm.addTask(time, dur, title); showAdd = false
    }
}

@Composable
private fun WeekBars(values: List<Int>) {
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            val date = Util.dateOffset((i - 6).toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$v", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.width(26.dp).height((v.toFloat() / max * 64).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (i == 6) bar else bar.copy(alpha = 0.45f))
                )
                Text(Util.dayName(date).take(3), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FocusCard(vm: MainViewModel) {
    val store = vm.store
    val focusEnd by store.focusEnd.collectAsState()
    val cfg by store.config.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focusEnd) {
        while (focusEnd > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(1000) }
        now = System.currentTimeMillis()
    }
    val remain = (focusEnd - now) / 1000
    SectionCard("Sesi Fokus") {
        if (remain > 0) {
            Text("%02d:%02d".format(remain / 60, remain % 60), style = MaterialTheme.typography.displaySmall)
            Text("Semua aplikasi yang dibatasi sedang diblokir.")
            if (cfg.strictMode) Text("Mode strict: sesi tidak bisa dibatalkan.", style = MaterialTheme.typography.bodySmall)
            else OutlinedButton(onClick = { store.cancelFocus() }) { Text("Batalkan") }
        } else {
            Text("Blokir semua aplikasi yang dibatasi selama:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 45, 90).forEach { m ->
                    Button(onClick = { store.startFocus(m) }, modifier = Modifier.weight(1f)) { Text("$m m") }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(t: PlanTask, vm: MainViewModel, strict: Boolean) {
    val overdue = !t.done && Util.nowMin() > t.timeMin + t.durationMin
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = t.done, onCheckedChange = { vm.toggleTask(t, it) })
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified
                )
                Text(
                    "${Util.fmt(t.timeMin)} · ${t.durationMin} mnt · ${t.category}" + if (overdue) " · TERLAMBAT" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!strict || vm.canDelete(t)) {
                IconButton(onClick = { vm.deleteTask(t) }) { Icon(Icons.Filled.Delete, contentDescription = "Hapus") }
            }
        }
    }
}

@Composable
private fun HistoryRow(records: List<com.disiplinai.data.DayRecord>) {
    if (records.isEmpty()) return
    SectionCard("Riwayat 7 hari terakhir") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            records.forEach { r ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (r.noPlan) "–" else if (r.success) "✓" else if (r.light) "~" else "✗",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = if (r.noPlan || r.light) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (r.success) Brand.Success else Brand.Danger
                    )
                    Text(r.date.takeLast(5), style = MaterialTheme.typography.labelSmall)
                    if (!r.noPlan) Text("${r.done}/${r.total}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (Int, Int, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableIntStateOf((Util.nowMin() + 15).coerceAtMost(23 * 60 + 59)) }
    var dur by remember { mutableIntStateOf(30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah tugas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Judul (spesifik & terukur)") })
                TimeField("Jam (HH:mm)", time) { time = it }
                NumberField("Durasi (menit)", dur) { dur = it }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onAdd(time, dur, title) }) { Text("Tambah") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
