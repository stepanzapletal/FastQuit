package com.stepan.fastquit

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// COLORS
//private val SuccessGreen = Color(0xFF00D158)
private val Gold = Color(0xFFFFD700)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun HabitDetailScreen(
    navController: NavController,
    habitName: String,
    viewModel: DetailViewModel
) {
    val habitEntity by viewModel.habit.collectAsState()
    val historyList by viewModel.history.collectAsState()
    val context = LocalContext.current

    // Global Timer
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    val start = habitEntity?.lastResetTime ?: now
    val diffSeconds = (now - start) / 1000

    // Live Notification Check
    LaunchedEffect(now, habitEntity?.completions) {
        if(habitEntity != null) {
            checkAndNotifyAchievements(
                context,
                habitEntity!!.name,
                habitEntity!!.id,
                diffSeconds,
                habitEntity!!.completions,
                habitEntity!!.targetSeconds
            )
        }
    }

    // Refresh State
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    // PAGER STATE
    val tabs = listOf(
        stringResource(R.string.overview),
        stringResource(R.string.statistics),
        stringResource(R.string.history),
        stringResource(R.string.achievements)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(habitEntity?.name ?: habitName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                    navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.back)) } },
                    actions = { IconButton(onClick = { }) { Icon(Icons.Default.Share, stringResource(R.string.share)) } }
                )

                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                                height = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if(pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ isRefreshing = false }, 800)
            },
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            indicator = { PullToRefreshDefaults.LoadingIndicator(state = pullState, isRefreshing = isRefreshing, modifier = Modifier.align(Alignment.TopCenter)) }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> OverviewTab(habitEntity, now, viewModel)
                        1 -> StatisticsTab(habitEntity, historyList, diffSeconds)
                        2 -> HistoryTab(historyList)
                        3 -> AchievementsTab(diffSeconds, habitEntity?.completions ?: 0)
                    }
                }
            }
        }
    }
}

