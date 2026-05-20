package org.dark.keyboard

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import org.dark.keyboard.suggestions.ModelDownloader
import org.dark.keyboard.util.FileLoggingTree
import timber.log.Timber
import java.io.File

class SettingsActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        setContent {
            DarkKeyboardTheme {
                SettingsScreen(
                    prefs = prefs,
                    context = this,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: SharedPreferences, context: android.content.Context, onBack: () -> Unit) {
    var showLayoutEditor by remember { mutableStateOf(false) }

    if (showLayoutEditor) {
        LayoutEditorScreen(
            onSelectLayout = { name ->
                prefs.edit().putString("custom_layout_name", name).apply()
                showLayoutEditor = false
            },
            onClearLayout = {
                prefs.edit().remove("custom_layout_name").apply()
            }
        )
        return
    }

    var currentLayout by remember { 
        mutableStateOf(prefs.getString("keyboard_layout", "pc") ?: "pc") 
    }
    var showModifierStatus by remember { 
        mutableStateOf(prefs.getBoolean("show_modifier_status", true)) 
    }
    var showNumberRow by remember { 
        mutableStateOf(prefs.getBoolean("show_number_row", true)) 
    }
    var chordingCtrlKey by remember {
        mutableStateOf(prefs.getString("chording_ctrl_key", "0") ?: "0")
    }
    var currentTheme by remember {
        mutableStateOf(prefs.getString("keyboard_theme", "Dark (Default)") ?: "Dark (Default)")
    }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showChordingDialog by remember { mutableStateOf(false) }
    var vibrateOnKeypress by remember {
        mutableStateOf(prefs.getBoolean("vibrate_on_keypress", false))
    }
    var soundOnKeypress by remember {
        mutableStateOf(prefs.getBoolean("sound_on_keypress", false))
    }

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

            item {
                SettingCard {
                    SettingItem(
                        icon = Icons.Default.Edit,
                        title = "Custom layouts",
                        subtitle = "Create and edit your own XML layouts live",
                        onClick = { showLayoutEditor = true }
                    )
                }
            }

            item {
                SettingCard {
                    SettingItem(
                        icon = Icons.Default.Settings,
                        title = "Ctrl key code",
                        subtitle = chordingCtrlKeyDisplay(chordingCtrlKey),
                        onClick = { showChordingDialog = true }
                    )
                }
            }

            // Appearance Section
            item {
                SectionHeader("Appearance")
            }

            item {
                SettingCard {
                    SettingItem(
                        icon = Icons.Default.Palette,
                        title = "Keyboard theme",
                        subtitle = currentTheme,
                        onClick = { showThemeDialog = true }
                    )
                }
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

            // Feedback Section
            item {
                SectionHeader("Feedback")
            }

            item {
                SettingCard {
                    Column {
                        SettingSwitchItem(
                            icon = Icons.Default.Star,
                            title = "Vibrate on keypress",
                            subtitle = "Haptic feedback when pressing keys",
                            checked = vibrateOnKeypress,
                            onCheckedChange = { checked ->
                                vibrateOnKeypress = checked
                                prefs.edit().putBoolean("vibrate_on_keypress", checked).apply()
                            }
                        )
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingSwitchItem(
                            icon = Icons.Default.Star,
                            title = "Sound on keypress",
                            subtitle = "Audible click when pressing keys",
                            checked = soundOnKeypress,
                            onCheckedChange = { checked ->
                                soundOnKeypress = checked
                                prefs.edit().putBoolean("sound_on_keypress", checked).apply()
                            }
                        )
                    }
                }
            }

             // AI Suggestions Section
             item { SectionHeader("AI Suggestions") }
             item {
                 AiModelCard(context = context)
             }

             // Debug Logs Section
             item { SectionHeader("Debug") }
             item {
                 LogManagementCard(context = context)
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
                prefs.edit().putString("keyboard_layout", layout).remove("custom_layout_name").apply()
                Timber.i("Layout changed to: $layout")
                showLayoutDialog = false
            }
        )
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { theme ->
                currentTheme = theme
                prefs.edit().putString("keyboard_theme", theme).apply()
                Timber.i("Theme changed to: $theme")
                showThemeDialog = false
            }
        )
    }

