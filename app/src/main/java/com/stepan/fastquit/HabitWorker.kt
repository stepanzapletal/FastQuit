package com.stepan.fastquit

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class HabitWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(context)
        val habits = database.habitDao().getAllHabits().first()
        val now = System.currentTimeMillis()

        habits.forEach { habit ->
            val diffSeconds = (now - habit.lastResetTime) / 1000

            // FIX: Pass targetSeconds so background worker detects "Green" state
            checkAndNotifyAchievements(
                context,
                habit.name,
                habit.id,
                diffSeconds,
                habit.completions,
                habit.targetSeconds
            )
        }

        return Result.success()
    }
}