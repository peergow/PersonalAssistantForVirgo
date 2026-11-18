package com.disiplinai.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.disiplinai.data.Store
import com.disiplinai.logic.Util
import kotlinx.coroutines.delay

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg").orEmpty()
        val reason = intent.getStringExtra("reason").orEmpty()
        val freeAt = intent.getLongExtra("freeAt", 0L)
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
        setContent {
            val cfg by Store.get(LocalContext.current).config.collectAsState()
            DisiplinTheme(cfg.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BlockScreen(label, reason, freeAt, onHome = { goHome() }, onAgent = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    })
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun BlockScreen(label: String, reason: String, freeAt: Long, onHome: () -> Unit, onAgent: () -> Unit) {
    val ctx = LocalContext.current
    val store = Store.get(ctx)
    val nowMin = Util.nowMin()
    val next = store.tasksFor(Util.today()).firstOrNull { !it.done && it.timeMin + it.durationMin >= nowMin }
        ?: store.tasksFor(Util.today()).firstOrNull { !it.done }
    val goal = store.profile.value.goals.firstOrNull()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val remain = if (freeAt > now) (freeAt - now) / 1000 else 0L

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("$label diblokir", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(reason, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (remain > 0) {
            Spacer(Modifier.height(16.dp))
            val h = remain / 3600; val m = (remain % 3600) / 60; val s = remain % 60
            Text(
                if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s),
                style = MaterialTheme.typography.displaySmall
            )
            Text("sampai bebas lagi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        if (next != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kerjakan ini sekarang", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("${Util.fmt(next.timeMin)} · ${next.title}", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (goal != null) {
            Text("Targetmu: ${goal.title}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            val left = if (goal.deadline.isNotBlank()) Util.daysUntil(goal.deadline) else null
            if (left != null && left >= 0) Text("Sisa $left hari", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke Home") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAgent, modifier = Modifier.fillMaxWidth()) { Text("Mulai tugas berikutnya") }
    }
}
