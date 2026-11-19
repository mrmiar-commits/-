package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.ZarnegarLocalDatabase
import com.example.data.local.ZarnegarLocalRepository
import com.example.security.SecurityPreferences
import java.util.concurrent.TimeUnit

/**
 * Automatically flushes offline-queued Voice Messages to Google Sheets
 * as soon as the device regains internet connectivity.
 */
class SheetsAutoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = SecurityPreferences(applicationContext)
            val webhookUrl = prefs.getSheetsWebhookUrlNow()
            if (webhookUrl.isNullOrBlank()) {
                return Result.success()
            }
            val db = ZarnegarLocalDatabase.getInstance(applicationContext)
            val repo = ZarnegarLocalRepository(db.dao())
            val pendingNotes = repo.getUnsyncedVoiceNotes()
            if (pendingNotes.isEmpty()) {
                return Result.success()
            }
            val sheetsService = GoogleSheetsSyncService()
            val syncResult = sheetsService.appendVoiceNotesViaWebhook(webhookUrl, pendingNotes)
            if (syncResult.isSuccess) {
                repo.markVoiceNotesSynced(pendingNotes.map { it.id })
                Result.success()
            } else {
                Log.w("SheetsAutoSync", "Retry pending voice sync: ${syncResult.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SheetsAutoSync", "Worker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "zarnegar_offline_voice_sheets_sync"

        fun enqueueWhenOnline(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SheetsAutoSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