// ===================== TABS =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetBottomSheet(
    habitName: String,
    currentStreak: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start
        ) {
            // Header row with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        stringResource(R.string.reset_habit),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        habitName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.current_streak),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        currentStreak,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Warning section
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        stringResource(R.string.reset_string),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Reset button (primary action)
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.reset_progress), fontWeight = FontWeight.Bold)
                    }
                }

                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.cancel), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalResetChoiceSheet(
    habitName: String,
    currentStreak: String,
    onDismiss: () -> Unit,
    onResetTimer: () -> Unit,
    onKeepCurrentTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedOption by remember { mutableIntStateOf(1) } // Default to "Continue streak" (1)
    val isConfirmEnabled by remember(selectedOption) { derivedStateOf { selectedOption != -1 } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start
        ) {
            // Header with Material 3 expressive icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        stringResource(R.string.new_milestone),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        habitName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Current streak display with Material 3 expressive card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.current_streak),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            currentStreak,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section title
            Text(
                stringResource(R.string.continue_your_journey),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(R.string.goal_track_string),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Option 1: Continue Streak (DEFAULT)
            Surface(
                onClick = { selectedOption = 1 },
                shape = RoundedCornerShape(20.dp),
                color = if (selectedOption == 1) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                tonalElevation = if (selectedOption == 1) 4.dp else 2.dp,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (selectedOption == 1) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOption == 1,
                        onClick = { selectedOption = 1 },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.secondary,
                            unselectedColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.continue_streak_space),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.keep_current_streak_going),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Timeline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Fresh Start
            Surface(
                onClick = { selectedOption = 0 },
                shape = RoundedCornerShape(20.dp),
                color = if (selectedOption == 0) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                tonalElevation = if (selectedOption == 0) 4.dp else 2.dp,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (selectedOption == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOption == 0,
                        onClick = { selectedOption = 0 },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.fresh_start),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.begin_new_goal_from_today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subtle helper text (Material 3 style - no background, just text)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    stringResource(R.string.streak_continue_types),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons row (Material 3 expressive)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Cancel button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
                }

                // Confirm button
                Button(
                    onClick = {
                        when (selectedOption) {
                            0 -> onResetTimer()
                            1 -> onKeepCurrentTime()
                        }
                        onDismiss()
                    },
                    enabled = isConfirmEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(stringResource(R.string.continue_str), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OverviewTab(habitEntity: HabitEntity?, now: Long, viewModel: DetailViewModel) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel()
    val prefs by settingsViewModel.preferences.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    val start = habitEntity?.lastResetTime ?: now
    val targetSeconds = habitEntity?.targetSeconds ?: 604800L
    val goalLabel = habitEntity?.goalLabel ?: stringResource(R.string._7_days)
    val diffSeconds = (now - start) / 1000
    val safeGoal = if (targetSeconds == 0L) 1L else targetSeconds
    val rawProgress = (diffSeconds.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
    val isComplete = rawProgress >= 1f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(1000, easing = LinearEasing),
        label = stringResource(R.string.progress)
    )

    // Reset Dialog State
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(now) {
        HapticHelper.tick(view, prefs)
    }
    LaunchedEffect(isComplete) {
        if (isComplete) {
            HapticHelper.success(view, prefs)
        }
    }

    val (displayCount, displayUnit) = when {
        diffSeconds < 60 -> diffSeconds to stringResource(R.string.seconds)
        diffSeconds < 3600 -> TimeUnit.SECONDS.toMinutes(diffSeconds) to stringResource(R.string.minutes)
        diffSeconds < 86400 -> TimeUnit.SECONDS.toHours(diffSeconds) to stringResource(R.string.hours)
        else -> TimeUnit.SECONDS.toDays(diffSeconds) to stringResource(R.string.days)
    }

    // -- DIALOG & SHEET STATE --
    var showExtendSheet by remember { mutableStateOf(false) }
    var showEditTargetSheet by remember { mutableStateOf(false) }

    // Intermediate state for logic
    var pendingAmount by remember { mutableIntStateOf(0) }
    var pendingUnit by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf("") } // "EXTEND" or "EDIT"
    var showResetConfirmation by remember { mutableStateOf(false) }

    if (showResetConfirmation) {
        GoalResetChoiceSheet(
            habitName = habitEntity?.name ?: stringResource(R.string.habit),
            currentStreak = formatDurationFriendly(diffSeconds),
            onDismiss = { showResetConfirmation = false },
            onResetTimer = {
                if (pendingAction == "EXTEND") {
                    viewModel.extendGoal(pendingAmount, pendingUnit, resetTimer = true)
                } else {
                    viewModel.updateTarget(pendingAmount, pendingUnit, resetTimer = true)
                }
            },
            onKeepCurrentTime = {
                if (pendingAction == "EXTEND") {
                    viewModel.extendGoal(pendingAmount, pendingUnit, resetTimer = false)
                } else {
                    viewModel.updateTarget(pendingAmount, pendingUnit, resetTimer = false)
                }
            }
        )
    }

    if (showExtendSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExtendSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ExtendGoalSheetContent(
                title = stringResource(R.string.target_reached),
                subtitle = stringResource(R.string.milestone_next),
                icon = Icons.Rounded.EmojiEvents,
                iconColor = SuccessGreen,
                onDismiss = { showExtendSheet = false },
                onConfirm = { amount, unit ->
                    pendingAmount = amount
                    pendingUnit = unit
                    pendingAction = context.getString(R.string.milestone_next)
                    showExtendSheet = false
                    showResetConfirmation = true
                }
            )
        }
    }

    if (showEditTargetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditTargetSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ExtendGoalSheetContent(
                title = stringResource(R.string.edit_target),
                subtitle = stringResource(R.string.goal_dest),
                icon = Icons.Rounded.Edit,
                iconColor = MaterialTheme.colorScheme.primary,
                onDismiss = { showEditTargetSheet = false },
                onConfirm = { amount, unit ->
                    pendingAmount = amount
                    pendingUnit = unit
                    pendingAction = "EDIT"
                    showEditTargetSheet = false
                    showResetConfirmation = true
                }
            )
        }
    }

    if (showResetDialog) {
        ResetBottomSheet(
            habitName = habitEntity?.name ?: stringResource(R.string.habit),
            currentStreak = formatDurationFriendly(diffSeconds),
            onDismiss = { showResetDialog = false },
            onConfirm = {
                HapticHelper.warning(view, prefs)
                viewModel.resetTimer()
                showResetDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp) // Tighter spacing for Expressive grouping
    ) {
        // 1. MAIN PROGRESS CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(32.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val waveColor = if (isComplete) SuccessGreen else MaterialTheme.colorScheme.primary

                CircularWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(300.dp),
                    color = waveColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    amplitude = { 1.0f },
                    wavelength = 120.dp,
                    stroke = Stroke(width = with(density) { 20.dp.toPx() }, cap = StrokeCap.Round),
                    trackStroke = Stroke(width = with(density) { 20.dp.toPx() }, cap = StrokeCap.Round)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.streak), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$displayCount", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 80.sp), color = MaterialTheme.colorScheme.onSurface)
                    Text(displayUnit, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = waveColor)
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        onClick = { showEditTargetSheet = true },
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            stringResource(R.string.goal, goalLabel.uppercase()),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. NEW: MOTIVATIONAL CAROUSEL (Snappy 2s cycle)
        MotivationalCarousel(progress = rawProgress)

        // 3. CLOCK CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val hours = (diffSeconds % 86400) / 3600; val minutes = (diffSeconds % 3600) / 60; val seconds = diffSeconds % 60
                TimeTickerUnit(value = hours, label = stringResource(R.string.hours)); Text(":", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline))
                TimeTickerUnit(value = minutes, label = stringResource(R.string.mins)); Text(":", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline))
                TimeTickerUnit(value = seconds, label = stringResource(R.string.secs), highlight = true)
            }
        }

        // 4. CONSISTENCY / CALENDAR
        Column {
            Text(stringResource(R.string.consistency), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 16.dp))
            CalendarCard(habitStartDate = start, targetSeconds = targetSeconds)
        }

        Spacer(modifier = Modifier.height(60.dp))

        // 5. ACTION BUTTONS
        if (isComplete) {
            Button(
                onClick = { showExtendSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Rounded.FastForward, null)
                Spacer(modifier = Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.continue_journey), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                    Text(stringResource(R.string.reach_target), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.RestartAlt, null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.reset_progress), fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Rounded.Warning, null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.reset_timer), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MotivationalCarousel(progress: Float) {
    val context = LocalContext.current
    val phrases = remember(progress) {
        when {
            progress >= 1f -> listOf(
                context.getString(R.string.motivate_goal_achieved),
                context.getString(R.string.motivate_absolute_legend),
                context.getString(R.string.motivate_milestone_reached),
                context.getString(R.string.motivate_new_peak_unlocked)
            )
            progress >= 0.8f -> listOf(
                context.getString(R.string.motivate_almost_there),
                context.getString(R.string.motivate_the_final_stretch),
                context.getString(R.string.motivate_don_t_stop_now),
                context.getString(R.string.motivate_finish_strong)
            )
            progress >= 0.5f -> listOf(
                context.getString(R.string.motivate_halfway_point),
                context.getString(R.string.motivate_urge_defeated),
                context.getString(R.string.motivate_momentum_building),
                context.getString(R.string.motivate_keep_it_up)
            )
            else -> listOf(
                context.getString(R.string.motivate_keep_going),
                context.getString(R.string.motivate_one_day_at_a_time),
                context.getString(R.string.motivate_building_habit),
                context.getString(R.string.motivate_stay_focused)
            )
        }
    }

    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(phrases) {
        while (true) {
            delay(2000)
            index = (index + 1) % phrases.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = phrases[index],
            transitionSpec = {
                (slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { it } + fadeIn())
                    .togetherWith(slideOutVertically { -it } + fadeOut())
            },
            label = stringResource(R.string.motivationaltext)
        ) { text ->
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 4.sp),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// EXPRESSIVE BOTTOM SHEET (Reused)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtendGoalSheetContent(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(context.getString(R.string.c_days)) }
    val units = listOf(
        stringResource(R.string.c_seconds),
        stringResource(R.string.c_minutes),
        stringResource(R.string.c_hours),
        stringResource(R.string.c_days),
        stringResource(R.string.c_weeks),
        stringResource(R.string.c_months),
        stringResource(R.string.c_years)
    )

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp)
        .navigationBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = iconColor.copy(alpha = 0.2f), modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(40.dp)) }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.all { char -> char.isDigit() }) amount = it },
            label = { Text(stringResource(R.string.amount)) },
            placeholder = { Text(stringResource(R.string.e_g_14)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            units.forEach { unit ->
                FilterChip(selected = selectedUnit == unit, onClick = { selectedUnit = unit }, label = { Text(unit) }, shape = RoundedCornerShape(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                val amt = amount.toIntOrNull()
                if (amt != null && amt > 0) onConfirm(amt, selectedUnit)
            },
            enabled = amount.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.set_goal))
        }
    }
}

