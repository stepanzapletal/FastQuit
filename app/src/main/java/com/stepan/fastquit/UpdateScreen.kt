package com.stepan.fastquit

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseUpdateScreen(onUpdateComplete: () -> Unit) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel()

    var hasStarted by remember { mutableStateOf(false) }
    var currentStepText by remember { mutableStateOf("Initializing...") }
    var isFinished by remember { mutableStateOf(false) }

    // This is the single source of truth for the smooth line
    val progressAnimatable = remember { Animatable(0f) }

    LaunchedEffect(hasStarted) {
        if (!hasStarted) return@LaunchedEffect

        // Launch the continuous smooth line animation (4.5 seconds of pure silk)
        val animationJob = launch {
            progressAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 4500, easing = LinearOutSlowInEasing)
            )
        }

        // Parallel Task: Habit DB Migration
        // Mentioning Habit DB Version
        currentStepText = "Optimizing Habit Database (v$HABIT_DB_VERSION)..."
        val habitDb = AppDatabase.getDatabase(context)
        habitDb.habitDao().getHabitById(-1)
        delay(1200)

        // Parallel Task: Settings Sync
        // Mentioning Settings DB Version
        currentStepText = "Syncing Settings Database (v$SETTINGS_DB_VERSION)..."
        val settingsDb = SettingsDatabase.getDatabase(context)
        settingsDb.settingsDao().setSettingsVersion(
            SettingsVersionInfo(versionCode = SETTINGS_DB_VERSION)
        )
        delay(1200)

        // Parallel Task: Version Finalization
        // Mentioning Both
        currentStepText = "Finalizing Storage (v$HABIT_DB_VERSION) & Preferences (v$SETTINGS_DB_VERSION)..."
        habitDb.habitDao().setDbVersion(DbVersionInfo(versionCode = HABIT_DB_VERSION))

        // Wait for the smooth line to actually finish its journey
        animationJob.join()

        isFinished = true
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Hero Icon
            Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = when {
                        isFinished -> Icons.Rounded.CheckCircle
                        hasStarted -> Icons.Rounded.Storage
                        else -> Icons.Rounded.RocketLaunch
                    },
                    transitionSpec = {
                        (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
                    },
                    label = "IconAnim"
                ) { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(110.dp),
                        tint = if (isFinished) SuccessGreen else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = if (isFinished) "System Ready" else "Core Update",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Smoothly transitioning status text using AnimatedContent
            Box(modifier = Modifier.height(72.dp), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    targetState = if (!hasStarted) "A mandatory system update is required to continue." else currentStepText,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
                    },
                    label = "TextAnim"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            if (!hasStarted) {
                Button(
                    onClick = { hasStarted = true },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("BEGIN MIGRATION", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            } else if (!isFinished) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // THE SMOOTH LINE: Thicker, Wavier, and Continuous
                    LinearWavyProgressIndicator(
                        progress = { progressAnimatable.value },
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        stroke = Stroke(width = 30f, cap = StrokeCap.Round), // Extra bold
                        trackStroke = Stroke(width = 30f, cap = StrokeCap.Round), // Extra bold
                        amplitude = { 0.3f },
                        wavelength = 40.dp,
                        waveSpeed = 20.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "${(progressAnimatable.value * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            } else {
                Button(
                    onClick = {
                        settingsViewModel.update { it.copy(forceUpdateScreen = false) }
                        onUpdateComplete()
                    },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("ENTER FASTQUIT", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
        }
    }
}