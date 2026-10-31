package top.lvbyte.studio.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import top.lvbyte.studio.MainActivity
import top.lvbyte.studio.R
import top.lvbyte.studio.StudioApp
import top.lvbyte.studio.data.SessionExpiredException

class NotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? StudioApp)?.container ?: return Result.success()
        val settings = container.settings.snapshot()
        if (!settings.notificationsEnabled || !container.secure.hasSession) return Result.success()
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        return try {
            val res = container.studio.notifications(unreadOnly = true, limit = 50)
            val newOnes = res.content.filter { it.id > settings.lastNotificationId }
            if (newOnes.isNotEmpty()) {
                showNotification(newOnes)
                container.settings.setLastNotificationId(newOnes.maxOf { it.id })
            }
            Result.success()
        } catch (_: SessionExpiredException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(items: List<top.lvbyte.studio.data.model.StudioNotification>) {
        val context = applicationContext
        NotificationScheduler.ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open", "friends")
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val latest = items.first()
        val title = if (items.size == 1) latest.title else "有 ${items.size} 条新通知"
        val text = if (items.size == 1) latest.body else latest.body.ifBlank { "打开工作台查看" }
        val style = NotificationCompat.InboxStyle()
        items.take(6).forEach { style.addLine("${it.title}：${it.body}") }
        val builder = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_studio)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // 权限被收回时静默失败
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
