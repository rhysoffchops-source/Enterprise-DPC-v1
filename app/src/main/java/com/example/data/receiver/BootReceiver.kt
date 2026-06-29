package com.example.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.database.AppDatabase
import com.example.data.service.DpcEnterpriseService
import com.example.domain.model.ComplianceLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            logEvent(context, "System Boot Completed", "Received BOOT_COMPLETED broadcast. Re-initializing DPC services.", "SUCCESS")
            
            // Start the background DpcEnterpriseService
            val serviceIntent = Intent(context, DpcEnterpriseService::class.java)
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                logEvent(context, "Service Start Failure", "Failed to start background foreground service on boot: ${e.message}", "FAILED")
            }
        }
    }

    private fun logEvent(context: Context, action: String, details: String, status: String) {
        val database = AppDatabase.getDatabase(context)
        receiverScope.launch {
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
