package me.lucky.silence.text

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import me.lucky.silence.AppDatabase
import java.util.concurrent.TimeUnit

class CleanupWorker(val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    companion object {
        private const val WORK_NAME = "cleanup"

        // a single pending job, moved to the expiry of the newest number
        fun schedule(ctx: Context, ttlMinutes: Int) =
            WorkManager
                .getInstance(ctx)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<CleanupWorker>()
                        .setInitialDelay(ttlMinutes.toLong() + 5, TimeUnit.MINUTES)
                        .build(),
                )
    }

    override fun doWork(): Result {
        AppDatabase.getInstance(ctx).allowNumberDao().deleteExpired()
        return Result.success()
    }
}
