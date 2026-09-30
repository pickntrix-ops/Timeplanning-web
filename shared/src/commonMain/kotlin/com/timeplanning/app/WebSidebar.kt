package com.timeplanning.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun BottomTab.sidebarGlyph(): WizardGlyph = when (this) {
    BottomTab.TODAY -> WizardGlyph.CHECK_CIRCLE
    BottomTab.CALENDAR -> WizardGlyph.CALENDAR
    BottomTab.PLAN -> WizardGlyph.CLOCK
    BottomTab.CATEGORIES -> WizardGlyph.LIST
    BottomTab.ACCOUNT -> WizardGlyph.PERSON
}

/** A category's colour softened to the pastel the web calendar and sidebar use. */
internal fun Color.webPastel(): Color = lerp(Color.White, this, 0.42f)

/**
 * The web app's persistent left navigation, after the reference design: app name with a menu
 * button (collapses the sidebar to an icon rail), the signed-in person, a square "New task" box,
 * full-width section rows (the selected one filled in WebAccent), a collapsible Categories list of
 * outlined swatches (shortcuts into each; "+" adds one), and an "Other" key for the calendar's
 * non-category colours. Wraps every signed-in web page — see App.kt.
 */
@Composable
fun WebSidebar(
    apiClient: ApiClient,
    sessionToken: String,
    current: BottomTab?,
    refreshKey: Int,
    onSelect: (BottomTab) -> Unit,
    onNewTask: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onAddCategory: () -> Unit,
    onSignOut: () -> Unit,
) {
    var categories by remember { mutableStateOf<List<TaskCategory>>(emptyList()) }
    var me by remember { mutableStateOf<Me?>(null) }
    var collapsed by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(true) }
    var profileMenu by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) {
        runCatching { apiClient.fetchTaskCategories(sessionToken) }.onSuccess { categories = it.sortedBy { c -> c.name.lowercase() } }
    }
    LaunchedEffect(Unit) { me = runCatching { apiClient.fetchMe(sessionToken) }.getOrNull() }
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val width = if (collapsed) 76.dp else 280.dp

    Row(Modifier.fillMaxHeight()) {
        Column(Modifier.width(width).fillMaxHeight().background(WebSidebarBackground).verticalScroll(rememberScrollState())) {
            // Name + menu (collapse) button.
            Row(
                modifier = Modifier.fillMaxWidth().height(88.dp).padding(horizontal = if (collapsed) 0.dp else 28.dp),
                horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!collapsed) Text("TimePlanning", style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), color = ink)
                Box(Modifier.size(36.dp).clickable { collapsed = !collapsed }, contentAlignment = Alignment.Center) { MenuIcon(ink) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))

            // Who's signed in.
            val name = me?.displayName ?: me?.email?.substringBefore("@") ?: ""
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { profileMenu = true }.padding(horizontal = if (collapsed) 16.dp else 28.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(WebAccent), contentAlignment = Alignment.Center) {
                        Text(initials(name), color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    if (!collapsed) {
                        Column(Modifier.weight(1f)) {
                            Text(name.ifBlank { "Signed in" }, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(me?.email ?: "Google account", style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        WizardIconGlyph(WizardGlyph.CHEVRON, ink, Modifier.size(14.dp))
                    }
                }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }, containerColor = Color.White) {
                    MenuRow("Account") { profileMenu = false; onSelect(BottomTab.ACCOUNT) }
                    MenuRow("Sign out") { profileMenu = false; onSignOut() }
                }
            }

            // New task — a plain square box, like the reference's "New event".
            Row(
                modifier = Modifier
                    .padding(horizontal = if (collapsed) 12.dp else 24.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.dp, hairline)
                    .clickable(onClick = onNewTask)
                    .padding(horizontal = if (collapsed) 0.dp else 18.dp, vertical = 16.dp),
                horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp).border(1.5.dp, ink, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                    WizardIconGlyph(WizardGlyph.PLUS, ink, Modifier.size(11.dp))
                }
                if (!collapsed) Text("New task", style = MaterialTheme.typography.bodyLarge, color = ink)
            }

            // Sections — full width, the selected one a solid accent bar.
            BottomTab.entries.forEach { tab ->
                val selected = tab == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) WebAccentSoft else Color.Transparent)
                        .clickable { onSelect(tab) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).height(62.dp).background(if (selected) WebAccent else Color.Transparent))
                    Row(
                        modifier = Modifier.weight(1f).padding(start = if (collapsed) 0.dp else 32.dp),
                        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WizardIconGlyph(tab.sidebarGlyph(), ink, Modifier.size(22.dp))
                        if (!collapsed) Text(tab.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal), color = ink)
                    }
                }
            }

            if (!collapsed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp).clickable { categoriesOpen = !categoriesOpen }.padding(start = 36.dp, end = 28.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Categories", style = MaterialTheme.typography.bodyLarge, color = ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clickable(onClick = onAddCategory), contentAlignment = Alignment.Center) {
                            WizardIconGlyph(WizardGlyph.PLUS, ink, Modifier.size(13.dp))
                        }
                        WizardIconGlyph(WizardGlyph.CHEVRON, ink, Modifier.size(14.dp).rotate(if (categoriesOpen) 0f else -90f))
                    }
                }
                if (categoriesOpen) {
                    categories.forEach { category ->
                        val color = (category.displayColor.toColorOrNull() ?: muted).webPastel()
                        LegendRow(category.name, color, onClick = { onOpenCategory(category.id) })
                    }
                }
                Text("Other", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(start = 36.dp, top = 20.dp, bottom = 4.dp))
                LegendRow("Commitments", WebCommitmentColor, onClick = null)
                LegendRow("Calendar events", WebCalendarEventColor, onClick = null)
                Box(Modifier.height(24.dp))
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(hairline))
    }
}

/** A small filled pastel square — the same colour the category's blocks use on the calendar. */
@Composable
private fun LegendRow(label: String, color: Color, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 36.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(180.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp))
}

/** Two-line "hamburger" menu glyph (the reference design's), drawn since WizardGlyph has none. */
@Composable
private fun MenuIcon(color: Color) {
    Canvas(Modifier.size(22.dp, 14.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(0f, size.height * 0.2f), Offset(size.width, size.height * 0.2f), strokeWidth = stroke)
        drawLine(color, Offset(0f, size.height * 0.8f), Offset(size.width, size.height * 0.8f), strokeWidth = stroke)
    }
}

private fun initials(name: String): String =
    name.split(" ", ".", "_").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "•" }
