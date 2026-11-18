package com.disiplinai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AgentScreen(vm: MainViewModel) {
    val chat by vm.store.chat.collectAsState()
    var input by remember { mutableStateOf("") }
    var showPlan by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(chat.size, vm.busy) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Box(Modifier.padding(top = 16.dp)) { ScreenTitle("Agent", "Pelatih disiplinmu. Tidak akan mengasihani.") }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showPlan = true }, enabled = !vm.busy) { Text("Rencana hari ini") }
            OutlinedButton(onClick = { vm.evaluate() }, enabled = !vm.busy) { Text("Evaluasi hari ini") }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp)) }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chat.isEmpty()) {
                item {
                    Text(
                        "Mulai dengan \"Rencana hari ini\", atau ketik apa saja: curhat malas, minta strategi, laporan progres. Agent tidak akan mengasihani kamu.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(chat) { m ->
                val mine = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) { Text(m.text, Modifier.padding(12.dp)) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Tulis ke agent...") },
                modifier = Modifier.weight(1f), maxLines = 4
            )
            IconButton(enabled = !vm.busy && input.isNotBlank(), onClick = { vm.send(input); input = "" }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim")
            }
        }
    }

    if (showPlan) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPlan = false },
            title = { Text("Buat rencana hari ini") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tugas yang belum selesai akan diganti. Ada kendala hari ini? (opsional)")
                    OutlinedTextField(note, { note = it }, placeholder = { Text("mis. kuliah sampai jam 15:00") })
                }
            },
            confirmButton = { TextButton(onClick = { vm.generatePlan(note); showPlan = false }) { Text("Buat") } },
            dismissButton = { TextButton(onClick = { showPlan = false }) { Text("Batal") } }
        )
    }
}
