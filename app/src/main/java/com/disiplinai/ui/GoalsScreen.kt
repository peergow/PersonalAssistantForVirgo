package com.disiplinai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disiplinai.data.Goal
import com.disiplinai.logic.Util
import java.util.UUID

@Composable
fun GoalsScreen(vm: MainViewModel) {
    val profile by vm.store.profile.collectAsState()
    var editing by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Agent memakai target dan deadline ini untuk menyusun rencana harian.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Button(onClick = { editing = Goal(UUID.randomUUID().toString(), "") }) { Text("+ Target baru") }
        }
        if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        vm.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
        vm.info?.let { i -> item { Text(i, color = MaterialTheme.colorScheme.secondary) } }
        if (profile.goals.isEmpty()) item { Text("Belum ada target. Target tanpa deadline hanyalah angan-angan.") }
        items(profile.goals, key = { it.id }) { g ->
            val left = if (g.deadline.isNotBlank()) Util.daysUntil(g.deadline) else null
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(g.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (left != null) {
                        Text(
                            if (left >= 0) "Deadline ${g.deadline} · sisa $left hari" else "Deadline ${g.deadline} · LEWAT ${-left} hari",
                            color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (g.notes.isNotBlank()) Text(g.notes, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.breakGoal(g) }, enabled = !vm.busy) { Text("Pecah dengan Agent") }
                        OutlinedButton(onClick = { editing = g }) { Text("Ubah") }
                    }
                }
            }
        }
    }

    editing?.let { g ->
        var title by remember(g.id) { mutableStateOf(g.title) }
        var deadline by remember(g.id) { mutableStateOf(g.deadline) }
        var notes by remember(g.id) { mutableStateOf(g.notes) }
        val exists = profile.goals.any { it.id == g.id }
        val deadlineOk = deadline.isBlank() || Util.daysUntil(deadline) != null
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (exists) "Ubah target" else "Target baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Target") })
                    OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline (YYYY-MM-DD)") }, isError = !deadlineOk, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") }, maxLines = 6)
                    if (exists) TextButton(onClick = {
                        vm.store.updateProfile { p -> p.copy(goals = p.goals.filter { it.id != g.id }) }; editing = null
                    }) { Text("Hapus target", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && deadlineOk, onClick = {
                    val ng = g.copy(title = title.trim(), deadline = deadline.trim(), notes = notes)
                    vm.store.updateProfile { p ->
                        p.copy(goals = if (exists) p.goals.map { if (it.id == g.id) ng else it } else p.goals + ng)
                    }
                    editing = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } }
        )
    }
}
