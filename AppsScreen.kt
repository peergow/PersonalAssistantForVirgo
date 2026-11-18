package com.disiplinai.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disiplinai.data.AppRule
import com.disiplinai.data.Store
import com.disiplinai.logic.PolicyEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PRESETS = listOf(
    "com.instagram.android" to "Instagram",
    "com.zhiliaoapp.musically" to "TikTok",
    "com.ss.android.ugc.trill" to "TikTok",
    "com.google.android.youtube" to "YouTube",
    "com.facebook.katana" to "Facebook",
    "com.twitter.android" to "X",
    "com.netflix.mediaclient" to "Netflix",
    "com.mobile.legends" to "Mobile Legends",
    "com.dts.freefireth" to "Free Fire",
    "com.tencent.ig" to "PUBG Mobile",
    "com.shopee.id" to "Shopee",
    "com.tokopedia.tkpd" to "Tokopedia",
    "com.reddit.frontpage" to "Reddit"
)

/** Tambahkan preset yang terpasang (jatah 30 mnt). Mengembalikan jumlah yang ditambahkan. */
fun addPresets(ctx: Context, store: Store): Int {
    val pm = ctx.packageManager
    var n = 0
    PRESETS.forEach { (pkg, label) ->
        if (pm.getLaunchIntentForPackage(pkg) != null && store.rules.value.none { it.pkg == pkg }) {
            store.requestRuleChange(AppRule(pkg, label, 30)); n++
        }
    }
    return n
}

@Composable
fun AppsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val store = vm.store
    val rules by store.rules.collectAsState()
    val pending by store.pending.collectAsState()
    val cfg by store.config.collectAsState()
    val progress by store.progress.collectAsState()
    var editing by remember { mutableStateOf<AppRule?>(null) }
    var picking by remember { mutableStateOf(false) }
    val penalty = PolicyEngine.penaltyPct(cfg, progress)

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle(
                "Aplikasi",
                if (penalty > 0) "Penalti aktif: semua jatah dipotong $penalty% karena hari gagal beruntun."
                else "Jatah penuh. Gagal memenuhi rencana akan memotong jatah."
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.loadInstalled(); picking = true }) { Text("Tambah aplikasi") }
                OutlinedButton(onClick = {
                    val n = addPresets(ctx, store)
                    Toast.makeText(ctx, if (n > 0) "$n aplikasi ditambahkan (30 mnt/hari)." else "Tidak ada preset baru yang terpasang.", Toast.LENGTH_SHORT).show()
                }) { Text("Preset populer") }
            }
        }
        if (rules.isEmpty()) item { Text("Belum ada aplikasi yang dibatasi.") }
        items(rules, key = { it.pkg }) { r ->
            val used = vm.usage[r.pkg] ?: 0
            val limit = PolicyEngine.effectiveLimit(r, cfg, progress)
            val p = pending.firstOrNull { it.pkg == r.pkg }
            Card(Modifier.fillMaxWidth().clickable { editing = r }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.label, fontWeight = FontWeight.SemiBold)
                        Text(if (limit == 0) "diblokir total" else "$used / $limit mnt")
                    }
                    if (limit > 0) LinearProgressIndicator(progress = { (used.toFloat() / limit).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Dasar ${r.dailyLimitMin} mnt · fokus ${if (r.blockInFocus) "diblokir" else "bebas"} · tidur ${if (r.blockAtSleep) "diblokir" else "bebas"}" +
                            if (r.requireCategory.isNotBlank()) " · buka setelah \"${r.requireCategory}\"" else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (p != null) {
                        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.effectiveAt))
                        Text(
                            (if (p.newRule == null) "Penghapusan" else "Pelonggaran") + " menunggu, berlaku jam $t",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    if (picking) {
        val apps = vm.installed
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Pilih aplikasi") },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, placeholder = { Text("Cari...") }, singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(apps.filter { it.second.contains(q, true) }, key = { it.first }) { (pkg, label) ->
                            Text(
                                label,
                                Modifier.fillMaxWidth().clickable {
                                    picking = false
                                    editing = rules.firstOrNull { it.pkg == pkg } ?: AppRule(pkg, label, 30)
                                }.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Tutup") } }
        )
    }

    editing?.let { rule ->
        val exists = rules.any { it.pkg == rule.pkg }
        var limit by remember(rule.pkg) { mutableIntStateOf(rule.dailyLimitMin) }
        var focus by remember(rule.pkg) { mutableStateOf(rule.blockInFocus) }
        var sleep by remember(rule.pkg) { mutableStateOf(rule.blockAtSleep) }
        var req by remember(rule.pkg) { mutableStateOf(rule.requireCategory) }
        val hasPending = pending.any { it.pkg == rule.pkg }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(rule.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Jatah harian (menit, 0 = blokir total)", limit, Modifier.fillMaxWidth()) { limit = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(focus, { focus = it }); Spacer(Modifier.width(8.dp)); Text("Blokir saat jendela fokus")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(sleep, { sleep = it }); Spacer(Modifier.width(8.dp)); Text("Blokir saat jam tidur")
                    }
                    Text("Kunci sampai tugas selesai:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("olahraga", "belajar", "kerja").forEach { c ->
                            if (req == c) Button(onClick = { req = "" }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(c, style = MaterialTheme.typography.labelMedium) }
                            else OutlinedButton(onClick = { req = c }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(c, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    if (cfg.strictMode && exists) {
                        Text(
                            "Mode strict: memperbesar jatah, melepas blokir/kunci, atau menghapus baru berlaku setelah ${cfg.loosenDelayMin} menit.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (hasPending) TextButton(onClick = { store.cancelPending(rule.pkg); editing = null }) { Text("Batalkan perubahan yang menunggu") }
                    if (exists) TextButton(onClick = {
                        val now = store.requestRemove(rule.pkg)
                        Toast.makeText(ctx, if (now) "Dihapus." else "Penghapusan berlaku ${cfg.loosenDelayMin} menit lagi.", Toast.LENGTH_LONG).show()
                        editing = null
                    }) { Text("Hapus aturan", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val applied = store.requestRuleChange(
                        rule.copy(dailyLimitMin = limit, blockInFocus = focus, blockAtSleep = sleep, requireCategory = req)
                    )
                    Toast.makeText(
                        ctx, if (applied) "Tersimpan." else "Pelonggaran ditunda ${cfg.loosenDelayMin} menit.", Toast.LENGTH_LONG
                    ).show()
                    editing = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } }
        )
    }
}