@Composable
fun StatisticsTab(habitEntity: HabitEntity?, history: List<ResetHistoryEntity>, currentStreakSeconds: Long) {
    val maxStreak = (history.maxOfOrNull { it.durationSeconds } ?: 0L).coerceAtLeast(currentStreakSeconds)
    val avgStreak = if (history.isNotEmpty()) history.map { it.durationSeconds }.average().toLong() else 0L
    val minStreak = history.minOfOrNull { it.durationSeconds } ?: 0L
    val totalResets = history.size

    val goalsCompleted = habitEntity?.completions ?: 0
    val targetChanges = habitEntity?.targetChangesCount ?: 0

    val stats = listOf(
        stringResource(R.string.goals_completed) to "$goalsCompleted",
        stringResource(R.string.target_changes) to "$targetChanges",
        stringResource(R.string.max_streak) to formatDurationFriendly(maxStreak),
        stringResource(R.string.avg_streak) to formatDurationFriendly(avgStreak),
        stringResource(R.string.min_streak) to formatDurationFriendly(minStreak),
        stringResource(R.string.total_resets) to "$totalResets"
    )

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(stats) { (label, value) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(history: List<ResetHistoryEntity>) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(
            stringResource(R.string.no_resets), color = MaterialTheme.colorScheme.outline) }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(history) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) } }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.reset), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(SimpleDateFormat(stringResource(R.string.timeformat), Locale.getDefault()).format(Date(entry.endDate)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.lasted), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(formatDurationFriendly(entry.durationSeconds), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AchievementsTab(currentSeconds: Long, completions: Int) {
    val context = LocalContext.current
    data class MilestoneUI(val label: String, val target: Long, val icon: ImageVector, val type: String)

    val timeMilestones = listOf(
        MilestoneUI(stringResource(R.string.beginner), 0, Icons.Rounded.Start, "TIME"),
        MilestoneUI(stringResource(R.string._24_hours), 86400, Icons.Rounded.LooksOne, "TIME"),
        MilestoneUI(stringResource(R.string._3_days), 259200, Icons.Rounded.Looks3, "TIME"),
        MilestoneUI(stringResource(R.string._1_week), 604800, Icons.Rounded.CalendarViewWeek, "TIME"),
        MilestoneUI(stringResource(R.string._1_month), 2592000, Icons.Rounded.CalendarMonth, "TIME"),
        MilestoneUI(stringResource(R.string._3_months), 7776000, Icons.Rounded.Filter3, "TIME"),
        MilestoneUI(stringResource(R.string._6_months), 15552000, Icons.Rounded.Filter6, "TIME"),
        MilestoneUI(stringResource(R.string._1_year), 31536000, Icons.Rounded.Cake, "TIME")
    )

    val completionMilestones = listOf(
        MilestoneUI(stringResource(R.string.goal_crusher_i), 1, Icons.Rounded.WorkspacePremium, "GOAL"),
        MilestoneUI(stringResource(R.string.goal_crusher_ii), 3, Icons.Rounded.WorkspacePremium, "GOAL"),
        MilestoneUI(stringResource(R.string.goal_crusher_iii), 5, Icons.Rounded.WorkspacePremium, "GOAL"),
        MilestoneUI(stringResource(R.string.elite), 10, Icons.Rounded.Diamond, "GOAL"),
        MilestoneUI(stringResource(R.string.master), 25, Icons.Rounded.MilitaryTech, "GOAL")
    )

    val allMilestones = (timeMilestones + completionMilestones)
    val nextTimeTarget = timeMilestones.firstOrNull { currentSeconds < it.target }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(allMilestones) { m ->
            val isGoalType = m.type == "GOAL"
            val isUnlocked = if (isGoalType) completions >= m.target else currentSeconds >= m.target

            val isCurrentTarget = if (isGoalType) {
                !isUnlocked && (completions < m.target) && (allMilestones.filter { it.type == "GOAL" && it.target < m.target }.all { completions >= it.target })
            } else {
                (m == nextTimeTarget)
            }

            val rawProgress = if (isGoalType) {
                (completions.toFloat() / m.target.toFloat()).coerceIn(0f, 1f)
            } else {
                (currentSeconds.toFloat() / m.target.toFloat()).coerceIn(0f, 1f)
            }

            val visualProgress = if (isCurrentTarget) rawProgress.coerceAtLeast(0.05f) else rawProgress
            val animatedProgress by animateFloatAsState(visualProgress, label = "")

            val statusText = if (isUnlocked) context.getString(R.string.unlocked) else {
                if (isGoalType) {
                    context.getString(R.string.goals_met, completions, m.target)
                } else {
                    val remainingSeconds = m.target - currentSeconds
                    val futureMillis = System.currentTimeMillis() + (remainingSeconds * 1000)
                    context.getString(R.string.unlocks) + SimpleDateFormat(context.getString(R.string.mmm_dd_hh_mm), Locale.getDefault()).format(Date(futureMillis))
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(20.dp),
                border = if(isCurrentTarget && !isGoalType) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (isUnlocked) SuccessGreen else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(m.icon, null, tint = if (isUnlocked) Color.White else MaterialTheme.colorScheme.outline)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = if(isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)

                        if (!isUnlocked) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isCurrentTarget && !isGoalType) {
                                LinearWavyProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                                    amplitude = { 0.2f },
                                    wavelength = 16.dp,
                                    waveSpeed = 16.dp
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    strokeCap = StrokeCap.Round,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                    if (isUnlocked) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen)
                    }
                }
            }
        }
    }
}

