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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate

private val ClashAccent = Color(0xFFE08A1E)

/**
 * One new calendar event and everything in the plan it lands on, with a single decision for all of
 * it — rather than one row per task (a lunch landing on a Cleaning block used to raise a separate
 * card for every task inside it). Shared by Today and Calendar, mobile and web.
 */
@Composable
internal fun ClashCard(clash: CalendarClash, resolving: Boolean, onResolve: (ClashAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(ClashAccent.copy(alpha = 0.08f))
            .border(1.dp, ClashAccent.copy(alpha = 0.35f), cardShape())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "NEW IN YOUR CALENDAR",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ClashAccent,
            )
            Text(clash.eventTitle, style = titleStyle())
            Text(
                "${clashDayLabel(clash.date)} · ${clash.startTime.toShortTimeOrSelf()}–${clash.endTime.toShortTimeOrSelf()}",
                style = secondaryStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Clashes with", style = secondaryStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            clash.items.forEach { item ->
                val color = item.categoryColor?.toColorOrNull() ?: MaterialTheme.colorScheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(item.label, style = primaryStyle().copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        listOfNotNull(
                            "${item.startTime.toShortTimeOrSelf()}–${item.endTime.toShortTimeOrSelf()}",
                            item.taskCount.takeIf { it > 0 }?.let { if (it == 1) "1 task" else "$it tasks" },
                        ).joinToString(" · "),
                        style = secondaryStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (resolving) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ClashAccent, strokeWidth = 2.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClashButton("Move", filled = true) { onResolve(ClashAction.MOVE) }
                ClashButton("Remove", filled = false) { onResolve(ClashAction.REMOVE) }
                ClashButton("Keep both", filled = false) { onResolve(ClashAction.KEEP) }
            }
            Text(
                "Move re-plans it into free time that day · Remove takes it off the plan · Keep both leaves it as is",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "How should we plan around this all-day event?" — asked once per event (once per series for a
 * repeating one). Until answered, the day keeps its commitments and gets nothing else.
 */
@Composable
internal fun AllDayPromptCard(prompt: AllDayPrompt, resolving: Boolean, onChoose: (AllDayChoice) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.25f), cardShape())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ALL-DAY EVENT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accent)
            Text(prompt.title, style = titleStyle())
            Text(
                if (prompt.firstDate == prompt.lastDate) clashDayLabel(prompt.firstDate)
                else "${clashDayLabel(prompt.firstDate)} – ${clashDayLabel(prompt.lastDate)}",
                style = secondaryStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Is this a day off, or should your usual commitments still happen?",
            style = primaryStyle(),
        )
        if (resolving) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = accent, strokeWidth = 2.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Day off", filled = true, accent = accent) { onChoose(AllDayChoice.DAY_OFF) }
                PillButton("Keep my commitments", filled = false, accent = accent) { onChoose(AllDayChoice.KEEP_COMMITMENTS) }
            }
            Text(
                "Day off clears commitments and tasks · Keep my commitments plans nothing extra that day",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClashButton(label: String, filled: Boolean, onClick: () -> Unit) = PillButton(label, filled, ClashAccent, onClick)

@Composable
private fun PillButton(label: String, filled: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = if (filled) Color.White else accent,
        modifier = Modifier
            .clip(pillShape())
            .background(if (filled) accent else Color.Transparent)
            .border(1.dp, accent, pillShape())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun clashDayLabel(isoDate: String): String = runCatching {
    val date = LocalDate.parse(isoDate)
    val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    "$day ${date.day} $month"
}.getOrDefault(isoDate)

/** Square on web (the reference design has no rounded corners), rounded on the phone. */
@Composable
private fun cardShape(): androidx.compose.ui.graphics.Shape = if (LocalWebStyle.current) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(16.dp)

@Composable
private fun pillShape(): androidx.compose.ui.graphics.Shape = if (LocalWebStyle.current) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(50)

// These cards sit in Today's side column, so they use its text styles (14 heading / 14 / 12).
@Composable
private fun titleStyle(): androidx.compose.ui.text.TextStyle =
    if (LocalWebStyle.current) androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

@Composable
private fun primaryStyle(): androidx.compose.ui.text.TextStyle =
    if (LocalWebStyle.current) androidx.compose.ui.text.TextStyle(fontSize = 14.sp) else MaterialTheme.typography.bodyMedium

@Composable
private fun secondaryStyle(): androidx.compose.ui.text.TextStyle =
    if (LocalWebStyle.current) androidx.compose.ui.text.TextStyle(fontSize = 12.sp) else MaterialTheme.typography.bodySmall
