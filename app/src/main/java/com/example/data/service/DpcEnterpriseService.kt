package com.example.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.data.database.AppDatabase
import com.example.domain.model.ComplianceLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DpcEnterpriseService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val channelId = "dpc_headless_service_channel"
    private val notificationId = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Enforce Foreground status immediately to satisfy background execution limits
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(notificationId, notification)
            }
        } else {
            startForeground(notificationId, notification)
        }

        // Run persistence-related tasks or corporate policy health check
        logEvent("DPC Service Initialized", "DPC persistent background compliance monitoring service started.", "SUCCESS")

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Enterprise DPC Service"
            val descriptionText = "Ensures background corporate policy synchronization and enforcement."
            
            // Using IMPORTANCE_MIN to minimize user distraction as requested
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Enterprise DPC Utility")
            .setContentText("Policy enforcement and auditing active in background.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun logEvent(action: String, details: String, status: String) {
        val database = AppDatabase.getDatabase(this)
        serviceScope.launch {
            database.complianceLogDao().insertLog(
                ComplianceLog(
                    action = action,
                    details = details,
                    status = status,
                    isSimulated = false
                )
            )
        }
    }
}
