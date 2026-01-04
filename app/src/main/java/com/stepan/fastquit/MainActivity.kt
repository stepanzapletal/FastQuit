package com.stepan.fastquit

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stepan.fastquit.ui.theme.FastQuitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        setContent {
            // 1. Init Settings ViewModel early to read Theme
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        SettingsViewModel(application) as T
                }
            )
            val prefs by settingsViewModel.preferences.collectAsState()

            // 2. Check if database needs update
            var needsDatabaseUpdate by remember { mutableStateOf<Boolean?>(null) }
            var showUpdateScreen by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                // Check both databases
                val needsHabitUpdate = AppDatabase.needsUpdate(applicationContext)
                val needsSettingsUpdate = SettingsDatabase.needsUpdate(applicationContext)
                needsDatabaseUpdate = needsHabitUpdate || needsSettingsUpdate

                if (needsDatabaseUpdate == true) {
                    showUpdateScreen = true
                }
            }

            // 3. Calculate Theme
            val darkTheme = when (prefs.theme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            // 4. Pass to Theme
            FastQuitTheme(darkTheme = darkTheme) {
                if (showUpdateScreen) {
                    DatabaseUpdateScreen(
                        onUpdateComplete = {
                            showUpdateScreen = false
                            needsDatabaseUpdate = false
                        }
                    )
                } else if (needsDatabaseUpdate == false) {
                    // Show normal app only when update is not needed or completed
                    val navController = rememberNavController()

                    val context = LocalContext.current
                    var hasNotificationPermission by remember {
                        mutableStateOf(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            } else true
                        )
                    }
                    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasNotificationPermission = it }
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") { FastQuitHomeScreen(navController) }
                        composable("settings") { SettingsScreen(navController, settingsViewModel) }
                        composable("detail/{habitId}/{habitName}", arguments = listOf(navArgument("habitId") { type = NavType.IntType })) { backStackEntry ->
                            val id = backStackEntry.arguments?.getInt("habitId") ?: 0
                            val name = backStackEntry.arguments?.getString("habitName") ?: ""
                            val app = LocalContext.current.applicationContext as Application
                            val detailViewModel: DetailViewModel = viewModel(factory = DetailViewModelFactory(app, id))
                            HabitDetailScreen(navController, name, detailViewModel)
                        }
                    }
                } else {
                    // Show loading while checking
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ACHIEVEMENTS", "Achievements", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

// CONSTANT FOR NICE GREEN
val SuccessGreen = Color(0xFF00D158)

data class HabitModel(val id: Int, val name: String, val icon: ImageVector, val lastResetTime: Long, val lastEventTitle: String, val targetSeconds: Long, val targetLabel: String, val completions: Int, val targetChangesCount: Int)

fun formatLiveDuration(diffMillis: Long): String {
    val seconds = diffMillis / 1000
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (d > 0) String.format("%dd %02d:%02d:%02d", d, h, m, s) else String.format("%02d:%02d:%02d", h, m, s)
}

// ===================== SETTINGS SCREEN =====================

enum class SettingsPage { Main, Basic, Backup, Dev, About, Licenses, Haptics }

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, settingsViewModel: SettingsViewModel = viewModel()) {
    var currentPage by remember { mutableStateOf(SettingsPage.Main) }
    // Track the previous page to determine direction
    var previousPage by remember { mutableStateOf<SettingsPage?>(null) }

    BackHandler(enabled = currentPage != SettingsPage.Main) {
        previousPage = currentPage
        currentPage = SettingsPage.Main
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        when(currentPage) {
                            SettingsPage.Main -> "Settings"
                            SettingsPage.Basic -> "Preferences"
                            SettingsPage.Backup -> "Data & Sync"
                            SettingsPage.Dev -> "Lab"
                            SettingsPage.About -> "About"
                            SettingsPage.Licenses -> "Legal"
                            SettingsPage.Haptics -> "Haptics"
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        previousPage = currentPage
                        if (currentPage == SettingsPage.Main) navController.popBackStack() else currentPage = SettingsPage.Main
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    // Determine if we're navigating forward or backward
                    val isForward = when {
                        // If going from Main to any other page, it's forward
                        initialState == SettingsPage.Main && targetState != SettingsPage.Main -> true
                        // If going back to Main from any page, it's backward
                        targetState == SettingsPage.Main && initialState != SettingsPage.Main -> false
                        // Otherwise, compare enum ordinals
                        else -> targetState.ordinal > initialState.ordinal
                    }

                    if (isForward) {
                        // Forward navigation: slide in from right, slide out to left
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 300)
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth },
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 300)
                        )
                    } else {
                        // Backward navigation: slide in from left, slide out to right
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth },
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 300)
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 300)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 300)
                        )
                    }
                },
                label = "SettingsNav"
            ) { page ->
                when (page) {
                    SettingsPage.Main -> SettingsMainList(onNavigate = {
                        previousPage = currentPage
                        currentPage = it
                    })
                    SettingsPage.Basic -> BasicSettings(settingsViewModel, onNavigate = {
                        previousPage = currentPage
                        currentPage = it
                    })
                    SettingsPage.Haptics -> HapticsSettings(settingsViewModel)
                    SettingsPage.Backup -> BackupSettings()
                    SettingsPage.Dev -> DevSettings(settingsViewModel)
                    SettingsPage.About -> AboutSettings()
                    SettingsPage.Licenses -> LicenseSettings()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticsSettings(viewModel: SettingsViewModel) {
    val prefs by viewModel.preferences.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ExpressiveSection("Master Control") {
            ExpressiveToggleItem(
                title = "Global Haptics",
                subtitle = "Master switch for all haptics",
                icon = Icons.Rounded.Vibration,
                checked = prefs.hapticsGlobal,
                onCheckedChange = { viewModel.update { it.copy(hapticsGlobal = !it.hapticsGlobal) } }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(visible = prefs.hapticsGlobal) {
            Column {
                ExpressiveSection("Tactile Details") {
                    ExpressiveToggleItem(
                        title = "Timer Ticks",
                        subtitle = "Subtle pulse every second",
                        icon = Icons.Rounded.Timer,
                        checked = prefs.hapticsTimer,
                        onCheckedChange = { viewModel.update { it.copy(hapticsTimer = !it.hapticsTimer) } }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    ExpressiveToggleItem(
                        title = "Event Success",
                        subtitle = "Thump on achievements",
                        icon = Icons.Rounded.Celebration,
                        checked = prefs.hapticsEvents,
                        onCheckedChange = { viewModel.update { it.copy(hapticsEvents = !it.hapticsEvents) } }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    ExpressiveToggleItem(
                        title = "UI Interaction",
                        subtitle = "Clicks on buttons and cards",
                        icon = Icons.Rounded.TouchApp,
                        checked = prefs.hapticsUI,
                        onCheckedChange = { viewModel.update { it.copy(hapticsUI = !it.hapticsUI) } }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    ExpressiveToggleItem(
                        title = "Alerts & Warnings",
                        subtitle = "Resistive feel on destructive actions",
                        icon = Icons.Rounded.Warning,
                        checked = prefs.hapticsWarnings,
                        onCheckedChange = { viewModel.update { it.copy(hapticsWarnings = !it.hapticsWarnings) } }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Add haptic tester card for quick access (similar to DevSettings)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Haptic Preview",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Test your settings in real-time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val view = LocalView.current
                        val tests = listOf(
                            "Click" to { HapticHelper.click(view, prefs) },
                            "Tick" to { HapticHelper.tick(view, prefs) },
                            "Success" to { HapticHelper.success(view, prefs) },
                            "Warning" to { HapticHelper.warning(view, prefs) }
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.height(140.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tests) { (label, action) ->
                                FilledTonalButton(
                                    onClick = action,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Show hint when global haptics is disabled
        AnimatedVisibility(
            visible = !prefs.hapticsGlobal,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        "Turn on Global Haptics to customize individual feedback types",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun HapticToggleItem(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onToggle() }
        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
fun SettingsMainList(onNavigate: (SettingsPage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ExpressiveSection("General") {
            ExpressiveItem("Basic Options", "Language, Notifications, Theme", Icons.Rounded.Tune) { onNavigate(SettingsPage.Basic) }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpressiveItem("Backup & Restore", "Export data locally", Icons.Rounded.CloudUpload) { onNavigate(SettingsPage.Backup) }
        }

        ExpressiveSection("Advanced") {
            ExpressiveItem("Dev / Lab Options", "Experimental features & stats", Icons.Rounded.Science) { onNavigate(SettingsPage.Dev) }
        }

        ExpressiveSection("Info") {
            ExpressiveItem("About App", "Credits & FAQ", Icons.Rounded.Info) { onNavigate(SettingsPage.About) }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpressiveItem("Licensing", "MIT License", Icons.Rounded.Gavel) { onNavigate(SettingsPage.Licenses) }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// --- SUB-SCREENS ---

@Composable
fun BasicSettings(viewModel: SettingsViewModel, onNavigate: (SettingsPage) -> Unit) {
    val prefs by viewModel.preferences.collectAsState()

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {
        ExpressiveSection("Appearance") {
            var showThemeDialog by remember { mutableStateOf(false) }
            ExpressiveItem("App Theme", prefs.theme, Icons.Rounded.Palette) { showThemeDialog = true }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpressiveItem("Language", "${prefs.language} (Default)", Icons.Rounded.Language) { /* Future */ }

            if(showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Done") } },
                    title = { Text("Choose Theme") },
                    text = {
                        Column {
                            listOf("System", "Light", "Dark").forEach { t ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.update { it.copy(theme = t) }; showThemeDialog =
                                            false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = (prefs.theme == t), onClick = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(t)
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        ExpressiveSection("Behavior") {
            // Expressive Notifications
            ExpressiveToggleItem(
                title = "Notifications",
                subtitle = "Goal alerts & milestones",
                icon = Icons.Rounded.Notifications,
                checked = prefs.notificationsEnabled,
                onCheckedChange = { newCheckedState ->
                    viewModel.update { it.copy(notificationsEnabled = newCheckedState) }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            ExpressiveItem("Haptic Feedback", "Customize tactile response", Icons.Rounded.Vibration) {
                onNavigate(SettingsPage.Haptics)
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            // Expressive Updates
            ExpressiveToggleItem(
                title = "Auto-Check Updates",
                subtitle = "Check GitHub for versions",
                icon = Icons.Rounded.SystemUpdate,
                checked = prefs.autoUpdateEnabled,
                onCheckedChange = { newCheckedState ->
                    viewModel.update { it.copy(autoUpdateEnabled = newCheckedState) }
                }
            )

            AnimatedVisibility(visible = prefs.autoUpdateEnabled) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        ExpressiveItem("Check Frequency", prefs.updateFrequency, Icons.Rounded.Timer) { expanded = true }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            listOf("15 Mins", "1 Hour", "6 Hours", "24 Hours").forEach { f ->
                                DropdownMenuItem(text = { Text(f) }, onClick = { viewModel.update { it.copy(updateFrequency = f) }; expanded = false })
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ExpressiveToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 1. Improved semantics for accessibility
            .semantics {
                // Makes the entire row behave like a single switch for screen readers
                toggleableState = ToggleableState(checked)
                role = Role.Switch
            }
            // 2. Simplified clickable modifier
            .clickable(
                // The default ripple indication will be used
                role = Role.Switch, // Matching the semantics role
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // The row's semantics cover this
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // The Switch is purely decorative; the Row handles all logic and accessibility.
        Switch(
            checked = checked,
            onCheckedChange = null // Keep null to prevent conflicting interactions
        )
    }
}
@Composable
fun BackupSettings() {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(100.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CloudOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Local Vault", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Your data is currently stored locally on this device. Cloud sync and JSON export features are coming in version 2.0.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {}, modifier = Modifier
            .fillMaxWidth()
            .height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Export Data (Soon)") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevSettings(settingsViewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val prefs by settingsViewModel.preferences.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showNukeSheet by remember { mutableStateOf(false) }

    // Version states restoration
    var habitDbStoredVersion by remember { mutableIntStateOf(0) }
    var settingsDbStoredVersion by remember { mutableIntStateOf(0) }
    var isLoadingVersions by remember { mutableStateOf(true) }

    // State for database update testing
    var needsDatabaseUpdate by remember { mutableStateOf<Boolean?>(null) }
    var showUpdateScreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // Load versions logic restored
    LaunchedEffect(Unit) {
        isLoadingVersions = true
        withContext(Dispatchers.IO) {
            try {
                val habitDb = AppDatabase.getDatabase(context)
                habitDbStoredVersion = habitDb.habitDao().getDbVersion()?.versionCode ?: 0

                val settingsDb = SettingsDatabase.getDatabase(context)
                settingsDbStoredVersion = settingsDb.settingsDao().getSettingsVersion()?.versionCode ?: 0

                val needsHabitUpdate = AppDatabase.needsUpdate(context)
                val needsSettingsUpdate = SettingsDatabase.needsUpdate(context)
                needsDatabaseUpdate = needsHabitUpdate || needsSettingsUpdate
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingVersions = false
            }
        }
    }

    if (showNukeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNukeSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), modifier = Modifier.size(80.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp)) }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Danger Zone", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                Text("Deleting a database is permanent. App will kill process.", textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        HapticHelper.warning(view, prefs) // Fixed Signature
                        context.deleteDatabase("fastquit_db")
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.History, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("NUKE HABITS & HISTORY", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        HapticHelper.warning(view, prefs) // Fixed Signature
                        context.deleteDatabase("settings_db")
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.SettingsBackupRestore, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("NUKE ALL SETTINGS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showUpdateScreen) {
        DatabaseUpdateScreen(
            onUpdateComplete = {
                showUpdateScreen = false
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ExpressiveSection("Build Info") {
            ExpressiveItem("App Version", "1.0.0 (Alpha)", Icons.Rounded.Android) {}
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            ExpressiveItem(
                title = "Habits DB",
                subtitle = if (isLoadingVersions) "Loading..." else "Schema: v$HABIT_DB_VERSION • Stored: v$habitDbStoredVersion",
                icon = Icons.Rounded.Storage
            ) {}

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            ExpressiveItem(
                title = "Settings DB",
                subtitle = if (isLoadingVersions) "Loading..." else "Schema: v$SETTINGS_DB_VERSION • Stored: v$settingsDbStoredVersion",
                icon = Icons.Rounded.SettingsSystemDaydream
            ) {}

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            ExpressiveItem(
                title = "Database Update Testing",
                subtitle = if (needsDatabaseUpdate == true) "Update required! Click to test"
                else if (needsDatabaseUpdate == false) "No update needed. Click to force test"
                else "Checking update status...",
                icon = Icons.Rounded.SystemUpdate,
                onClick = { showUpdateScreen = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("HAPTIC ENGINE TESTER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val tests = listOf(
                    "Click" to { HapticHelper.click(view, prefs) },
                    "Tick" to { HapticHelper.tick(view, prefs) },
                    "Success" to { HapticHelper.success(view, prefs) },
                    "Warning" to { HapticHelper.warning(view, prefs) }
                )
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tests) { (label, action) ->
                        FilledTonalButton(onClick = action, shape = RoundedCornerShape(12.dp)) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        ExpressiveSection("Notification testing") {
            val scope = rememberCoroutineScope()
            Button(modifier = Modifier.padding(16.dp).fillMaxWidth(), onClick = { scope.launch { sendNotification(context, "Test", "Notification Test") } }) {
                Text("Send Test Notification")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("RUNTIME STATS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("PKG: ${context.packageName}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("TS: $now", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("UPTIME: ${android.os.SystemClock.elapsedRealtime() / 1000}s", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = SuccessGreen)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { showNukeSheet = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.DeleteForever, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("OPEN DESTROYER MENU", fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
fun AboutSettings() {
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {
        // PROFILE HEADER
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    // USER (IMAGE SUPPORT)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.profile), // Ensure R.drawable.profile exists
                            contentDescription = "Insecurity",
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Insecurity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Developer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(modifier = Modifier.width(24.dp))
                    Text("&", style = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.surfaceVariant))
                    Spacer(modifier = Modifier.width(24.dp))

                    // AI
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(70.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(32.dp)) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Gemini", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Co-Pilot", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Crafted with <3", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("(AI was used for debugging)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("F.A.Q.", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(16.dp))

        ExpressiveSection("Questions") {
            ExpandableItem("Why I made this app?", "To help myself and others break free from bad habits using a clean, guilt-free interface.")
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpandableItem("Is my data safe?", "Yes! 100% local. Your data never leaves this phone.")
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpandableItem("Future plans?", "Just general bux fixes and whatever the people want!")
        }
    }
}

@Composable
fun LicenseSettings() {
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {
        // Removed duplicate "Legal" Text header here
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("MIT License", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Copyright (c) 2026 Stepan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = """
                        Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

                        The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

                        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// --- EXPRESSIVE COMPONENTS ---

@Composable
fun ExpressiveSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), // "Island" color
            shape = RoundedCornerShape(24.dp), // Expressive Roundness
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun ExpressiveItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp), // Larger tap target
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer, // Expressive Icon BG
            modifier = Modifier.size(48.dp) // Larger Icon
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
fun ExpandableItem(title: String, body: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier
        .clickable { expanded = !expanded }
        .padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Icon(if(expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.outline)
        }
        AnimatedVisibility(visible = expanded) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

// ===================== HOME SCREEN (Unchanged Logic) =====================

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FastQuitHomeScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val habitList by viewModel.habits.collectAsState()
    var quote by remember { mutableStateOf(ApiQuote("The only way out is through.", "Unknown")) }
    var refreshCount by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    LaunchedEffect(refreshCount) { try { quote = withContext(Dispatchers.IO) { NetworkModule.api.getRandomQuote() } } catch (e: Exception) { e.printStackTrace() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), onClick = { refreshCount++ }, modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .zIndex(1f)) {
                Column(modifier = Modifier
                    .statusBarsPadding()
                    .padding(24.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            AnimatedContent(targetState = quote, transitionSpec = { (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90))).togetherWith(fadeOut(animationSpec = tween(90))) }, label = "QuoteAnim") { targetQuote ->
                                Column {
                                    Text("\"${targetQuote.quote}\"", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("- ${targetQuote.author}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        Box(modifier = Modifier.offset(y = (-8).dp)) {
                            // SETTINGS BUTTON
                            FilledTonalIconButton(onClick = { navController.navigate("settings") }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                                Icon(Icons.Rounded.Settings, "Settings")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showBottomSheet = true }, icon = { Icon(Icons.Default.Add, "Add") }, text = { Text("New Habit") }, expanded = true)
        }
    ) { innerPadding ->
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true; Handler(
            Looper.getMainLooper()).postDelayed({ isRefreshing = false }, 1000) }, state = pullState, modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding()), indicator = { PullToRefreshDefaults.LoadingIndicator(state = pullState, isRefreshing = isRefreshing, modifier = Modifier.align(Alignment.TopCenter)) }) {
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                items(items = habitList, key = { it.id }) { habit ->
                    val context = LocalContext.current
                    LaunchedEffect(now) {
                        val seconds = (now - habit.lastResetTime) / 1000
                        checkAndNotifyAchievements(context, habit.name, habit.id, seconds, habit.completions, habit.targetSeconds)
                    }
                    Box(modifier = Modifier.animateItem()) {
                        HabitCard(habit = habit, now = now, navController = navController, onDelete = { viewModel.deleteHabit(habit.id) }, onMoveUp = { viewModel.moveHabit(habit.id, true) }, onMoveDown = { viewModel.moveHabit(habit.id, false) })
                    }
                }
                if (habitList.isEmpty()) { item { Text("No habits yet. Tap 'New Habit'!", modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp), textAlign = TextAlign.Center) } }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
    if (showBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { BottomSheetDefaults.DragHandle() }) {
            NewHabitSheet(onDismiss = { showBottomSheet = false }, onSave = { name, icon, amount, unit, start -> viewModel.addHabit(name, icon, amount, unit, start); showBottomSheet = false })
        }
    }
}

// ... (Rest of HabitCard and NewHabitSheet remain unchanged from previous correct versions) ...
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    habit: HabitModel,
    now: Long,
    navController: NavController,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val density = LocalDensity.current
    val diffMillis = now - habit.lastResetTime
    val liveTime = formatLiveDuration(diffMillis)
    val diffSeconds = diffMillis / 1000f

    val rawProgress = if (habit.targetSeconds > 0) (diffSeconds / habit.targetSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    val isComplete = rawProgress >= 1f

    val animatedProgress by animateFloatAsState(targetValue = rawProgress, animationSpec = tween(1000, easing = LinearEasing), label = "Wave")

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 1.02f else 1f, label = "scale")
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (isPressed) 1f else 0f)
            .clip(RoundedCornerShape(28.dp))
            .combinedClickable(
                onClick = { navController.navigate("detail/${habit.id}/${habit.name}") },
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) { Icon(habit.icon, null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(habit.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "Options") }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(habit.lastEventTitle.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = liveTime,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = (-1).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        val waveColor = if(isComplete) SuccessGreen else MaterialTheme.colorScheme.primary
                        CircularWavyProgressIndicator(progress = { animatedProgress }, modifier = Modifier.size(100.dp), color = waveColor, trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f), amplitude = { 1f }, stroke = Stroke(width = with(density) { 8.dp.toPx() }, cap = StrokeCap.Round), trackStroke = Stroke(width = with(density) { 8.dp.toPx() }))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isComplete) {
                                Icon(Icons.Rounded.Check, null, tint = waveColor, modifier = Modifier.size(32.dp))
                            } else {
                                Text("${(animatedProgress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Text(habit.targetLabel.replace("Goal: ", ""), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, offset = DpOffset(x = 200.dp, y = 0.dp), shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(text = { Text("Move Up") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { showMenu = false; onMoveUp() })
                DropdownMenuItem(text = { Text("Move Down") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { showMenu = false; onMoveDown() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

// --- NEW HABIT SHEET (Date + Time Picker) ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewHabitSheet(onDismiss: () -> Unit, onSave: (String, String, Int, String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("Energy") }
    var showIconStudio by remember { mutableStateOf(false) }
    var goalAmount by remember { mutableStateOf("7") }
    var selectedUnit by remember { mutableStateOf("Days") }

    // DATE & TIME STATE
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isManualDate by remember { mutableStateOf(false) }
    var tempDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) } // Temp store date before adding time
    var selectedStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) } // Final Result

    val context = LocalContext.current
    val units = listOf("Seconds", "Minutes", "Hours", "Days", "Weeks", "Months", "Years")
    val isValid = name.isNotBlank() && goalAmount.toIntOrNull() != null

    // 1. DATE PICKER
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedStartTime)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Capture Date, then show Time Picker
                    tempDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    // 2. TIME PICKER
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            initialMinute = Calendar.getInstance().get(Calendar.MINUTE),
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // COMBINE DATE + TIME
                    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    calendar.timeInMillis = tempDateMillis // Set UTC Date from Picker
                    val year = calendar.get(Calendar.YEAR)
                    val month = calendar.get(Calendar.MONTH)
                    val day = calendar.get(Calendar.DAY_OF_MONTH)

                    // Now create Local Calendar to set Time
                    val localCal = Calendar.getInstance()
                    localCal.set(year, month, day, timeState.hour, timeState.minute)
                    val finalTime = localCal.timeInMillis

                    // Future Check
                    if (finalTime > System.currentTimeMillis()) {
                        Toast.makeText(context, "Cannot start in the future!", Toast.LENGTH_SHORT).show()
                        // Reset to Now
                        selectedStartTime = System.currentTimeMillis()
                        isManualDate = false
                    } else {
                        selectedStartTime = finalTime
                        isManualDate = true
                    }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    TimePicker(state = timeState)
                }
            }
        )
    }

    if (showIconStudio) {
        ModalBottomSheet(onDismissRequest = { showIconStudio = false }, containerColor = MaterialTheme.colorScheme.surface, dragHandle = { BottomSheetDefaults.DragHandle() }, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                var searchQuery by remember { mutableStateOf("") }
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Icon Library", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search...") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                var tempSelectedIcon by remember { mutableStateOf(selectedIconName) }
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 60.dp), modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val filtered = if (searchQuery.isBlank()) IconMapper.allIcons.toList() else IconMapper.allIcons.filter { it.key.contains(searchQuery, ignoreCase = true) }.toList()
                    items(items = filtered) { (key, icon) ->
                        val isSelected = tempSelectedIcon == key
                        Surface(onClick = { tempSelectedIcon = key }, shape = RoundedCornerShape(16.dp), color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(70.dp)) { Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp)) }
                        }
                    }
                }
                Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(IconMapper.getIcon(tempSelectedIcon), null, tint = MaterialTheme.colorScheme.onPrimary) } }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Text(tempSelectedIcon, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1) }
                        Button(onClick = { selectedIconName = tempSelectedIcon; showIconStudio = false }, modifier = Modifier.height(48.dp)) { Text("Confirm") }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp)
        .navigationBarsPadding()
        .imePadding()) {
        Text("New Commitment", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Habit Name") }, placeholder = { Text("e.g. Stop Vaping") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(modifier = Modifier.height(24.dp))

        // DISPLAY DATE + TIME
        val dateLabel = if (isManualDate) SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(selectedStartTime)) else "Starts Now (When added)"

        OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DateRange, null); Spacer(modifier = Modifier.width(16.dp)); Column { Text("Start Date", style = MaterialTheme.typography.labelSmall); Text(dateLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) } }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = IconMapper.quickIcons) { iconKey ->
                val isSelected = selectedIconName == iconKey
                val animScale by animateFloatAsState(if (isSelected) 1.1f else 1f, label = "scale")
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(onClick = { selectedIconName = iconKey }, shape = CircleShape, color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null, modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer { scaleX = animScale; scaleY = animScale }) { Box(contentAlignment = Alignment.Center) { Icon(IconMapper.getIcon(iconKey), null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
            item { Surface(onClick = { showIconStudio = true }, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreHoriz, "All", tint = MaterialTheme.colorScheme.onSecondaryContainer) } } }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Goal Duration", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = goalAmount, onValueChange = { if (it.all { char -> char.isDigit() }) goalAmount = it }, modifier = Modifier.weight(0.4f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            LazyRow(modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(items = units) { unit -> FilterChip(selected = selectedUnit == unit, onClick = { selectedUnit = unit }, label = { Text(unit) }) } }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { val amount = goalAmount.toIntOrNull() ?: 7; val finalStart = if (isManualDate) selectedStartTime else System.currentTimeMillis(); onSave(name, selectedIconName, amount, selectedUnit, finalStart) }, enabled = isValid, modifier = Modifier
            .fillMaxWidth()
            .height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Commit") }
    }
}

// NETWORK
data class ApiQuote(val quote: String, val author: String)
interface QuoteApi { @GET("quotes/random") suspend fun getRandomQuote(): ApiQuote }
object NetworkModule {
    val api: QuoteApi = Retrofit.Builder().baseUrl("https://dummyjson.com/").addConverterFactory(GsonConverterFactory.create()).build().create(QuoteApi::class.java)
}