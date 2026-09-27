package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A small pill-shaped selectable label — shared visual language for chip-style pickers across screens. */
@Composable
fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** An icon-tile + title + description row with a trailing Switch — the wizard's default-behaviour row style. */
@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    icon: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            icon(Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = accent))
    }
}

/** A selectable radio-style card — used for the wizard's two-way "when a repeating task completes" choice. */
@Composable
fun RecurrenceOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    icon: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.White)
            .border(1.5.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            icon(Modifier.size(22.dp))
            RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = accent))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One selectable icon tile in the icon-picker grid. */
@Composable
fun IconTile(icon: CategoryIcon, selected: Boolean, onClick: () -> Unit, accent: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier.size(64.dp)) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 2.dp else 0.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CategoryIconGlyph(icon, if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(26.dp))
    }
}

/** A round back button with a hand-drawn chevron (not a Text glyph — a Unicode arrow character has baked-in whitespace that makes it render off-centre). */
@Composable
fun BackChevronButton(
    onClick: () -> Unit,
    background: Color = Color(0xFFE7E7EC),
    tint: Color = Color(0xFF2E2E38),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(11.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.8.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
            val chevron = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.66f, 0f)
                lineTo(size.width * 0.22f, size.height / 2f)
                lineTo(size.width * 0.66f, size.height)
            }
            drawPath(chevron, tint, style = stroke)
        }
    }
}

/** A short, hand-drawn forward-arrow — a Unicode "→" character has inconsistent baseline/weight across fonts and never quite lines up next to real text. */
@Composable
fun ArrowGlyph(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.8.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        val arrow = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height / 2f)
            lineTo(size.width * 0.82f, size.height / 2f)
            moveTo(size.width * 0.48f, size.height * 0.12f)
            lineTo(size.width * 0.86f, size.height / 2f)
            lineTo(size.width * 0.48f, size.height * 0.88f)
        }
        drawPath(arrow, tint, style = stroke)
    }
}

/** The wizard's primary pill button — always tinted with the category's own accent colour, not the app-wide theme colour, so it matches the live preview/selected swatch. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color.White,
            disabledContainerColor = accent.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.8f),
        ),
        modifier = modifier,
    ) {
        when {
            content != null -> content()
            showArrow -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.titleMedium)
                ArrowGlyph(tint = Color.White, modifier = Modifier.size(16.dp))
            }
            else -> Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
