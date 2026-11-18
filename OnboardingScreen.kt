package com.disiplinai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.disiplinai.data.Goal
import com.disiplinai.logic.Util
import java.util.UUID

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val store = vm.store
    val cfg by store.config.collectAsState()
    val prof by store.profile.collectAsState()
    val rules by store.rules.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var goalTitle by remember { mutableStateOf("") }
    var goalDeadline by remember { mutableStateOf("") }
    val last = 4
    val deadlineOk = goalDeadline.isBlank() || Util.daysUntil(goalDeadline) != null

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp)
    ) {
        Text("Langkah ${step + 1} dari ${last + 1}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { (step + 1) / (last + 1f) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    ScreenTitle("Disiplin AI", "Pelatih disiplin pribadi di HP-mu. Mari siapkan dulu.")
                    Text("Agent memakai Google Gemini. Buat API key gratis di aistudio.google.com/apikey, lalu tempel di sini. Key hanya disimpan di HP ini.")
                    OutlinedTextField(
                        cfg.apiKey, { v -> store.updateConfig { it.copy(apiKey = v.trim()) } },
                        label = { Text("API key Gemini") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { vm.refreshModels() }, enabled = !vm.busy && cfg.apiKey.isNotBlank()) {
                        Text("Hubungkan & pilih model otomatis")
                    }
                    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    vm.info?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("Boleh dilewati sekarang. Blokir aplikasi tetap bekerja tanpa AI.", style = MaterialTheme.typography.bodySmall)
                }
                1 -> {
                    ScreenTitle("Izin", "Tanpa dua izin wajib, pemblokiran tidak bekerja.")
                    PermissionsPanel(showAll = true)
                }
                2 -> {
                    ScreenTitle("Profil & jadwal", "Agent memakai ini untuk menyusun rencana dan jam blokir.")
                    OutlinedTextField(prof.name, { v -> store.updateProfile { it.copy(name = v) } }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(prof.fitnessLevel, { v -> store.updateProfile { it.copy(fitnessLevel = v) } }, label = { Text("Level kebugaran (pemula/menengah/mahir)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField("Bangun", prof.wakeMin, Modifier.weight(1f)) { v -> store.updateProfile { it.copy(wakeMin = v) } }
                        TimeField("Tidur", prof.sleepMin, Modifier.weight(1f)) { v -> store.updateProfile { it.copy(sleepMin = v) } }
                        TimeField("Check-in", cfg.checkinMin, Modifier.weight(1f)) { v -> store.updateConfig { it.copy(checkinMin = v) } }
                    }
                    Text("Jendela fokus bawaan: 09:00-12:00 dan 14:00-16:00. Ubah nanti di Setelan.", style = MaterialTheme.typography.bodySmall)
                }
                3 -> {
                    ScreenTitle("Target pertama", "Target dengan deadline akan dikejar agent setiap hari.")
                    OutlinedTextField(goalTitle, { goalTitle = it }, label = { Text("Target (mis. Lulus ujian sertifikasi)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(goalDeadline, { goalDeadline = it }, label = { Text("Deadline YYYY-MM-DD (opsional)") }, singleLine = true, isError = !deadlineOk, modifier = Modifier.fillMaxWidth())
                    if (prof.goals.isNotEmpty()) Text("Sudah ada ${prof.goals.size} target tersimpan.", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    ScreenTitle("Aplikasi yang dibatasi", "Mulai dari yang paling menggoda. Jatah awal 30 menit per hari.")
                    Button(onClick = {
                        val n = addPresets(ctx, store)
                        android.widget.Toast.makeText(ctx, if (n > 0) "$n aplikasi ditambahkan." else "Tidak ada preset baru yang terpasang.", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("Tambah aplikasi populer yang terpasang") }
                    Text("${rules.size} aplikasi dibatasi. Atur detail di tab Aplikasi.")
                    Text("Setelah semua pas, nyalakan Mode Strict di Setelan agar tidak mudah dilonggarkan.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) OutlinedButton(onClick = { step-- }) { Text("Kembali") }
            Spacer(Modifier.weight(1f))
            if (step < last) {
                Button(enabled = step != 3 || deadlineOk, onClick = {
                    if (step == 3 && goalTitle.isNotBlank()) {
                        val g = Goal(UUID.randomUUID().toString(), goalTitle.trim(), goalDeadline.trim())
                        store.updateProfile { it.copy(goals = it.goals + g) }
                        goalTitle = ""; goalDeadline = ""
                    }
                    step++
                }) { Text("Lanjut") }
            } else {
                Button(onClick = { store.updateConfig { it.copy(onboarded = true) } }) { Text("Selesai & mulai") }
            }
        }
    }
}
