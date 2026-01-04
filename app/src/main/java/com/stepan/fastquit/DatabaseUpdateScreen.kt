package com.stepan.fastquit

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseUpdateScreen(
    onUpdateComplete: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var updateProgress by remember { mutableFloatStateOf(0f) }
    var currentStep by remember { mutableIntStateOf(0) }
    var isUpdating by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    val totalSteps = 3
    val stepMessages = listOf(
        "Preparing database migration...",
        "Adding new features...",
        "Optimizing performance..."
    )

    LaunchedEffect(Unit) {
        // Start the update process
        isUpdating = true
        updateProgress = 0f

        try {
            // Step 1: Check and update habits database
            delay(500)
            currentStep = 1
            updateProgress = 0.33f

            val needsHabitUpdate = AppDatabase.needsUpdate(context)
            if (needsHabitUpdate) {
                // Force database initialization which will trigger migrations
                AppDatabase.getDatabase(context)
            }

            // Step 2: Check and update settings database
            delay(500)
            currentStep = 2
            updateProgress = 0.66f

            val needsSettingsUpdate = SettingsDatabase.needsUpdate(context)
            if (needsSettingsUpdate) {
                SettingsDatabase.getDatabase(context)
            }

            // Step 3: Finalize
            delay(500)
            currentStep = 3
            updateProgress = 1f

            delay(500) // Show completion for a moment

            onUpdateComplete()

        } catch (e: Exception) {
            updateError = "Update failed: ${e.localizedMessage}"
            isUpdating = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated icon container
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Native CircularWavyProgressIndicator
                    CircularWavyProgressIndicator(
                        progress = { updateProgress },
                        modifier = Modifier.size(120.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        amplitude = { 0.3f },
                        stroke = Stroke(
                            width = with(density) { 8.dp.toPx() },
                            cap = StrokeCap.Round
                        ),
                        trackStroke = Stroke(
                            width = with(density) { 8.dp.toPx() }
                        ),
                        wavelength = 80.dp,
                        waveSpeed = 80.dp
                    )

                    // Icon in the center
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        val animatedRotation by animateFloatAsState(
                            targetValue = updateProgress * 360f,
                            animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                            label = "IconRotation"
                        )

                        Icon(
                            Icons.Rounded.SystemUpdate,
                            contentDescription = "Updating",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    rotationZ = animatedRotation
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "Database Update",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Your app needs to update the database to access new features",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Progress section
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Native LinearProgressIndicator
                        LinearProgressIndicator(
                            progress = { updateProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)), // <-- Apply shape using clip modifier
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )


                        Spacer(modifier = Modifier.height(16.dp))

                        if (currentStep > 0 && currentStep <= stepMessages.size) {
                            Text(
                                stepMessages[currentStep - 1],
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "${(updateProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Version info using Native Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Database Version",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                "v$HABIT_DB_VERSION • v$SETTINGS_DB_VERSION",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            tonalElevation = 2.dp,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Error message using Native AssistChip when error occurs
                if (updateError != null) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                updateError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Error,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Native Button for retry
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isUpdating = true
                                updateProgress = 0f
                                updateError = null
                                currentStep = 0

                                // Retry logic
                                delay(1000)
                                try {
                                    AppDatabase.getDatabase(context)
                                    SettingsDatabase.getDatabase(context)
                                    onUpdateComplete()
                                } catch (e: Exception) {
                                    updateError = "Retry failed: ${e.localizedMessage}"
                                    isUpdating = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Update", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Info text using Native Text
                    Text(
                        "Please keep the app open during the update\nYour data is safe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// Since we need a smooth rotation animation for the icon, let's create a simple modifier extension
// that uses native graphicsLayer
@Composable
fun rememberRotationState(progress: Float): Float {
    return animateFloatAsState(
        targetValue = progress * 360f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "IconRotation"
    ).value
}