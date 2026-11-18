package com.disiplinai.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.disiplinai.data.TimeWindow
import com.disiplinai.service.AdminReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val store = vm.store
    val cfg by store.config.collectAsState()
    val prof by store.profile.collectAsState()
    val locked = cfg.strictMode
    var showKey by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val adminComp = remember { ComponentName(ctx, AdminReceiver::class.java) }
    val dpm = remember { ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminActive = remember(tick) { dpm.isAdminActive(adminComp) }

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenTitle("Setelan") }
        item { PermissionsPanel(showAll = true) }

        item {
            SectionCard("Agent AI (Gemini)") {
                OutlinedTextField(
                    cfg.apiKey, { v -> store.updateConfig { it.copy(apiKey = v.trim()) } },
                    label = { Text("API key (Google AI Studio)") }, singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(showKey, { showKey = it }); Spacer(Modifier.width(8.dp)); Text("Tampilkan key")
                }
                Button(onClick = { vm.refreshModels() }, enabled = !vm.busy && cfg.apiKey.isNotBlank()) {
                    Text("Ambil daftar model")
                }
                Text("Model dipakai: ${cfg.model.ifBlank { "(otomatis)" }}", style = MaterialTheme.typography.labelLarge)
                if (cfg.modelCandidates.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { modelMenu = true }) { Text("Pilih model") }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            cfg.modelCandidates.forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = {
                                    store.updateConfig { it.copy(model = m) }; modelMenu = false
                                })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    cfg.model, { v -> store.updateConfig { it.copy(model = v.trim()) } },
                    label = { Text("Nama model (manual, opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Buat key gratis di aistudio.google.com/apikey. Daftar model diambil dari key-mu dan jika satu model " +
                        "gagal (dimatikan/kuota habis), app otomatis mencoba model lain dari daftar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { vm.testGemini() }, enabled = !vm.busy && cfg.apiKey.isNotBlank()) { Text("Tes koneksi") }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.info?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        item {
            SectionCard("Tampilan") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "Gelap", "light" to "Terang", "system" to "Ikut sistem").forEach { (k, l) ->
                        if (cfg.theme == k) Button(onClick = {}, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(l) }
                        else OutlinedButton(onClick = { store.updateConfig { it.copy(theme = k) } }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(l) }
                    }
                }
            }
        }

        item {
            SectionCard("Profil") {
                OutlinedTextField(prof.name, { v -> store.updateProfile { it.copy(name = v) } }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(prof.fitnessLevel, { v -> store.updateProfile { it.copy(fitnessLevel = v) } }, label = { Text("Level kebugaran (pemula/menengah/mahir)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(prof.equipment, { v -> store.updateProfile { it.copy(equipment = v) } }, label = { Text("Alat olahraga yang ada") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(prof.notes, { v -> store.updateProfile { it.copy(notes = v) } }, label = { Text("Catatan (cedera, jadwal kuliah/kerja, dll)") }, maxLines = 5, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            SectionCard("Jadwal harian") {
                if (locked) Text("Terkunci karena Mode Strict aktif. Mematikan Mode Strict butuh jeda ${cfg.loosenDelayMin} menit.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("Bangun", prof.wakeMin, Modifier.weight(1f), enabled = !locked) { v -> store.updateProfile { it.copy(wakeMin = v) } }
                    TimeField("Tidur", prof.sleepMin, Modifier.weight(1f), enabled = !locked) { v -> store.updateProfile { it.copy(sleepMin = v) } }
                    TimeField("Check-in", cfg.checkinMin, Modifier.weight(1f), enabled = !locked) { v -> store.updateConfig { it.copy(checkinMin = v) } }
                }
                Text("Jendela fokus (aplikasi terbatas diblokir)", fontWeight = FontWeight.SemiBold)
                prof.focusWindows.forEachIndexed { idx, w ->
                    key(idx) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimeField("Mulai", w.startMin, Modifier.weight(1f), enabled = !locked) { v ->
                                store.updateProfile { p -> p.copy(focusWindows = p.focusWindows.mapIndexed { i, x -> if (i == idx) x.copy(startMin = v) else x }) }
                            }
                            TimeField("Selesai", w.endMin, Modifier.weight(1f), enabled = !locked) { v ->
                                store.updateProfile { p -> p.copy(focusWindows = p.focusWindows.mapIndexed { i, x -> if (i == idx) x.copy(endMin = v) else x }) }
                            }
                            IconButton(enabled = !locked, onClick = {
                                store.updateProfile { p -> p.copy(focusWindows = p.focusWindows.filterIndexed { i, _ -> i != idx }) }
                            }) { Icon(Icons.Filled.Delete, contentDescription = "Hapus") }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    store.updateProfile { it.copy(focusWindows = it.focusWindows + TimeWindow(19 * 60, 20 * 60, "Fokus")) }
                }) { Text("+ Jendela fokus") }
            }
        }

        item {
            SectionCard("Mode disiplin") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = cfg.strictMode, onCheckedChange = { on ->
                        if (on) store.updateConfig { it.copy(strictMode = true, strictOffAt = 0L) } else store.requestStrictOff()
                    })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Mode Strict", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Mengunci setelan, menunda pelonggaran, sesi fokus tak bisa dibatalkan, dan menutup halaman pengaturan Disiplin AI saat fokus/tidur.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (cfg.strictMode && cfg.strictOffAt > 0) {
                    val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cfg.strictOffAt))
                    Text("Mode Strict akan mati jam $t.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { store.cancelStrictOff() }) { Text("Batalkan, tetap strict") }
                }
                if (locked) Text("Parameter di bawah terkunci selama Mode Strict aktif.", style = MaterialTheme.typography.bodySmall)
                NumberField("Jeda pelonggaran (menit)", cfg.loosenDelayMin, Modifier.fillMaxWidth(), enabled = !locked) { v -> store.updateConfig { it.copy(loosenDelayMin = v.coerceAtLeast(5)) } }
                NumberField("Penalti jatah per hari gagal (%)", cfg.penaltyPerStrikePct, Modifier.fillMaxWidth(), enabled = !locked) { v -> store.updateConfig { it.copy(penaltyPerStrikePct = v.coerceIn(0, 100)) } }
                NumberField("Penalti maksimum (%)", cfg.maxPenaltyPct, Modifier.fillMaxWidth(), enabled = !locked) { v -> store.updateConfig { it.copy(maxPenaltyPct = v.coerceIn(0, 95)) } }
                NumberField("Syarat hari berhasil (% tugas selesai)", cfg.successThresholdPct, Modifier.fillMaxWidth(), enabled = !locked) { v -> store.updateConfig { it.copy(successThresholdPct = v.coerceIn(1, 100)) } }
            }
        }

        item {
            SectionCard("Perlindungan uninstall") {
                Text(
                    if (adminActive) "Aktif: aplikasi tidak bisa di-uninstall sebelum admin perangkat dinonaktifkan dulu."
                    else "Aktifkan admin perangkat agar uninstall butuh langkah ekstra. Ini tidak memberi app akses ke datamu.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!adminActive) {
                    Button(onClick = {
                        try {
                            ctx.startActivity(
                                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Mempersulit penghapusan Disiplin AI saat kamu sedang malas.")
                            )
                        } catch (e: Exception) { /* ROM tidak mendukung */ }
                    }) { Text("Aktifkan") }
                } else if (!locked) {
                    OutlinedButton(onClick = { dpm.removeActiveAdmin(adminComp); tick++ }) { Text("Nonaktifkan") }
                } else {
                    Text("Mode Strict aktif: matikan dulu untuk menonaktifkan perlindungan ini.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            SectionCard("Data & backup") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportL.launch("disiplin-ai-backup.json") }) { Text("Ekspor") }
                    OutlinedButton(enabled = !locked, onClick = { importL.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Pulihkan") }
                    OutlinedButton(onClick = { store.clearChat() }) { Text("Hapus chat") }
                }
                Text(
                    "Backup berisi profil, aturan, rencana, riwayat, dan kebiasaan. API key tidak ikut diekspor. " +
                        "Semua data tersimpan lokal. Hanya konteks yang dibutuhkan agent dikirim ke Google Gemini saat fitur AI dipakai.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
