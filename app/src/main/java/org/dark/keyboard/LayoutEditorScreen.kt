package org.dark.keyboard

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    onSelectLayout: (String) -> Unit,
    onClearLayout: () -> Unit
) {
    val context = LocalContext.current
    var layouts by remember { mutableStateOf(XmlKeyboardStorage.listLayouts(context)) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingLayout by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf<String?>(null) }
    var newLayoutName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Layouts") },
                actions = {
                    IconButton(onClick = {
                        newLayoutName = ""
                        editingLayout = null
                        showEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New layout")
                    }
                }
            )
        }
    ) { padding ->
        if (layouts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No custom layouts yet", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        newLayoutName = ""
                        editingLayout = null
                        showEditDialog = true
                    }) {
                        Text("+ Create your first layout")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    TextButton(
                        onClick = {
                            onClearLayout()
                            Toast.makeText(context, "Using built-in layout", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Use built-in layout (default)")
                    }
                }
                items(layouts) { name ->
                    LayoutItem(
                        name = name,
                        isActive = name == getActiveLayoutName(context),
                        onSelect = {
                            onSelectLayout(name)
                            Toast.makeText(context, "Activated: $name", Toast.LENGTH_SHORT).show()
                        },
                        onEdit = {
                            editingLayout = name
                            showEditDialog = true
                        },
                        onDeleteRequest = { deleteConfirm = name }
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        LayoutEditDialog(
            initialName = editingLayout ?: newLayoutName,
            initialContent = editingLayout?.let { XmlKeyboardStorage.readContent(context, it) } ?: "",
            isNew = editingLayout == null,
            onSave = { name, content ->
                XmlKeyboardStorage.saveLayout(context, name, content)
                onSelectLayout(name)
                layouts = XmlKeyboardStorage.listLayouts(context)
                showEditDialog = false
                editingLayout = null
                Toast.makeText(context, "Saved: $name", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showEditDialog = false
                editingLayout = null
            }
        )
    }

    deleteConfirm?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text("Delete layout?") },
            text = { Text("Delete \"$name\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    XmlKeyboardStorage.deleteLayout(context, name)
                    layouts = XmlKeyboardStorage.listLayouts(context)
                    deleteConfirm = null
                    if (name == getActiveLayoutName(context)) {
                        onClearLayout()
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LayoutItem(
    name: String,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onSelect,
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (isActive) {
                    Text("Active", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDeleteRequest) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun LayoutEditDialog(
    initialName: String,
    initialContent: String,
    isNew: Boolean,
    onSave: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var content by remember { mutableStateOf(initialContent) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Layout" else "Edit Layout") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = if (it.isBlank()) "Name is required" else null
                    },
                    label = { Text("Layout name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("XML Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    maxLines = Int.MAX_VALUE
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), content)
                    } else {
                        nameError = "Name is required"
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getActiveLayoutName(context: android.content.Context): String? =
    context.getSharedPreferences("org.dark.keyboard_preferences", android.content.Context.MODE_PRIVATE)
        .getString("custom_layout_name", null)
