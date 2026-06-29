package com.example.data.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.database.AppDatabase
import com.example.domain.model.ComplianceLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DpcDeviceAdminReceiver : DeviceAdminReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

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

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show()
        logEvent(context, "Device Admin Enabled", "DPC application configured as active Device Admin.", "SUCCESS")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show()
        logEvent(context, "Device Admin Disabled", "DPC application privileges deactivated by user.", "WARNING")
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        logEvent(context, "Password Changed", "Device/profile lock password successfully updated.", "SUCCESS")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        logEvent(context, "Password Attempt Failed", "An invalid passcode attempt was registered on the secure lock screen.", "FAILED")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        logEvent(context, "Password Attempt Succeeded", "Successful authentication registered.", "SUCCESS")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Toast.makeText(context, "Work Profile Provisioning Complete", Toast.LENGTH_SHORT).show()
        
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = getComponentName(context)
        
        try {
            dpm.setProfileName(adminComponent, "Work Profile DPC")
            dpm.setProfileEnabled(adminComponent)
            logEvent(context, "Work Profile Active", "Work profile created, labeled, and marked active successfully.", "SUCCESS")
        } catch (e: Exception) {
            logEvent(context, "Profile Config Failure", "Error configuring work profile: ${e.message}", "FAILED")
        }
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, DpcDeviceAdminReceiver::class.java)
        }
    }
}
