package app.sentinel.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object BackgroundChecks {
    const val PREFS = "yxshu_security"
    const val ENABLED = "background_enabled"
    const val LAST_CHECK = "background_last_check"
    const val LAST_RESULT = "background_last_result"
    private const val PERIODIC_NAME = "yxshu-periodic-security-check"
    private const val INITIAL_NAME = "yxshu-first-security-check"
    const val CHANNEL_ID = "yxshu_security_alerts"
    private const val CHANNEL_NAME = "YXSHU security alerts"
    private const val NOTIFICATION_ID = 8102

    fun enable(context: Context) {
        val appContext = context.applicationContext
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        val periodic = PeriodicWorkRequestBuilder<SecurityCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic
        )
        val firstCheck = OneTimeWorkRequestBuilder<SecurityCheckWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            INITIAL_NAME, ExistingWorkPolicy.KEEP, firstCheck
        )
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, true).apply()
    }

    fun disable(context: Context) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelUniqueWork(PERIODIC_NAME)
        WorkManager.getInstance(appContext).cancelUniqueWork(INITIAL_NAME)
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, false).apply()
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerts when a background check finds app names to review"
                }
            )
        }
    }

    fun postReviewNotification(context: Context, report: ScanReport) {
        if (!report.hasReviewCandidates || !notificationsAllowed(context)) return
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = openApp?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = Notification.Builder(context, CHANNEL_ID)
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("YXSHU found an app name to review")
            .setContentText(report.notificationText())
            .setStyle(Notification.BigTextStyle().bigText(report.notificationText()))
            .setAutoCancel(true)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)
        manager.notify(NOTIFICATION_ID, builder.build())
    }
}

class SecurityCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val report = DeviceScanner.scan(applicationContext)
        applicationContext.getSharedPreferences(BackgroundChecks.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(BackgroundChecks.LAST_CHECK, System.currentTimeMillis())
            .putString(BackgroundChecks.LAST_RESULT, report.notificationText())
            .apply()
        BackgroundChecks.postReviewNotification(applicationContext, report)
        Result.success()
    }.getOrElse { Result.retry() }
}
