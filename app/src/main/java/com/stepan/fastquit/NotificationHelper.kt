package com.stepan.fastquit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.firstOrNull

object Milestones {

    // Use a function that takes context
    fun time(context: Context) = mapOf(
        context.getString(R.string._24_hours) to 86400L,
        context.getString(R.string._3_days) to 259200L,
        context.getString(R.string._1_week) to 604800L,
        context.getString(R.string._1_month) to 2592000L,
        context.getString(R.string._3_months) to 7776000L,
        context.getString(R.string._6_months) to 15552000L,
        context.getString(R.string._1_year) to 31536000L
    )

    fun completion(context: Context) = mapOf(
        context.getString(R.string.goal_crusher_i) to 1,
        context.getString(R.string.goal_crusher_ii) to 3,
        context.getString(R.string.goal_crusher_iii) to 5,
        context.getString(R.string.elite) to 10,
        context.getString(R.string.master) to 25
    )
}


// Make this suspend so it can wait for the settings DB check
suspend fun checkAndNotifyAchievements(
    context: Context,
    habitName: String,
    habitId: Int,
    currentSeconds: Long,
    storedCompletions: Int,
    targetSeconds: Long
) {
    val prefs = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)

    // 1. Check Time Milestones
    Milestones.time(context).forEach { (title, requiredSeconds) ->
        if (currentSeconds >= requiredSeconds) {
            triggerNotification(context, prefs, habitId, title, habitName)
        }
    }

    // 2. Check Completion Milestones
    val isCurrentFinished = if (currentSeconds >= targetSeconds && targetSeconds > 0) 1 else 0
    val effectiveCompletions = storedCompletions + isCurrentFinished

    Milestones.completion(context).forEach { (title, requiredCompletions) ->
        if (effectiveCompletions >= requiredCompletions) {
            triggerNotification(context, prefs, habitId, title, habitName)
        }
    }
}

private suspend fun triggerNotification(
    context: Context,
    prefs: android.content.SharedPreferences,
    habitId: Int,
    title: String,
    habitName: String
) {
    val key = "unlocked_${habitId}_$title"

    if (!prefs.getBoolean(key, false)) {
        prefs.edit().putBoolean(key, true).apply()
        sendNotification(context,
            context.getString(R.string.achievement_unlocked_title),
            context.getString(R.string.achievement_unlocked_content, title, habitName))
    }
}

suspend fun sendNotification(context: Context, title: String, content: String) {
    // 1. ACCESS SETTINGS DB TO OBEY CONFIG
    val settingsDao = SettingsDatabase.getDatabase(context).settingsDao()
    val userPrefs = settingsDao.getPreferences().firstOrNull() ?: UserPreferences()

    // STOP if user disabled notifications in settings
    if (!userPrefs.notificationsEnabled) return

    val channelId = "ACHIEVEMENTS"

    // 2. Setup Channel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId,
            context.getString(R.string.habit_milestones), NotificationManager.IMPORTANCE_DEFAULT).apply {
            // Obey global haptics switch for the notification vibration
            if (!userPrefs.hapticsGlobal) {
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    // 3. Permission Check
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    }

    // 4. Build Notification
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(content)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    // Obey global haptics switch for sound/vibration
    if (!userPrefs.hapticsGlobal) {
        builder.setSilent(true)
        builder.setVibrate(longArrayOf(0))
    }

    // 5. Fire
    with(NotificationManagerCompat.from(context)) {
        notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }
}