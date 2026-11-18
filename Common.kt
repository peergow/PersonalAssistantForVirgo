package com.disiplinai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.disiplinai.logic.Util

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProgressRing(progress: Float, label: String, diameter: Dp = 96.dp, stroke: Dp = 10.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = when {
        progress >= 0.7f -> Brand.Success
        progress >= 0.4f -> Brand.Warning
        else -> MaterialTheme.colorScheme.primary
    }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, topLeft = tl, size = arcSize, style = Stroke(s))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft = tl, size = arcSize,
                style = Stroke(s, cap = StrokeCap.Round))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TimeField(
    label: String, minutes: Int, modifier: Modifier = Modifier,
    enabled: Boolean = true, onValid: (Int) -> Unit
) {
    var text by remember { mutableStateOf(Util.fmt(minutes)) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; Util.parseTime(t)?.let(onValid) },
        label = { Text(label) }, singleLine = true, enabled = enabled,
        isError = Util.parseTime(text) == null,
        modifier = modifier
    )
}

@Composable
fun NumberField(
    label: String, value: Int, modifier: Modifier = Modifier,
    enabled: Boolean = true, onValid: (Int) -> Unit
) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t.filter { it.isDigit() }.take(4); text.toIntOrNull()?.let(onValid) },
        label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
