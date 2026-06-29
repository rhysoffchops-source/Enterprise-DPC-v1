package com.example.data.repository

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.UserManager
import com.example.data.database.ComplianceLogDao
import com.example.data.receiver.DpcDeviceAdminReceiver
import com.example.domain.model.ComplianceLog
import com.example.domain.model.PolicyState
import com.example.domain.repository.PolicyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.net.Inet4Address
import java.net.NetworkInterface

class PolicyRepositoryImpl(
    private val context: Context,
    private val logDao: ComplianceLogDao
) : PolicyRepository {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = DpcDeviceAdminReceiver.getComponentName(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("dpc_prefs", Context.MODE_PRIVATE)

    private val simulationModeFlow = MutableStateFlow(prefs.getBoolean("simulated_mode", true))

    // In-memory states for transient actions
    private val isRemoteViewingActiveFlow = MutableStateFlow(prefs.getBoolean("remote_viewing_active", false))
    private val isLogCollectionRunningFlow = MutableStateFlow(false)
    private val logCollectionProgressFlow = MutableStateFlow(0f)

    override fun getPolicyState(): Flow<PolicyState> {
        val combinedFlow = combine(
            simulationModeFlow,
            isRemoteViewingActiveFlow,
            isLogCollectionRunningFlow,
            logCollectionProgressFlow
        ) { simulated, remoteActive, logRunning, logProgress ->
            Quadruple(simulated, remoteActive, logRunning, logProgress)
        }

        return combinedFlow.combine(getRealPolicyFlow()) { extra, real ->
            val (simulated, remoteActive, logRunning, logProgress) = extra
            if (simulated) {
                PolicyState(
                    isDeviceAdmin = prefs.getBoolean("sim_is_device_admin", true),
                    isProfileOwner = prefs.getBoolean("sim_is_profile_owner", true),
                    isCameraDisabled = prefs.getBoolean("sim_is_camera_disabled", false),
                    isScreenCaptureDisabled = prefs.getBoolean("sim_is_screen_capture_disabled", false),
                    isSmsDisabled = prefs.getBoolean("sim_is_sms_disabled", false),
                    isOutgoingCallsDisabled = prefs.getBoolean("sim_is_outgoing_calls_disabled", false),
                    passwordMinimumLength = prefs.getInt("sim_password_min_length", 6),
                    passwordQuality = prefs.getInt("sim_password_quality", DevicePolicyManager.PASSWORD_QUALITY_NUMERIC),
                    isSimulatedMode = true,
                    isRemoteViewingActive = remoteActive,
                    supportAgentName = prefs.getString("support_agent", "IT Support Desk") ?: "IT Support Desk",
                    supportSessionId = prefs.getString("support_session", "SES-5910-38A") ?: "SES-5910-38A",
                    isLogCollectionRunning = logRunning,
                    logCollectionProgress = logProgress,
                    assetTag = prefs.getString("asset_tag", "ENT-8392-X") ?: "ENT-8392-X",
                    deviceModel = "Simulated " + Build.MODEL,
                    osVersion = "Android 14 (API 34)",
                    serialNumber = "S/N SIM-F892H19KSA",
                    batteryLevel = 94,
                    totalStorageGB = 256.0,
                    availableStorageGB = 184.5,
                    ipAddress = "192.168.1.104",
                    macAddress = "02:00:00:00:00:00",
                    workDomain = prefs.getString("work_domain", "enterprise.corp") ?: "enterprise.corp"
                )
            } else {
                real.copy(
                    isRemoteViewingActive = remoteActive,
                    isLogCollectionRunning = logRunning,
                    logCollectionProgress = logProgress,
                    supportAgentName = prefs.getString("support_agent", "IT Support Desk") ?: "IT Support Desk",
                    supportSessionId = prefs.getString("support_session", "SES-5910-38A") ?: "SES-5910-38A",
                    assetTag = prefs.getString("asset_tag", "ENT-8392-X") ?: "ENT-8392-X",
                    workDomain = prefs.getString("work_domain", "enterprise.corp") ?: "enterprise.corp"
                )
            }
        }
    }

    private fun getRealPolicyFlow(): Flow<PolicyState> = flow {
        val isDeviceAdmin = try {
            dpm.isAdminActive(adminComponent)
        } catch (e: Exception) {
            false
        }
        val isProfileOwner = try {
            dpm.isProfileOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
        val isCameraDisabled = try {
            if (isDeviceAdmin || isProfileOwner) dpm.getCameraDisabled(adminComponent) else false
        } catch (e: Exception) {
            false
        }
        val isScreenCaptureDisabled = try {
            if (isDeviceAdmin || isProfileOwner) dpm.getScreenCaptureDisabled(adminComponent) else false
        } catch (e: Exception) {
            false
        }
        val passwordMinLength = try {
            if (isDeviceAdmin || isProfileOwner) dpm.getPasswordMinimumLength(adminComponent) else 0
        } catch (e: Exception) {
            0
        }
        val passwordQuality = try {
            if (isDeviceAdmin || isProfileOwner) dpm.getPasswordQuality(adminComponent) else 0
        } catch (e: Exception) {
            0
        }
        val isSmsDisabled = try {
            if (isDeviceAdmin || isProfileOwner) dpm.getUserRestrictions(adminComponent).getBoolean(UserManager.DISALLOW_SMS, false) else false
        } catch (e: Exception) {
            false
        }
        val isOutgoingCallsDisabled = try {
            if (isDeviceAdmin || isProfileOwner) dpm.getUserRestrictions(adminComponent).getBoolean(UserManager.DISALLOW_OUTGOING_CALLS, false) else false
        } catch (e: Exception) {
            false
        }

        // Real battery level
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85

        // Real storage calculation
        var totalStorage = 128.0
        var availStorage = 64.0
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            totalStorage = (stat.blockCountLong * blockSize) / (1024.0 * 1024.0 * 1024.0)
            availStorage = (stat.availableBlocksLong * blockSize) / (1024.0 * 1024.0 * 1024.0)
            totalStorage = Math.round(totalStorage * 10.0) / 10.0
            availStorage = Math.round(availStorage * 10.0) / 10.0
        } catch (e: Exception) {
            // fallback
        }

        val ipStr = getIPAddress()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val versionRelease = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { Build.getSerial() } catch (e: SecurityException) { "S/N Restricted" }
        } else {
            @Suppress("DEPRECATION")
            Build.SERIAL
        }

        emit(
            PolicyState(
                isDeviceAdmin = isDeviceAdmin,
                isProfileOwner = isProfileOwner,
                isCameraDisabled = isCameraDisabled,
                isScreenCaptureDisabled = isScreenCaptureDisabled,
                isSmsDisabled = isSmsDisabled,
                isOutgoingCallsDisabled = isOutgoingCallsDisabled,
                passwordMinimumLength = passwordMinLength,
                passwordQuality = passwordQuality,
                isSimulatedMode = false,
                batteryLevel = battery,
                totalStorageGB = totalStorage,
                availableStorageGB = availStorage,
                ipAddress = ipStr,
                deviceModel = deviceName,
                osVersion = versionRelease,
                serialNumber = if (serial.isNullOrBlank() || serial == "unknown") "S/N F892H19KSA" else serial
            )
        )
    }

    private fun getIPAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "10.0.2.15"
                    }
                }
            }
        } catch (e: Exception) {}
        return "10.0.2.15"
    }

    override fun getComplianceLogs(): Flow<List<ComplianceLog>> {
        return logDao.getAllLogs()
    }

    override suspend fun addComplianceLog(log: ComplianceLog) {
        logDao.insertLog(log)
    }

    override suspend fun clearComplianceLogs() {
        logDao.deleteAllLogs()
    }

    override suspend fun updatePolicyState(state: PolicyState) {
        prefs.edit().apply {
            putBoolean("simulated_mode", state.isSimulatedMode)
            putBoolean("remote_viewing_active", state.isRemoteViewingActive)
            putString("support_agent", state.supportAgentName)
            putString("support_session", state.supportSessionId)
            putString("asset_tag", state.assetTag)
            putString("work_domain", state.workDomain)
            if (state.isSimulatedMode) {
                putBoolean("sim_is_device_admin", state.isDeviceAdmin)
                putBoolean("sim_is_profile_owner", state.isProfileOwner)
                putBoolean("sim_is_camera_disabled", state.isCameraDisabled)
                putBoolean("sim_is_screen_capture_disabled", state.isScreenCaptureDisabled)
                putBoolean("sim_is_sms_disabled", state.isSmsDisabled)
                putBoolean("sim_is_outgoing_calls_disabled", state.isOutgoingCallsDisabled)
                putInt("sim_password_min_length", state.passwordMinimumLength)
                putInt("sim_password_quality", state.passwordQuality)
            }
            apply()
        }
        simulationModeFlow.value = state.isSimulatedMode
        isRemoteViewingActiveFlow.value = state.isRemoteViewingActive
        isLogCollectionRunningFlow.value = state.isLogCollectionRunning
        logCollectionProgressFlow.value = state.logCollectionProgress
    }
}

// Helper tuple class since Pair/Triple are standard but Quadruple is not.
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
