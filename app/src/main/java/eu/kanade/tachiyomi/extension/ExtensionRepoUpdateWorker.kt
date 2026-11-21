package eu.kanade.tachiyomi.extension

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.TimeUnit

/**
 * Periodically (and on-demand, via [startNow]) refreshes every configured extension repository
 * and checks all installed extensions for updates, the same way opening the app does, but without
 * needing the user to open the Extensions tab.
 *
 * Scheduling is controlled by [SourcePreferences.autoExtensionUpdateInterval]; a value of 0 hours
 * disables the periodic job (see [setupTask]).
 */
class ExtensionRepoUpdateWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var extensionManager: ExtensionManager

    override suspend fun doWork(): Result {
        graph.inject(this)

        return try {
            extensionManager.checkForUpdates()
            Result.success()
        } catch (e: CancellationException) {
            // Assume success although cancelled
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ExtensionRepoUpdate"
        private const val WORK_NAME_AUTO = "ExtensionRepoUpdate-auto"
        private const val WORK_NAME_MANUAL = "ExtensionRepoUpdate-manual"

        /**
         * Schedules (or cancels) the periodic background check according to the current
         * [SourcePreferences.autoExtensionUpdateInterval] value. Call this again whenever that
         * preference changes, and once on app startup (see the migration that does so).
         */
        fun setupTask(context: Context, prefInterval: Int? = null) {
            val preferences = context.appGraph.sourcePreferences
            val interval = prefInterval ?: preferences.autoExtensionUpdateInterval.get()
            if (interval > 0) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<ExtensionRepoUpdateWorker>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        /**
         * Triggers an immediate one-off check, for a manual "update now" button. Returns false if
         * a check (scheduled or manual) is already running.
         */
        fun startNow(workManager: WorkManager): Boolean {
            if (workManager.isRunning(TAG)) {
                return false
            }

            val request = OneTimeWorkRequestBuilder<ExtensionRepoUpdateWorker>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }
    }
}
