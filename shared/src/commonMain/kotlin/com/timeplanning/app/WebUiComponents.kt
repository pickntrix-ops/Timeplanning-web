package com.timeplanning.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Desktop-web-only building blocks for the categories section — genuinely
 * different composables from the mobile screens (not the mobile layout
 * squeezed into a centered panel, see WebTheme.kt's earlier "same screens,
 * web styling" approach), reused across WebCategoriesScreen,
 * WebCategoryFormScreen, WebAddTaskScreen and WebTaskDetailScreen.
 */

/** A slim horizontal top bar replacing the mobile bottom tab bar — a tab row reads as native on a wide desktop page, a bottom bar reads as a phone. */
@Composable
fun WebTopNav(currentTab: BottomTab, onSelectTab: (BottomTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(0.dp, Color.Transparent)
            .padding(horizontal = 32.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TimePlanning", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BottomTab.entries.forEach { tab ->
                val selected = tab == currentTab
                Text(
                    tab.label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/**
 * A full-bleed page with a centered, wide card — the desktop-web equivalent
 * of the mobile full-screen forms (AddTaskScreen/CategoryFormScreen's own
 * header+scroll+pinned-button shape), used for anything reached as a
 * distinct action (create/edit category, add/edit task, task detail) rather
 * than the persistent master-detail browsing shell.
 *
 * The primary action (Save/Create/etc.) lives in the header, next to Close —
 * not pinned below the content. A bottom-pinned button only stays put if
 * the card's own height is bounded so its scrollable content can't just grow
 * past the viewport; card height wasn't bounded here, so on a long form (e.g.
 * a category with several subcategories) the button used to scroll off the
 * bottom of the page with no way to reach it. The header is always visible
 * regardless of content length, so this sidesteps that entirely.
 */
@Composable
fun WebPageShell(
    title: String,
    onClose: () -> Unit,
    maxWidth: Dp = 720.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val verticalPadding = 40.dp
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(vertical = verticalPadding)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .heightIn(max = maxHeight - verticalPadding * 2)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    actions()
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { WizardIconGlyph(WizardGlyph.CLOSE, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(14.dp)) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) { content() }
        }
    }
}

/** A section title, consistent across every web form — same weight/size as the mobile FormSection but no mobile-only spacing assumptions. */
@Composable
fun WebSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
}

/** Outlined-field colours tuned to an accent, shared by every web form's text fields. */
@Composable
fun webFieldColors(accent: Color): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = accent,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    cursorColor = accent,
)

/** A pill choice — tinted with the accent when selected, white outlined otherwise. Shared by every web form/pane's chip pickers. */
@Composable
fun WebChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.White)
            .border(1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** A white, outlined rounded card — the base container reused by sidebar rows, pane cards, and settings rows across the web categories section. */
@Composable
fun WebCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}
