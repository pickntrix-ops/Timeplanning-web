package com.timeplanning.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * A tap-to-open date picker instead of typing "YYYY-MM-DD" by hand. The
 * OutlinedTextField stays read-only (no keyboard, no cursor) — a
 * transparent Box on top catches the tap and opens the real picker, the
 * standard way to make a read-only Compose text field feel clickable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateField(value: String?, onValueChange: (String?) -> Unit, label: String, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value ?: "No date",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Invisible click-catcher the size of the field above — a read-only
        // OutlinedTextField still consumes taps for cursor/focus itself, so
        // this is the standard way to make one open a picker instead.
        Box(modifier = Modifier.matchParentSize().zIndex(1f).clickable { showDialog = true })
    }

    if (showDialog) {
        val initialMillis = value?.let { LocalDate.parse(it).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    onValueChange(millis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString() })
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        ) {
            Column {
                DatePicker(state = state)
                if (value != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { onValueChange(null); showDialog = false },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) { Text("Clear date") }
                    }
                }
            }
        }
    }
}
