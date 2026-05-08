package org.dark.keyboard

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager

class SettingsActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        setContent {
            DarkKeyboardTheme {
                SettingsScreen(
                    prefs = prefs,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    var currentLayout by remember { 
        mutableStateOf(prefs.getString("keyboard_layout", "pc") ?: "pc") 
    }
    var showModifierStatus by remember { 
        mutableStateOf(prefs.getBoolean("show_modifier_status", true)) 
    }
    var showNumberRow by remember { 
        mutableStateOf(prefs.getBoolean("show_number_row", true)) 
    }
    var showLayoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Keyboard Section
            item {
                SectionHeader("Keyboard")
            }
            
            item {
                SettingCard {
                    SettingItem(
                        icon = Icons.Default.Settings,
                        title = "Layout",
                        subtitle = when (currentLayout) {
                            "pc" -> "QWERTY Standard"
                            "compact" -> "PC Compact"
                            else -> "QWERTY Standard"
                        },
                        onClick = { showLayoutDialog = true }
                    )
                }
            }

            item {
                SettingCard {
                    SettingSwitchItem(
                        icon = Icons.Default.AccountBox,
                        title = "Number row",
                        subtitle = "Show dedicated row for numbers 1-9 and 0",
                        checked = showNumberRow,
                        onCheckedChange = { checked ->
                            showNumberRow = checked
                            prefs.edit().putBoolean("show_number_row", checked).apply()
                        }
                    )
                }
            }

            // Appearance Section
            item {
                SectionHeader("Appearance")
            }

            item {
                SettingCard {
                    SettingSwitchItem(
                        icon = Icons.Default.Info,
                        title = "Modifier status bar",
                        subtitle = "Show active modifiers (Ctrl, Shift, Alt, Fn)",
                        checked = showModifierStatus,
                        onCheckedChange = { checked ->
                            showModifierStatus = checked
                            prefs.edit().putBoolean("show_modifier_status", checked).apply()
                        }
                    )
                }
            }

            // About Section
            item {
                SectionHeader("About")
            }

            item {
                SettingCard {
                    Column {
                        SettingItem(
                            icon = Icons.Default.Info,
                            title = "DarkKeyboard",
                            subtitle = "Version 1.0.0",
                            onClick = { }
                        )
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingItem(
                            icon = Icons.Default.Favorite,
                            title = "Credits",
                            subtitle = "Based on Hacker's Keyboard by Klaus Weidner",
                            onClick = { }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Layout Selection Dialog
    if (showLayoutDialog) {
        LayoutSelectionDialog(
            currentLayout = currentLayout,
            onDismiss = { showLayoutDialog = false },
            onLayoutSelected = { layout ->
                currentLayout = layout
                prefs.edit().putString("keyboard_layout", layout).apply()
                android.util.Log.i("SettingsActivity", "Layout changed to: $layout")
                showLayoutDialog = false
            }
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
fun LayoutSelectionDialog(
    currentLayout: String,
    onDismiss: () -> Unit,
    onLayoutSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Layout",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                LayoutOption(
                    title = "QWERTY Standard",
                    description = "Standard 5-row layout with proper proportions. Numbers, QWERTY, Tab beside 'a', and bottom row with modifiers. Most similar to traditional keyboards.",
                    icon = Icons.Default.Star,
                    selected = currentLayout == "pc",
                    onClick = { onLayoutSelected("pc") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LayoutOption(
                    title = "PC Compact",
                    description = "Compact 6-row layout with navigation arrows. Extension row, Tab beside 'a', Fn/Ctrl/Alt/?123 in bottom row with arrows. More features in less space.",
                    icon = Icons.Default.Star,
                    selected = currentLayout == "compact",
                    onClick = { onLayoutSelected("compact") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LayoutOption(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
