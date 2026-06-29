package com.example.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.domain.model.ComplianceLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Audit receiver to capture and log incoming SMS metadata for corporate security compliance.
 *
 * NOTE regarding `abortBroadcast()`:
 * To protect user privacy and prevent malware-like behavior (such as silently stealing MFA or OTP tokens),
 * modern Android systems (Android 4.4+) restrict the ability to block or abort incoming SMS messages.
 * Only the user-selected default SMS client receives the final non-abortable `SMS_DELIVER` broadcast
 * and has write access to the SMS provider. General enterprise management utilities and DPCs should NOT
 * use `abortBroadcast()` to intercept or suppress system-wide communications.
 *
 * This receiver operates transparently for enterprise security auditing, writing log records directly to the DPC secure repository.
 */
class SmsAuditReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (message in smsMessages) {
                    val sender = message.displayOriginatingAddress ?: "Unknown Sender"
                    val timestamp = message.timestampMillis
                    val length = message.messageBody?.length ?: 0
                    
                    // Audit logs: log metadata for compliant oversight
                    val details = "SMS metadata audit: received message from '$sender' (length: $length characters) at timestamp $timestamp."
                    logEvent(context, "SMS Audit Captured", details, "SUCCESS")
                }
            } catch (e: Exception) {
                Log.e("SmsAuditReceiver", "Failed to audit incoming SMS: ${e.message}")
                logEvent(context, "SMS Audit Error", "Error processing SMS broadcast: ${e.message}", "FAILED")
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
