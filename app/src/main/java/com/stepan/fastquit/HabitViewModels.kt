package com.stepan.fastquit

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Extension to convert DB Entity to UI Model
fun HabitEntity.toUiModel(): HabitModel {
    return HabitModel(
        id = this.id,
        name = this.name,
        icon = IconMapper.getIcon(this.iconName),
        lastResetTime = this.lastResetTime,
        lastEventTitleRes = R.string.c_streak,
        targetSeconds = this.targetSeconds,
        targetLabelRes = R.string.c_goal,
        goalLabel = this.goalLabel,
        completions = this.completions,
        targetChangesCount = this.targetChangesCount
    )
}

// ================== MAIN VIEW MODEL ==================
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).habitDao()
    private val _rawHabits = dao.getAllHabits().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val habits: StateFlow<List<HabitModel>> = _rawHabits
        .map { list -> list.map { it.toUiModel() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addHabit(name: String, icon: String, amount: Int, unit: String, startTime: Long) {
        viewModelScope.launch {
            // Fix: Pass context to helper
            val totalSeconds: Long = calculateSeconds(amount, unit, getApplication())
            val label = "$amount $unit"
            val nextIndex = (_rawHabits.value.maxOfOrNull { it.sortIndex } ?: -1) + 1

            dao.insert(HabitEntity(
                name = name, iconName = icon, startTime = startTime, lastResetTime = startTime,
                targetSeconds = totalSeconds, goalLabel = label, sortIndex = nextIndex
            ))
        }
    }

    fun deleteHabit(id: Int) {
        viewModelScope.launch {
            val habit = dao.getHabitById(id)
            if (habit != null) dao.delete(habit)
        }
    }

    fun moveHabit(id: Int, moveUp: Boolean) {
        viewModelScope.launch {
            val currentList = _rawHabits.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index == -1) return@launch
            val swapIndex = if (moveUp) index - 1 else index + 1
            if (swapIndex in 0 until currentList.size) {
                val itemA = currentList[index]
                val itemB = currentList[swapIndex]
                val newA = itemA.copy(sortIndex = itemB.sortIndex)
                val newB = itemB.copy(sortIndex = itemA.sortIndex)
                dao.updateHabits(listOf(newA, newB))
            }
        }
    }
}

// ================== SETTINGS VIEW MODEL ==================
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = SettingsDatabase.getDatabase(application).settingsDao()

    val preferences = dao.getPreferences()
        .map { it ?: UserPreferences() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun update(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            val current = preferences.value
            val newPrefs = transform(current)
            dao.setPreferences(newPrefs)
        }
    }
}

// ================== DETAIL VIEW MODEL ==================
class DetailViewModel(application: Application, private val habitId: Int) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).habitDao()

    val habit: StateFlow<HabitEntity?> = dao.getHabitFlow(habitId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val history: StateFlow<List<ResetHistoryEntity>> = dao.getHistoryForHabit(habitId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val goalChanges: StateFlow<List<GoalChangeHistoryEntity>> = dao.getGoalChangesForHabit(habitId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Essential Functions for Detail Screen ---

    fun deleteHabit() {
        viewModelScope.launch {
            val current = dao.getHabitById(habitId)
            if (current != null) dao.delete(current)
        }
    }

    fun updateDetails(newName: String, newIcon: String) {
        viewModelScope.launch {
            val current = dao.getHabitById(habitId)
            current?.let {
                dao.update(it.copy(name = newName, iconName = newIcon))
            }
        }
    }

    fun resetTimer() {
        viewModelScope.launch {
            val current = dao.getHabitById(habitId)
            current?.let { habit ->
                val now = System.currentTimeMillis()
                val durationSecs = (now - habit.lastResetTime) / 1000
                if (durationSecs > 0) {
                    dao.insertHistory(ResetHistoryEntity(
                        habitId = habit.id,
                        startDate = habit.lastResetTime,
                        endDate = now,
                        durationSeconds = durationSecs
                    ))
                }
                dao.update(habit.copy(lastResetTime = now))
            }
        }
    }

    fun extendGoal(amount: Int, unit: String, resetTimer: Boolean) {
        viewModelScope.launch {
            val current = dao.getHabitById(habitId)
            current?.let { habit ->
                // Fix: Removed .toInt() causing crash, added context
                val newTarget = calculateSeconds(amount, unit, getApplication())
                val newLabel = "$amount $unit"
                val newResetTime = if (resetTimer) System.currentTimeMillis() else habit.lastResetTime

                dao.insertGoalChange(GoalChangeHistoryEntity(
                    habitId = habit.id,
                    changeDate = System.currentTimeMillis(),
                    oldTargetSeconds = habit.targetSeconds,
                    newTargetSeconds = newTarget,
                    oldGoalLabel = habit.goalLabel,
                    newGoalLabel = newLabel,
                    changeType = "EXTEND",
                    resetTimer = resetTimer
                ))

                dao.update(habit.copy(
                    targetSeconds = newTarget,
                    goalLabel = newLabel,
                    completions = habit.completions + 1,
                    lastResetTime = newResetTime
                ))
            }
        }
    }

    fun updateTarget(amount: Int, unit: String, resetTimer: Boolean) {
        viewModelScope.launch {
            val current = dao.getHabitById(habitId)
            current?.let { habit ->
                // Fix: Removed .toInt() causing crash, added context
                val newTarget = calculateSeconds(amount, unit, getApplication())
                val newLabel = "$amount $unit"
                val newResetTime = if (resetTimer) System.currentTimeMillis() else habit.lastResetTime

                dao.insertGoalChange(GoalChangeHistoryEntity(
                    habitId = habit.id,
                    changeDate = System.currentTimeMillis(),
                    oldTargetSeconds = habit.targetSeconds,
                    newTargetSeconds = newTarget,
                    oldGoalLabel = habit.goalLabel,
                    newGoalLabel = newLabel,
                    changeType = "UPDATE",
                    resetTimer = resetTimer
                ))

                dao.update(habit.copy(
                    targetSeconds = newTarget,
                    goalLabel = newLabel,
                    targetChangesCount = habit.targetChangesCount + 1,
                    lastResetTime = newResetTime
                ))
            }
        }
    }
}

class DetailViewModelFactory(private val app: Application, private val id: Int) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailViewModel(app, id) as T
}

// ================== HELPER ==================

// Updated to use Context for reliable String matching across languages
fun calculateSeconds(amount: Int, unit: String, context: Context): Long {
    val res = context.resources
    return when(unit) {
        res.getString(R.string.c_seconds) -> amount.toLong()
        res.getString(R.string.c_minutes) -> amount * 60L
        res.getString(R.string.c_hours)   -> amount * 3600L
        res.getString(R.string.c_days)    -> amount * 86400L
        res.getString(R.string.c_weeks)   -> amount * 604800L
        res.getString(R.string.c_months)  -> amount * 2592000L
        res.getString(R.string.c_years)   -> amount * 31536000L
        else -> amount * 86400L // Default to Days
    }
}