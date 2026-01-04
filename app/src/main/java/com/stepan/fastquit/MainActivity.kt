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
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.stringResource
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
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.annotations.SerializedName
import com.stepan.fastquit.ui.theme.FastQuitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Helper enum for managing high-level app states
private enum class AppLaunchState { LOADING, UPDATE, MAIN }

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        setContent {
            // 1. Init Settings ViewModel
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        SettingsViewModel(application) as T
                }
            )
            val prefs by settingsViewModel.preferences.collectAsState()

            // Apply Language Logic on Start
            LaunchedEffect(prefs.language) {
                val targetTag = if (prefs.language == "System") "" else (LanguageHelper.supportedLanguages[prefs.language] ?: "en")
                val currentTag = LanguageHelper.getCurrentCode()

                if (currentTag != targetTag) {
                    LanguageHelper.setLanguage(targetTag)
                }
            }

            // 2. Database Update Logic
            var needsDatabaseUpdate by remember { mutableStateOf<Boolean?>(null) }
            var showUpdateScreen by remember { mutableStateOf(false) }

            LaunchedEffect(prefs.forceUpdateScreen) {
                val needsHabitUpdate = AppDatabase.needsUpdate(applicationContext)
                val needsSettingsUpdate = SettingsDatabase.needsUpdate(applicationContext)
                val shouldUpdate = needsHabitUpdate || needsSettingsUpdate || prefs.forceUpdateScreen

                needsDatabaseUpdate = shouldUpdate
                if (shouldUpdate) {
                    showUpdateScreen = true
                }
            }

            // 3. App State
            val appState = when {
                showUpdateScreen -> AppLaunchState.UPDATE
                needsDatabaseUpdate == false -> AppLaunchState.MAIN
                else -> AppLaunchState.LOADING
            }

            // 4. Theme
            val darkTheme = when (prefs.theme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            // 5. Content
            FastQuitTheme(darkTheme = darkTheme) {
                AnimatedContent(
                    targetState = appState,
                    transitionSpec = {
                        if (initialState == AppLaunchState.UPDATE && targetState == AppLaunchState.MAIN) {
                            (fadeIn(animationSpec = tween(800)) togetherWith
                                    slideOutVertically(
                                        animationSpec = tween(800, easing = FastOutSlowInEasing),
                                        targetOffsetY = { fullHeight -> fullHeight }
                                    ))
                                .apply { targetContentZIndex = -1f }
                        } else {
                            fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                        }
                    },
                    label = "AppLaunchTransition",
                    modifier = Modifier.fillMaxSize()
                ) { state ->
                    when (state) {
                        AppLaunchState.UPDATE -> {
                            DatabaseUpdateScreen(
                                onUpdateComplete = {
                                    showUpdateScreen = false
                                    needsDatabaseUpdate = false
                                }
                            )
                        }
                        AppLaunchState.MAIN -> {
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
                        }
                        AppLaunchState.LOADING -> {
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

data class HabitModel(
    val id: Int,
    val name: String,
    val icon: ImageVector,
    val lastResetTime: Long,
    val lastEventTitleRes: Int,
    val targetSeconds: Long,
    val targetLabelRes: Int,
    val goalLabel: String,
    val completions: Int,
    val targetChangesCount: Int
)


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
    BackHandler(enabled = currentPage != SettingsPage.Main) { currentPage = SettingsPage.Main }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        when(currentPage) {
                            SettingsPage.Main -> stringResource(R.string.settings)
                            SettingsPage.Basic -> stringResource(R.string.preferences)
                            SettingsPage.Backup -> stringResource(R.string.data_sync)
                            SettingsPage.Dev -> stringResource(R.string.lab)
                            SettingsPage.About -> stringResource(R.string.about)
                            SettingsPage.Licenses -> stringResource(R.string.legal)
                            SettingsPage.Haptics -> stringResource(R.string.haptics)
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (currentPage == SettingsPage.Main) navController.popBackStack() else currentPage = SettingsPage.Main }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
                    val duration = 400
                    val easing = FastOutSlowInEasing
                    val isForward = when {
                        initialState == SettingsPage.Main && targetState != SettingsPage.Main -> true
                        targetState == SettingsPage.Main && initialState != SettingsPage.Main -> false
                        else -> targetState.ordinal > initialState.ordinal
                    }
                    if (isForward) {
                        slideInHorizontally(tween(duration, easing = easing)) { it } + fadeIn(tween(duration)) togetherWith
                                slideOutHorizontally(tween(duration, easing = easing)) { -it / 3 } + fadeOut(tween(duration))
                    } else {
                        slideInHorizontally(tween(duration, easing = easing)) { -it / 3 } + fadeIn(tween(duration)) togetherWith
                                slideOutHorizontally(tween(duration, easing = easing)) { it } + fadeOut(tween(duration))
                    }
                },
                label = "SettingsNav"
            ) { page ->
                when (page) {
                    SettingsPage.Main -> SettingsMainList(onNavigate = { currentPage = it })
                    SettingsPage.Basic -> BasicSettings(settingsViewModel, onNavigate = { currentPage = it })
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

// ---------------------------------------------------------
//  NEW EXPRESSIVE POPUP COMPONENT
// ---------------------------------------------------------
data class SelectionOption(val display: String, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSelectionSheet(title: String, options: List<SelectionOption>, selectedOptionValue: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp))
            LazyColumn {
                items(options) { option ->
                    val isSelected = option.value == selectedOptionValue
                    ListItem(
                        headlineContent = { Text(text = option.display, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                        trailingContent = { if (isSelected) { Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSelect(option.value); onDismiss() }.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}


// --- BASIC SETTINGS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicSettings(viewModel: SettingsViewModel, onNavigate: (SettingsPage) -> Unit) {
    val prefs by viewModel.preferences.collectAsState()
    val context = LocalContext.current
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    val currentLanguageSubtitle by remember { derivedStateOf { LanguageHelper.getDisplayLanguage(context) } }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ExpressiveSection(stringResource(R.string.appearance)) {
            ExpressiveItem(title = stringResource(R.string.app_theme), subtitle = when(prefs.theme) { "Light" -> stringResource(R.string.c_light); "Dark" -> stringResource(R.string.c_dark); else -> stringResource(R.string.c_system) }, icon = Icons.Rounded.Palette) { showThemeSheet = true }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpressiveItem(title = stringResource(R.string.language), subtitle = stringResource(R.string.lanaguage_subtitle, currentLanguageSubtitle), icon = Icons.Rounded.Language) { showLanguageSheet = true }
        }
        Spacer(modifier = Modifier.height(24.dp))
        ExpressiveSection(stringResource(R.string.behavior)) {
            ExpressiveToggleItem(title = stringResource(R.string.notifications), subtitle = stringResource(R.string.goal_alerts_milestones), icon = Icons.Rounded.Notifications, checked = prefs.notificationsEnabled, onCheckedChange = { viewModel.update { p -> p.copy(notificationsEnabled = !p.notificationsEnabled) } })
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpressiveItem(title = stringResource(R.string.haptics), subtitle = stringResource(R.string.haptic_preview), icon = Icons.Rounded.Vibration) { onNavigate(SettingsPage.Haptics) }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpressiveToggleItem(title = stringResource(R.string.auto_check_updates), subtitle = stringResource(R.string.check_github_for_versions), icon = Icons.Rounded.SystemUpdate, checked = prefs.autoUpdateEnabled, onCheckedChange = { viewModel.update { p -> p.copy(autoUpdateEnabled = !p.autoUpdateEnabled) } })
        }
    }
    if (showThemeSheet) {
        val themeOptions = listOf(SelectionOption(stringResource(R.string.c_system), "System"), SelectionOption(stringResource(R.string.c_light), "Light"), SelectionOption(stringResource(R.string.c_dark), "Dark"))
        ExpressiveSelectionSheet(title = stringResource(R.string.choose_theme), options = themeOptions, selectedOptionValue = prefs.theme, onDismiss = { showThemeSheet = false }, onSelect = { viewModel.update { p -> p.copy(theme = it) } })
    }
    if (showLanguageSheet) {
        val languageOptions = remember {
            val list = mutableListOf<SelectionOption>()
            list.add(SelectionOption(context.getString(R.string.c_system), "System"))
            LanguageHelper.supportedLanguages.forEach { (name, _) -> list.add(SelectionOption(name, name)) }
            list
        }
        ExpressiveSelectionSheet(title = stringResource(R.string.select_language), options = languageOptions, selectedOptionValue = prefs.language, onDismiss = { showLanguageSheet = false }, onSelect = { newValue ->
            viewModel.update { it.copy(language = newValue) }
            val codeToSet = if (newValue == "System") "" else (LanguageHelper.supportedLanguages[newValue] ?: "en")
            LanguageHelper.setLanguage(codeToSet)
        })
    }
}


// ---------------------------------------------------------
//  LANGUAGE HELPER OBJECT
// ---------------------------------------------------------
object LanguageHelper {
    // Map of Display Name -> ISO Code
    val supportedLanguages = mapOf(
        "English" to "en",
        "Čeština" to "cs"
    )

    fun setLanguage(code: String) {
        if (code.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            val appLocale = LocaleListCompat.forLanguageTags(code)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    fun getCurrentCode(): String {
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        return if (!currentAppLocales.isEmpty) {
            currentAppLocales.get(0)?.language ?: "en"
        } else {
            "" // Represents System Default
        }
    }

    fun getDisplayLanguage(context: Context): String {
        val code = getCurrentCode()
        return if (code.isEmpty()) {
            context.getString(R.string.c_system)
        } else {
            // Find the key in our supported map, or default to English
            supportedLanguages.entries.find { it.value == code }?.key ?: "English"
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
        ExpressiveSection(stringResource(R.string.master_control)) {
            ExpressiveToggleItem(
                title = stringResource(R.string.global_haptics),
                subtitle = stringResource(R.string.master_switch_for_all_haptics),
                icon = Icons.Rounded.Vibration,
                checked = prefs.hapticsGlobal,
                onCheckedChange = { viewModel.update { it.copy(hapticsGlobal = !it.hapticsGlobal) } }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(visible = prefs.hapticsGlobal) {
            Column {
                ExpressiveSection(stringResource(R.string.tactile_details)) {
                    ExpressiveToggleItem(
                        title = stringResource(R.string.timer_ticks),
                        subtitle = stringResource(R.string.subtle_pulse_every_second),
                        icon = Icons.Rounded.Timer,
                        checked = prefs.hapticsTimer,
                        onCheckedChange = { viewModel.update { it.copy(hapticsTimer = !it.hapticsTimer) } }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    ExpressiveToggleItem(
                        title = stringResource(R.string.event_success),
                        subtitle = stringResource(R.string.thump_on_achievements),
                        icon = Icons.Rounded.Celebration,
                        checked = prefs.hapticsEvents,
                        onCheckedChange = { viewModel.update { it.copy(hapticsEvents = !it.hapticsEvents) } }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    ExpressiveToggleItem(
                        title = stringResource(R.string.ui_interaction),
                        subtitle = stringResource(R.string.clicks_on_buttons_and_cards),
                        icon = Icons.Rounded.TouchApp,
                        checked = prefs.hapticsUI,
                        onCheckedChange = { viewModel.update { it.copy(hapticsUI = !it.hapticsUI) } }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )

                    ExpressiveToggleItem(
                        title = stringResource(R.string.alerts_warnings),
                        subtitle = stringResource(R.string.resistive_feel_on_destructive_actions),
                        icon = Icons.Rounded.Warning,
                        checked = prefs.hapticsWarnings,
                        onCheckedChange = { viewModel.update { it.copy(hapticsWarnings = !it.hapticsWarnings) } }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            stringResource(R.string.haptic_preview),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            stringResource(R.string.test_real_time),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val view = LocalView.current
                        val tests = listOf(
                            stringResource(R.string.click) to { HapticHelper.click(view, prefs) },
                            stringResource(R.string.tick) to { HapticHelper.tick(view, prefs) },
                            stringResource(R.string.success) to { HapticHelper.success(view, prefs) },
                            stringResource(R.string.warning) to { HapticHelper.warning(view, prefs) }
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
                        stringResource(R.string.global_haptic_info),
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
fun SettingsMainList(onNavigate: (SettingsPage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ExpressiveSection(stringResource(R.string.general)) {
            ExpressiveItem(stringResource(R.string.basic_options), stringResource(R.string.language_notifications_theme), Icons.Rounded.Tune) { onNavigate(SettingsPage.Basic) }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpressiveItem(stringResource(R.string.backup_restore), stringResource(R.string.export_data_locally), Icons.Rounded.CloudUpload) { onNavigate(SettingsPage.Backup) }
        }

        ExpressiveSection(stringResource(R.string.advanced)) {
            ExpressiveItem(stringResource(R.string.dev_lab_options),
                stringResource(R.string.experimental_features_stats), Icons.Rounded.Science) { onNavigate(SettingsPage.Dev) }
        }

        ExpressiveSection(stringResource(R.string.info)) {
            ExpressiveItem(stringResource(R.string.about_app), stringResource(R.string.credits_faq), Icons.Rounded.Info) { onNavigate(SettingsPage.About) }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpressiveItem(stringResource(R.string.licensing), stringResource(R.string.mit_license), Icons.Rounded.Gavel) { onNavigate(SettingsPage.Licenses) }
        }

        Spacer(modifier = Modifier.height(32.dp))
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
        Text(stringResource(R.string.local_vault), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.vault_temp_info), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = false
        ) { Text(stringResource(R.string.export_data_soon)) }
    }
}

fun getAppVersion(context: Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "Unknown"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevSettings(settingsViewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs by settingsViewModel.preferences.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showNukeSheet by remember { mutableStateOf(false) }

    // Version states
    var habitDbStoredVersion by remember { mutableIntStateOf(0) }
    var settingsDbStoredVersion by remember { mutableIntStateOf(0) }
    var isLoadingVersions by remember { mutableStateOf(true) }

    // Update testing state
    var needsDatabaseUpdate by remember { mutableStateOf<Boolean?>(null) }
    var showUpdateScreen by remember { mutableStateOf(false) }

    // Global Timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // Version Loader
    LaunchedEffect(Unit) {
        isLoadingVersions = true
        withContext(Dispatchers.IO) {
            try {
                habitDbStoredVersion = AppDatabase.getDatabase(context).habitDao().getDbVersion()?.versionCode ?: 0
                settingsDbStoredVersion = SettingsDatabase.getDatabase(context).settingsDao().getSettingsVersion()?.versionCode ?: 0

                val needsHabit = AppDatabase.needsUpdate(context)
                val needsSettings = SettingsDatabase.needsUpdate(context)
                needsDatabaseUpdate = needsHabit || needsSettings
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), modifier = Modifier.size(80.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp)) }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.danger_zone), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                Text(stringResource(R.string.database_reset_warning), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        HapticHelper.warning(view, prefs)
                        context.deleteDatabase("fastquit_db")
                        Process.killProcess(Process.myPid())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.History, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.nuke_habits_history), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        HapticHelper.warning(view, prefs)
                        context.deleteDatabase("settings_db")
                        Process.killProcess(Process.myPid())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.SettingsBackupRestore, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.nuke_all_settings), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {
        ExpressiveSection(stringResource(R.string.build_info)) {
            ExpressiveItem(
                stringResource(R.string.app_version),
                getAppVersion(context),
                Icons.Rounded.Android
            ) {}

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            ExpressiveItem(
                title = stringResource(R.string.habits_db),
                subtitle = if (isLoadingVersions) stringResource(R.string.loading) else stringResource(
                    R.string.schema_v_stored_v, HABIT_DB_VERSION, habitDbStoredVersion
                ),
                icon = Icons.Rounded.Storage
            ) {}

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            ExpressiveItem(
                title = stringResource(R.string.settings_db),
                subtitle = if (isLoadingVersions) stringResource(R.string.loading) else stringResource(
                    R.string.schema_v_stored_v, SETTINGS_DB_VERSION, settingsDbStoredVersion
                ),
                icon = Icons.Rounded.SettingsSystemDaydream
            ) {}

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            ExpressiveItem(
                title = stringResource(R.string.database_migration),
                subtitle = when(needsDatabaseUpdate) {
                    true -> stringResource(R.string.update_required)
                    false -> stringResource(R.string.database_is_up_to_date)
                    else -> stringResource(R.string.checking_status)
                },
                icon = Icons.Rounded.SystemUpdate,
                onClick = { if (needsDatabaseUpdate == true) showUpdateScreen = true }
            )

            ExpressiveSection(title = stringResource(R.string.system_debug)) {
                ExpressiveToggleItem(
                    title = stringResource(R.string.force_update_screen),
                    subtitle = stringResource(R.string.test_the_migration_ui_on_next_launch),
                    icon = Icons.Rounded.SystemUpdate,
                    checked = prefs.forceUpdateScreen,
                    onCheckedChange = { newValue ->
                        settingsViewModel.update { it.copy(forceUpdateScreen = newValue) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.haptic_engine_tester), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val tests = listOf(
                    stringResource(R.string.click) to { HapticHelper.click(view, prefs) },
                    stringResource(R.string.tick) to { HapticHelper.tick(view, prefs) },
                    stringResource(R.string.success) to { HapticHelper.success(view, prefs) },
                    stringResource(R.string.warning) to { HapticHelper.warning(view, prefs) }
                )
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tests) { (label, action) ->
                        FilledTonalButton(onClick = action, shape = RoundedCornerShape(12.dp)) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        ExpressiveSection(stringResource(R.string.lab_tools)) {
            val scope = rememberCoroutineScope()
            ExpressiveItem(stringResource(R.string.test_notification),
                stringResource(R.string.sends_a_dummy_alert), Icons.Rounded.Notifications) {
                scope.launch { sendNotification(context,
                    context.getString(R.string.test), context.getString(R.string.notification_test)) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.runtime_stats), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.pkg_title, context.packageName), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.timestamp, now), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.uptime_s, SystemClock.elapsedRealtime() / 1000), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = SuccessGreen)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { showNukeSheet = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.DeleteForever, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.open_destroyer_menu), fontWeight = FontWeight.Bold)
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
                            contentDescription = stringResource(R.string.my_username),
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.my_username), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(stringResource(R.string.my_role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(modifier = Modifier.width(24.dp))
                    Text(stringResource(R.string.amperstand), style = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.surfaceVariant))
                    Spacer(modifier = Modifier.width(24.dp))

                    // AI
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(70.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(32.dp)) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.helper), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(stringResource(R.string.helper_role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.crafted_with_love), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.ai_debug), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.faq), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(16.dp))

        ExpressiveSection(stringResource(R.string.questions)) {
            ExpandableItem(
                stringResource(R.string.q_1),
                stringResource(R.string.a_1)
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpandableItem(
                stringResource(R.string.q_2),
                stringResource(R.string.a_2)
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            ExpandableItem(
                stringResource(R.string.q_3),
                stringResource(R.string.a_3)
            )
        }
    }
}

@Composable
fun LicenseSettings() {
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.mit_license), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.copyright), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.mit_license_text).trimIndent(),
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
            .semantics {
                toggleableState = ToggleableState(checked)
                role = Role.Switch
            }
            .clickable(
                role = Role.Switch,
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
                    contentDescription = null,
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
        Switch(
            checked = checked,
            onCheckedChange = null // Keep null to prevent conflicting interactions
        )
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

// ===================== HOME SCREEN =====================

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FastQuitHomeScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val habitList by viewModel.habits.collectAsState()

    // Initial State is loading dots
    var quote by remember { mutableStateOf(ApiQuote("...", "...")) }

    var refreshCount by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val currentLangCode = LanguageHelper.getCurrentCode()

    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }

    // UPDATED QUOTE FETCHING
    LaunchedEffect(refreshCount) {
        try {
            // Fetch asynchronously on IO thread
            quote = withContext(Dispatchers.IO) {
                QuoteManager.getMotivationalQuote(currentLangCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                onClick = { refreshCount++ },
                modifier = Modifier.fillMaxWidth().animateContentSize().zIndex(1f)
            ) {
                Column(modifier = Modifier.statusBarsPadding().padding(24.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = quote,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                                        .togetherWith(fadeOut(animationSpec = tween(90)))
                                },
                                label = "QuoteAnim"
                            ) { targetQuote ->
                                Column {
                                    Text("\"${targetQuote.quote}\"", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("- ${targetQuote.author}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        Box(modifier = Modifier.offset(y = (-8).dp)) {
                            FilledTonalIconButton(
                                onClick = { navController.navigate("settings") },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showBottomSheet = true },
                icon = { Icon(Icons.Default.Add, stringResource(R.string.add)) },
                text = { Text(stringResource(R.string.new_habit)) },
                expanded = true
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                refreshCount++
                Handler(Looper.getMainLooper()).postDelayed({ isRefreshing = false }, 1000)
            },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            indicator = { PullToRefreshDefaults.LoadingIndicator(state = pullState, isRefreshing = isRefreshing, modifier = Modifier.align(Alignment.TopCenter)) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                items(items = habitList, key = { it.id }) { habit ->
                    val context = LocalContext.current
                    LaunchedEffect(now) {
                        val seconds = (now - habit.lastResetTime) / 1000
                        checkAndNotifyAchievements(context, habit.name, habit.id, seconds, habit.completions, habit.targetSeconds)
                    }
                    Box(modifier = Modifier.animateItem()) {
                        HabitCard(
                            habit = habit,
                            now = now,
                            navController = navController,
                            onDelete = { viewModel.deleteHabit(habit.id) },
                            onMoveUp = { viewModel.moveHabit(habit.id, true) },
                            onMoveDown = { viewModel.moveHabit(habit.id, false) }
                        )
                    }
                }
                if (habitList.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_habbits_added),
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NewHabitSheet(
                onDismiss = { showBottomSheet = false },
                onSave = { name, icon, amount, unit, start ->
                    viewModel.addHabit(name, icon, amount, unit, start)
                    showBottomSheet = false
                }
            )
        }
    }
}
object NetworkModule {
    // 1. ZenQuotes for Content (English)
    val quoteApi: QuoteApi = Retrofit.Builder()
        .baseUrl("https://zenquotes.io/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(QuoteApi::class.java)

    // 2. MyMemory for Translation
    val translationApi: TranslationApi = Retrofit.Builder()
        .baseUrl("https://api.mymemory.translated.net/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TranslationApi::class.java)
}
object LocalQuotes {
    val fallback = listOf(
        ApiQuote("The only way out is through.", "Robert Frost"),
        ApiQuote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche"),
        ApiQuote("Discipline is choosing between what you want now and what you want most.", "Abraham Lincoln"),
        ApiQuote("We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Aristotle")
    )
    val czechFallback = listOf(
        ApiQuote("Jediná cesta ven je skrz.", "Robert Frost"),
        ApiQuote("Kdo má PROČ žít, snese téměř každé JAK.", "Friedrich Nietzsche"),
        ApiQuote("Disciplína je volba mezi tím, co chceš teď, a tím, co chceš nejvíc.", "Abraham Lincoln"),
        ApiQuote("Jsme tím, co opakovaně děláme. Dokonalost není čin, ale zvyk.", "Aristoteles")
    )
}

object QuoteManager {
    suspend fun getMotivationalQuote(langCode: String): AnimationState<Float, AnimationVector1D> {
        // 1. Fetch English Quote
        var quote = try {
            val response = NetworkModule.quoteApi.getRandomQuotes()
            if (response.isNotEmpty()) response[0] else LocalQuotes.fallback.random()
        } catch (e: Exception) {
            LocalQuotes.fallback.random()
        }

        // 2. If target is Czech, Attempt Translation
        if (langCode == "cs") {
            return try {
                val translatedText = NetworkModule.translationApi.translate(
                    text = quote.quote,
                    langpair = "en|cs"
                ).responseData.translatedText

                // Return translated text with original author
                quote.copy(quote = translatedText)
            } catch (e: Exception) {
                // Translation failed -> Use Local Czech Fallback to avoid showing English
                LocalQuotes.czechFallback.random()
            }
        }
        return quote
    }
}

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
                        Text(stringResource(habit.targetLabelRes, habit.goalLabel).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
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
                        CircularWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(100.dp),
                            color = waveColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                            amplitude = { 1f },
                            stroke = Stroke(
                                width = with(density) { 8.dp.toPx() },
                                cap = StrokeCap.Round
                            ),
                            trackStroke = Stroke(
                                width = with(density) { 8.dp.toPx() },
                                cap = StrokeCap.Round)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isComplete) {
                                Icon(Icons.Rounded.Check, null, tint = waveColor, modifier = Modifier.size(32.dp))
                            } else {
                                Text("${(animatedProgress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Text(stringResource(habit.targetLabelRes, habit.goalLabel).replace(
                                stringResource(R.string.goal_doubledot), ""), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, offset = DpOffset(x = 200.dp, y = 0.dp), shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { showMenu = false; onMoveUp() })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { showMenu = false; onMoveDown() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

// --- NEW HABIT SHEET (Date + Time Picker) ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewHabitSheet(onDismiss: () -> Unit, onSave: (String, String, Int, String, Long) -> Unit) {

    val context = LocalContext.current

    var name by remember { mutableStateOf("") }

    // Fix: Use context.getString() instead of stringResource()
    var selectedIconName by remember { mutableStateOf(context.getString(R.string.energy)) }

    var showIconStudio by remember { mutableStateOf(false) }
    var goalAmount by remember { mutableStateOf("7") }

    // Fix: Use context.getString() here as well
    var selectedUnit by remember { mutableStateOf(context.getString(R.string.c_days)) }

    // DATE & TIME STATE
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isManualDate by remember { mutableStateOf(false) }
    var tempDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) } // Temp store date before adding time
    var selectedStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) } // Final Result

    val units = listOf(
        stringResource(R.string.c_seconds),
        stringResource(R.string.c_minutes),
        stringResource(R.string.c_hours),
        stringResource(R.string.c_days),
        stringResource(R.string.c_weeks),
        stringResource(R.string.c_months),
        stringResource(R.string.c_years)
    )
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
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
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
                        Toast.makeText(context,
                            context.getString(R.string.future_pick_error), Toast.LENGTH_SHORT).show()
                        // Reset to Now
                        selectedStartTime = System.currentTimeMillis()
                        isManualDate = false
                    } else {
                        selectedStartTime = finalTime
                        isManualDate = true
                    }
                    showTimePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.select_time), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
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
                    Text(stringResource(R.string.icon_library), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(
                        stringResource(R.string.search)
                    ) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
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
                        Column(modifier = Modifier.weight(1f)) { Text(stringResource(R.string.selected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Text(tempSelectedIcon, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1) }
                        Button(onClick = { selectedIconName = tempSelectedIcon; showIconStudio = false }, modifier = Modifier.height(48.dp)) { Text(
                            stringResource(R.string.confirm)
                        ) }
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
        Text(stringResource(R.string.new_commitment), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(
            stringResource(
                R.string.habit_name
            )) }, placeholder = { Text(stringResource(R.string.example_new_habit_temp)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(modifier = Modifier.height(24.dp))

        // DISPLAY DATE + TIME
        val dateLabel = if (isManualDate) SimpleDateFormat(stringResource(R.string.timeformat), Locale.getDefault()).format(Date(selectedStartTime)) else stringResource(
            R.string.starts_now
        )

        OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DateRange, null); Spacer(modifier = Modifier.width(16.dp)); Column { Text(
                stringResource(R.string.start_date), style = MaterialTheme.typography.labelSmall); Text(dateLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) } }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.icon), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
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
            item { Surface(onClick = { showIconStudio = true }, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreHoriz,
                stringResource(R.string.all), tint = MaterialTheme.colorScheme.onSecondaryContainer) } } }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.goal_duration), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
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
            .height(50.dp), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.commit)) }
    }
}

data class ApiQuote(
    @SerializedName("q") val quote: String,
    @SerializedName("a") val author: String
)

data class TranslationResponse(val responseData: ResponseData)
data class ResponseData(val translatedText: String)

interface QuoteApi {
    @GET("random") // ZenQuotes: https://zenquotes.io/api/random
    suspend fun getRandomQuotes(): List<ApiQuote>
}

interface TranslationApi {
    @GET("get") // MyMemory: https://api.mymemory.translated.net/get?q=...&langpair=en|cs
    suspend fun translate(@Query("q") text: String, @Query("langpair") langpair: String): TranslationResponse
}