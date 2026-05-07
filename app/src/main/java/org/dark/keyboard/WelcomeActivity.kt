package org.dark.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DarkKeyboardTheme {
                WelcomeScreen(
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun DarkKeyboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            background = MaterialTheme.colorScheme.background,
        ),
        content = content
    )
}

fun isImeEnabled(ctx: Context): Boolean {
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
}

fun isImeCurrent(ctx: Context): Boolean {
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val currentIme = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return imm.enabledInputMethodList.any {
        it.id == currentIme && it.packageName == ctx.packageName
    }
}

@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    
    fun determineStep(): Int = when {
        !isImeEnabled(ctx) -> 0
        !isImeCurrent(ctx) -> 1
        else -> 2
    }
    
    var step by rememberSaveable { mutableIntStateOf(determineStep()) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(step) {
        if (step == 1) {
            scope.launch {
                while (step == 1 && !isImeCurrent(ctx)) {
                    delay(100)
                }
                step = 2
            }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Dark Keyboard",
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            when (step) {
                0 -> EnableStep(onNext = { step = determineStep() })
                1 -> SelectStep(onNext = { step = determineStep() })
                2 -> FinishStep(onFinish = onFinish)
            }
        }
    }
}

@Composable
fun EnableStep(onNext: () -> Unit) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onNext()
    }
    
    StepCard(
        title = "Step 1: Enable Keyboard",
        instruction = "Go to Settings and enable Dark Keyboard in the list of input methods.",
        actionText = "Open Settings"
    ) {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        launcher.launch(intent)
    }
}

@Composable
fun SelectStep(onNext: () -> Unit) {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    
    StepCard(
        title = "Step 2: Select Keyboard",
        instruction = "Select Dark Keyboard from the input method picker.",
        actionText = "Show Picker"
    ) {
        imm.showInputMethodPicker()
    }
}

@Composable
fun FinishStep(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "All Set!",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Dark Keyboard is ready to use. You can now type with full PC keyboard support including Ctrl, Alt, Tab, and function keys.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = {
                    val intent = Intent(ctx, SettingsActivity::class.java)
                    ctx.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text("Open Settings")
            }
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Finish")
            }
        }
    }
}

@Composable
fun StepCard(
    title: String,
    instruction: String,
    actionText: String,
    action: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = action,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(actionText)
            }
        }
    }
}
