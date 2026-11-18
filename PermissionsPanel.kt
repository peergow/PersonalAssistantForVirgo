package com.disiplinai.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.disiplinai.logic.Perms

/** Daftar izin. [showAll] = tampilkan juga yang sudah beres (untuk layar Setelan). */
@Composable
fun PermissionsPanel(showAll: Boolean) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val items = remember(tick) { Perms.status(ctx) }
    val shown = if (showAll) items else items.filter { !it.ok }
    if (shown.isEmpty()) return

    val allRequiredOk = items.filter { it.required }.all { it.ok }
    SectionCard(if (allRequiredOk) "Izin" else "Izin yang masih kurang") {
        if (!allRequiredOk) {
            Text(
                "Tanpa dua izin wajib, pemblokiran tidak bekerja.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium
            )
        }
        shown.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (p.ok) "✓ " else "✗ ") + p.title + if (p.required) " (wajib)" else "", fontWeight = FontWeight.SemiBold)
                    Text(p.desc, style = MaterialTheme.typography.bodySmall)
                }
                if (!p.ok) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (p.id == "notif" && Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            try { ctx.startActivity(p.intent) } catch (e: Exception) {
                                Toast.makeText(ctx, "Buka pengaturan ini secara manual.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("Atur") }
                }
            }
        }
    }
}
