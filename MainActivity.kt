package com.disiplinai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.disiplinai.data.Store

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val cfg by Store.get(LocalContext.current).config.collectAsState()
            DisiplinTheme(cfg.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppRoot() }
            }
        }
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val cfg by vm.store.config.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.onAppOpen() }

    if (!cfg.onboarded) {
        OnboardingScreen(vm)
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val items = listOf(
                    "Hari Ini" to Icons.Filled.Today,
                    "Agent" to Icons.Filled.SmartToy,
                    "Aplikasi" to Icons.Filled.Apps,
                    "Progres" to Icons.Filled.Insights,
                    "Setelan" to Icons.Filled.Settings
                )
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(vm, onOpenAgent = { tab = 1 })
                1 -> AgentScreen(vm)
                2 -> AppsScreen(vm)
                3 -> ProgressScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
