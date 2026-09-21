package com.timeplanning.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(apiClient: ApiClient, sessionToken: String, onDone: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf(TaskType.GENERAL) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var durationMinutes by remember { mutableStateOf("30") }
    var dueDate by remember { mutableStateOf("") }
    var subcategory by remember { mutableStateOf<GeneralSubcategory?>(null) }
    var subcategoryMenuExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.safeContentPadding().fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add task", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(expanded = typeMenuExpanded, onExpandedChange = { typeMenuExpanded = it }) {
            OutlinedTextField(
                value = taskType.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                TaskType.entries.filter { it != TaskType.JOB }.forEach { type ->
                    DropdownMenuItem(text = { Text(type.name) }, onClick = {
                        taskType = type
                        if (type != TaskType.GENERAL) subcategory = null
                        typeMenuExpanded = false
                    })
                }
            }
        }

        if (taskType == TaskType.GENERAL) {
            ExposedDropdownMenuBox(expanded = subcategoryMenuExpanded, onExpandedChange = { subcategoryMenuExpanded = it }) {
                OutlinedTextField(
                    value = subcategory?.name ?: "(none)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Subcategory") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subcategoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = subcategoryMenuExpanded, onDismissRequest = { subcategoryMenuExpanded = false }) {
                    GeneralSubcategory.entries.forEach { sub ->
                        DropdownMenuItem(text = { Text(sub.name) }, onClick = {
                            subcategory = sub
                            subcategoryMenuExpanded = false
                        })
                    }
                }
            }
        }

        OutlinedTextField(
            value = durationMinutes,
            onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
            label = { Text("Duration (minutes)") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = dueDate,
            onValueChange = { dueDate = it },
            label = { Text("Due date (YYYY-MM-DD, optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = name.isNotBlank() && durationMinutes.toIntOrNull() != null && !loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        runCatching {
                            apiClient.createTask(
                                sessionToken,
                                CreateTaskRequest(
                                    name = name,
                                    taskType = taskType,
                                    subcategory = subcategory,
                                    dueDate = dueDate.ifBlank { null },
                                    durationMinutes = durationMinutes.toInt(),
                                ),
                            )
                        }.onSuccess { onDone() }
                            .onFailure { error = "Couldn't create task: ${it.message}" }
                        loading = false
                    }
                },
            ) {
                Text("Add")
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
