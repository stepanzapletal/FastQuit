package com.stepan.fastquit

import android.app.Application
import androidx.work.*
import java.util.concurrent.TimeUnit

class FastQuitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        IconMapper.init(this)
        setupWorker()
    }

    private fun setupWorker() {
        val workRequest = PeriodicWorkRequestBuilder<HabitWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HabitCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}