    // Chording Ctrl Key Dialog
    if (showChordingDialog) {
        val options = listOf(
            "0" to "None (only as meta state)",
            "113" to "Left Ctrl",
            "114" to "Right Ctrl"
        )
        ChordingKeyDialog(
            title = "Ctrl key code",
            currentValue = chordingCtrlKey,
            options = options,
            onDismiss = { showChordingDialog = false },
            onSelected = { value ->
                chordingCtrlKey = value
                prefs.edit().putString("chording_ctrl_key", value).apply()
                showChordingDialog = false
            }
        )
    }
}

private fun chordingCtrlKeyDisplay(value: String) = when (value) {
    "113" -> "Left Ctrl"
    "114" -> "Right Ctrl"
    else -> "None (only as meta state)"
}

@Composable
fun ChordingKeyDialog(
    title: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (v, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelected(v) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == v,
                            onClick = { onSelected(v) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
fun ThemeSelectionDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Theme",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                KeyboardTheme.all.forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        selected = currentTheme == theme.name,
                        onClick = { onThemeSelected(theme.name) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
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
fun ThemeOption(
    theme: KeyboardTheme,
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
            // Mini preview of the theme
            Row(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color(theme.background)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(androidx.compose.ui.graphics.Color(theme.keyNormal))
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
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

@Composable
fun AiModelCard(context: android.content.Context) {
    var isDownloaded by remember { mutableStateOf(ModelDownloader.areModelsDownloaded(context)) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf("") }

    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Spell Checker (T5 Encoder)",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        if (isDownloaded) "T5 active — GPU accelerated"
                        else "T5 Encoder (~${ModelDownloader.totalSizeMB()}MB) — Download for AI autocorrect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(errorMsg, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (isDownloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("Downloading... $progress%", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isDownloaded && !isDownloading) {
                    Button(onClick = {
                        isDownloading = true; errorMsg = ""
                        ModelDownloader.download(context,
                            onProgress = { p -> progress = p },
                            onComplete = { isDownloaded = true; isDownloading = false },
                            onError    = { e -> errorMsg = e; isDownloading = false }
                        )
                    }) { Text("Download Model") }
                }
                if (isDownloaded) {
                    OutlinedButton(onClick = {
                        ModelDownloader.deleteModels(context)
                        isDownloaded = false
                    }) { Text("Delete Model") }
                }
            }
        }
    }
}

@Composable
fun LogManagementCard(context: android.content.Context) {
    val logTree = DarkIME2.fileLoggingTree
    val logFiles = logTree?.getLogFiles() ?: emptyList()
    val logSizeMB = logTree?.getTotalLogSizeMB() ?: 0.0
    var showClearDialog by remember { mutableStateOf(false) }

    SettingCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Debug Logs",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "${logFiles.size} files, ${String.format("%.1f", logSizeMB)}MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        logTree?.getCurrentLogFile()?.let { logFile ->
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    logFile
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "DarkKeyboard Log - ${logFile.name}")
                                    putExtra(Intent.EXTRA_TEXT, "DarkKeyboard session log attached.")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share log file"))
                                Timber.i("Log file shared: ${logFile.name}")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to share log file")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share Log")
                }
                OutlinedButton(
                    onClick = {
                        logTree?.getLogFiles()?.let { files ->
                            if (files.isEmpty()) return@let
                            try {
                                val uris = files.map { file ->
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                }
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "text/plain"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    putExtra(Intent.EXTRA_SUBJECT, "DarkKeyboard Logs (${files.size} files)")
                                    putExtra(Intent.EXTRA_TEXT, "All DarkKeyboard session logs attached.")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share all logs"))
                                Timber.i("All logs shared: ${files.size} files")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to share all logs")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share All")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear Logs")
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Logs") },
            text = { Text("Delete all ${logFiles.size} log files (${String.format("%.1f", logSizeMB)}MB)?") },
            confirmButton = {
                TextButton(onClick = {
                    logTree?.clearAllLogs()
                    showClearDialog = false
                    Timber.i("Logs cleared")
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}
