package com.traceme.app.ui

import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.traceme.app.util.rememberSliderTicker
import kotlin.math.roundToInt

fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

fun formatDay(timestamp: Long): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(timestamp))

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun relativeDuration(millis: Long): String = when {
    millis < 5_000L -> "just now"
    millis < 60_000L -> "${millis / 1000L}s ago"
    millis < 3_600_000L -> "${millis / 60_000L} min ago"
    else -> "${millis / 3_600_000L} h ago"
}

/** "180 m" → "180 m", "1240 m" → "1.2 km". */
fun formatDistance(meters: Int): String {
    return if (meters < 1000) {
        "$meters m"
    } else {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    }
}

/** A gentle lift for flat brand colours, so solid buttons still look lit. */
fun lift(color: Color, amount: Float = 0.14f): Brush =
    Brush.linearGradient(listOf(lerp(color, Color.White, amount), lerp(color, Color.Black, 0.10f)))

/* ------------------------------------------------------------------ *
 * Surfaces
 * ------------------------------------------------------------------ */

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    container: Color = CardWarm,
    brush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val fill: Brush = brush ?: SolidColor(container)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = CardShape,
                ambientColor = Color(0x1AC49A72),
                spotColor = Color(0x26C49A72)
            )
            .clip(CardShape)
            .background(fill)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color(0x55FFFFFF), Color(0x11FFFFFF))
                ),
                shape = CardShape
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}


@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Ink
    )
}

/** Small caps eyebrow with a hairline rule running to the right. */
@Composable
fun SectionHeader(
    text: String,
    accent: Color = Clay,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0f))
                    )
                )
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/* ------------------------------------------------------------------ *
 * Buttons
 * ------------------------------------------------------------------ */

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = Clay,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val brush = remember(container) { lift(container, 0.16f) }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(
                elevation = if (enabled) 16.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = container.copy(alpha = 0.5f),
                spotColor = container.copy(alpha = 0.55f)
            )
            .background(if (enabled) brush else SolidColor(container.copy(alpha = 0.4f)), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor.copy(alpha = 0.7f)
        ),
        elevation = null
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun QuietButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Glass,
            contentColor = Ink
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineWarm)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Clay)
        Spacer(Modifier.width(9.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A compact pill-shaped button used inside rows and map overlays. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    accent: Color = Clay,
    onClick: () -> Unit
) {
    val shape = CircleShape
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            elevation = null,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Glass,
                contentColor = accent
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) { content() }
    }
}

/* ------------------------------------------------------------------ *
 * Gauges and readouts
 * ------------------------------------------------------------------ */

@Composable
fun StillnessGauge(
    progress: Float,
    centerTitle: String,
    centerSubtitle: String,
    progressColor: Color,
    trackColor: Color = Peach,
    diameter: Dp = 200.dp
) {
    val sweep = remember(progressColor) {
        Brush.sweepGradient(
            listOf(
                lerp(progressColor, Color.White, 0.35f),
                progressColor,
                lerp(progressColor, Color.Black, 0.18f)
            )
        )
    }
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val strokeWidth = 13.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

            // track
            drawArc(
                color = trackColor.copy(alpha = 0.55f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // glow behind the live arc
            if (progress > 0.001f) {
                drawArc(
                    color = progressColor.copy(alpha = 0.22f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth + 10.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    brush = sweep,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerTitle,
                style = MaterialTheme.typography.headlineLarge,
                color = Ink,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                centerSubtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint
            )
        }
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkFaint)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------------------------------------------------------ *
 * The slider used for every timing setting
 * ------------------------------------------------------------------ */

/**
 * A slider that lands on exact [step] increments and gives a haptic tap plus a
 * click for every increment crossed — in both directions.
 */
@Composable
fun StepSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    format: (Int) -> String,
    modifier: Modifier = Modifier,
    hint: String = "",
    onChange: (Int) -> Unit
) {
    val ticker = rememberSliderTicker()

    val maxIndex = (((range.last - range.first).toFloat() / step).roundToInt()).coerceAtLeast(1)
    val index = (((value - range.first).toFloat() / step).roundToInt()).coerceIn(0, maxIndex)
    // The thumb follows the finger immediately, even before the caller has
    // stored the new value, so no step is ever swallowed mid-drag.
    var dragIndex by remember(value) { mutableStateOf(index) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                format(value),
                style = MaterialTheme.typography.titleMedium,
                color = Clay,
                fontWeight = FontWeight.Bold
            )
        }
        if (hint.isNotBlank()) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        }
        Slider(
            value = dragIndex.toFloat(),
            onValueChange = { raw ->
                val next = raw.roundToInt().coerceIn(0, maxIndex)
                if (next != dragIndex) {
                    // Count the notches so a fast flick still clicks once per step.
                    val crossed = kotlin.math.abs(next - dragIndex)
                    dragIndex = next
                    ticker.tick(crossed)
                    onChange(range.first + next * step)
                }
            },
            valueRange = 0f..maxIndex.toFloat(),
            steps = (maxIndex - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Clay,
                activeTrackColor = Peach,
                inactiveTrackColor = OutlineWarm.copy(alpha = 0.75f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                format(range.first),
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint
            )
            Spacer(Modifier.weight(1f))
            Text(
                format(range.last),
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint
            )
        }
    }
}
