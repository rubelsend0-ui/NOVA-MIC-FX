package com.nova.micfx.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.micfx.audio.DspMath
import com.nova.micfx.ui.theme.Danger
import com.nova.micfx.ui.theme.ElectricBlue
import com.nova.micfx.ui.theme.Navy
import com.nova.micfx.ui.theme.NavyLight
import com.nova.micfx.ui.theme.TextDim
import com.nova.micfx.ui.theme.Violet
import com.nova.micfx.ui.theme.Warn

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Navy.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(ElectricBlue.copy(alpha = 0.5f), Violet.copy(alpha = 0.25f))))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ElectricBlue)
}

@Composable
fun Dim(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = TextDim)
}

@Composable
fun LabeledSlider(
    label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit, enabled: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = ElectricBlue, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Color.Unspecified else TextDim)
            Dim(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Level meter driven by real linear amplitudes. Range -60 dBFS .. 0 dBFS. */
@Composable
fun LevelMeter(label: String, level: Float, peakHold: Float, clipping: Boolean) {
    fun norm(lin: Float) = ((DspMath.linearToDb(lin) + 60f) / 60f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(norm(level), tween(60), label = "meter")
    val hold by animateFloatAsState(norm(peakHold), tween(120), label = "hold")
    val db = DspMath.linearToDb(level)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(if (db <= -100f) "-inf dB" else "%.0f dB".format(db), style = MaterialTheme.typography.labelLarge,
                color = if (clipping) Danger else TextDim)
        }
        Box(Modifier.fillMaxWidth().height(16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                drawRoundRect(NavyLight, size = size, cornerRadius = CornerRadius(8f, 8f))
                val w = size.width * animated
                val color = when { clipping -> Brush.horizontalGradient(listOf(Danger, Danger))
                    animated > 0.92f -> Brush.horizontalGradient(listOf(ElectricBlue, Warn))
                    else -> Brush.horizontalGradient(listOf(ElectricBlue, Violet)) }
                if (w > 1f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = CornerRadius(8f, 8f))
                val hx = size.width * hold
                if (hx > 2f) drawLine(Color.White, Offset(hx, 0f), Offset(hx, size.height), strokeWidth = 3f)
            }
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(end = 6.dp)
    )
}