// --- HELPERS ---
fun formatDurationFriendly(seconds: Long): String {
    if (seconds == 0L) return "0s"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m ${seconds % 60}s"
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun TimeTickerUnit(value: Long, label: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(16.dp), color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier
            .width(80.dp)
            .height(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = value, transitionSpec = { slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut() }, label = "Timer") { targetCount ->
                    Text(text = String.format("%02d", targetCount), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp)); Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun CalendarCard(habitStartDate: Long, targetSeconds: Long) {
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val startDate = Instant.ofEpochMilli(habitStartDate).atZone(zone).toLocalDate()
    val goalDate = Instant.ofEpochMilli(habitStartDate).atZone(zone).plusSeconds(targetSeconds).toLocalDate()
    val monthName = currentYearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val year = currentYearMonth.year
    val firstDayOfMonth = currentYearMonth.atDay(1).dayOfWeek.value
    val daysInMonth = currentYearMonth.lengthOfMonth()
    val startOffset = firstDayOfMonth - 1
    val totalSlots = 42
    val calendarGrid = buildList { repeat(startOffset) { add(null) }; (1..daysInMonth).forEach { add(it) }; val remaining = totalSlots - size; if(remaining > 0) repeat(remaining) { add(null) } }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prev") }
                Text("$monthName $year", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                IconButton(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf(
                stringResource(R.string.monday),
                stringResource(R.string.tuesday),
                stringResource(R.string.wednesday),
                stringResource(R.string.thursday),
                stringResource(R.string.friday),
                stringResource(R.string.saturday),
                stringResource(R.string.sunday)
            ).forEach { day -> Text(text = day, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline) } }
            Spacer(modifier = Modifier.height(12.dp))
            val weeks = calendarGrid.chunked(7)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                weeks.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        week.forEach { dayNum ->
                            if (dayNum == null) { Spacer(modifier = Modifier.size(32.dp)) }
                            else {
                                val date = currentYearMonth.atDay(dayNum)
                                val isToday = date.isEqual(today); val isStart = date.isEqual(startDate); val isGoal = date.isEqual(goalDate)
                                val isBetweenStartAndNow = date.isAfter(startDate) && (date.isBefore(today) || date.isEqual(today))
                                Box(modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isStart -> MaterialTheme.colorScheme.primary; isGoal -> MaterialTheme.colorScheme.tertiary; isBetweenStartAndNow -> MaterialTheme.colorScheme.primaryContainer; else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isToday) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape
                                        ) else Modifier
                                    ), contentAlignment = Alignment.Center) {
                                    if (isGoal) Icon(Icons.Default.Flag, null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(16.dp)) else Text(text = dayNum.toString(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isToday || isStart) FontWeight.Bold else FontWeight.Normal), color = when { isStart -> MaterialTheme.colorScheme.onPrimary; isGoal -> MaterialTheme.colorScheme.onTertiary; isBetweenStartAndNow -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSurface })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}