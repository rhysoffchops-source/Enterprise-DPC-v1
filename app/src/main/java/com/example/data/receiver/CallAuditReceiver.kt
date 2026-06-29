package com.example.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.domain.model.ComplianceLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Audit receiver to capture and log phone state transitions (IDLE, RINGING, OFFHOOK)
 * for corporate billing compliance and security audit trails.
 */
class CallAuditReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            try {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Restricted Number"

                val detailMsg = when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> "Device ringing: Incoming corporate call from '$incomingNumber'."
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> "Call status changed: Line active (Off-Hook)."
                    TelephonyManager.EXTRA_STATE_IDLE -> "Call status changed: Device idle / call ended."
                    else -> "Unknown phone state detected: $state."
                }

                logEvent(context, "Call Audit Captured", detailMsg, "SUCCESS")
            } catch (e: Exception) {
                Log.e("CallAuditReceiver", "Failed to audit phone state: ${e.message}")
                logEvent(context, "Call Audit Error", "Error processing telephony state changed broadcast: ${e.message}", "FAILED")
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
