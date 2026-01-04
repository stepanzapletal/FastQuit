package com.stepan.fastquit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.firstOrNull

object Milestones {
    val time = mapOf(
        "24 Hours" to 86400L,
        "3 Days" to 259200L,
        "1 Week" to 604800L,
        "1 Month" to 2592000L,
        "3 Months" to 7776000L,
        "6 Months" to 15552000L,
        "1 Year" to 31536000L
    )
    val completion = mapOf(
        "Goal Crusher I" to 1,
        "Goal Crusher II" to 3,
        "Goal Crusher III" to 5,
        "Elite" to 10,
        "Master" to 25
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
    Milestones.time.forEach { (title, requiredSeconds) ->
        if (currentSeconds >= requiredSeconds) {
            triggerNotification(context, prefs, habitId, title, habitName)
        }
    }

    // 2. Check Completion Milestones
    val isCurrentFinished = if (currentSeconds >= targetSeconds && targetSeconds > 0) 1 else 0
    val effectiveCompletions = storedCompletions + isCurrentFinished

    Milestones.completion.forEach { (title, requiredCompletions) ->
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
        sendNotification(context, "Achievement Unlocked! 🏆", "You reached $title on $habitName!")
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
        val channel = NotificationChannel(channelId, "Habit Milestones", NotificationManager.IMPORTANCE_DEFAULT).apply {
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