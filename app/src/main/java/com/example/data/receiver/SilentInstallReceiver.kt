package com.example.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.domain.model.ComplianceLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver to handle status callbacks from silent PackageInstaller sessions.
 * Verifies if the background APK installation succeeded or failed and logs results.
 */
class SilentInstallReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_INSTALL_STATUS") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No status message provided"
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: "unknown_package"

            Log.d("SilentInstallReceiver", "Install callback received. Status: $status, Message: $message, Package: $packageName")

            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    logEvent(
                        context,
                        "Silent Install Success",
                        "Silent installation of package '$packageName' completed successfully without user intervention.",
                        "SUCCESS"
                    )
                }
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // This happens if the app is not fully privileged (not DO/PO)
                    logEvent(
                        context,
                        "Silent Install Pending",
                        "Silent installation of package '$packageName' requires user interaction. DPC privileges may be missing.",
                        "WARNING"
                    )
                }
                else -> {
                    logEvent(
                        context,
                        "Silent Install Failed",
                        "Installation of package '$packageName' failed with code $status: $message.",
                        "FAILED"
                    )
                }
            }
        }
    }

    private fun logEvent(context: Context, action: String, details: String, status: String) {
        val database = AppDatabase.getDatabase(context)
        scope.launch {
            try {
                database.complianceLogDao().insertLog(
                    ComplianceLog(
                        action = action,
                        details = details,
                        status = status,
                        isSimulated = false
                    )
                )
            } catch (e: Exception) {
                Log.e("SilentInstallReceiver", "Failed to insert install compliance log: ${e.message}")
            }
        }
    }
}
