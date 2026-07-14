package com.obdvis.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.obdvis.android.domain.health.DiagnosticFinding
import com.obdvis.android.domain.health.DiagnosticSummary

class NotificationHelper(context: Context) {
    private val ctx = context.applicationContext
    private val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var notifId = 0

    companion object {
        const val CHANNEL_ID = "obdvis_health"
        const val CHANNEL_ID_STANDING = "obdvis_standing"
        private const val STANDING_NOTIF_ID = 1
    }

    init {
        val alertChannel = NotificationChannel(
            CHANNEL_ID,
            "Vehicle Health Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Background vehicle health check alerts" }
        manager.createNotificationChannel(alertChannel)

        val standingChannel = NotificationChannel(
            CHANNEL_ID_STANDING,
            "Connection Status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while OBDvis is connected and running in the background"
            setShowBadge(false)
        }
        manager.createNotificationChannel(standingChannel)
    }

    fun showStandingNotification(deviceName: String, summary: DiagnosticSummary? = null) {
        val body = when {
            summary == null -> "Monitoring…"
            summary.findings.isEmpty() -> "No issues detected"
            else -> {
                val worst = summary.findings.maxByOrNull { it.severity.ordinal }!!
                if (summary.findings.size == 1) worst.title
                else "${summary.findings.size} findings · worst: ${worst.severity.name.lowercase().replaceFirstChar { it.uppercase() }}"
            }
        }

        val launchIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID_STANDING)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Connected to $deviceName")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(STANDING_NOTIF_ID, notification)
    }

    fun cancelStandingNotification() {
        manager.cancel(STANDING_NOTIF_ID)
    }

    fun notifyFindings(findings: List<DiagnosticFinding>) {
        if (findings.isEmpty()) return
        val worst = findings.maxByOrNull { it.severity.ordinal } ?: return
        val body = if (findings.size > 1) "${findings.size} findings detected" else worst.description

        val launchIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(worst.title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(++notifId, notification)
    }
}
