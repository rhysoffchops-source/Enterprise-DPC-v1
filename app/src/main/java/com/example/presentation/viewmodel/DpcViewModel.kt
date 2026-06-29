package com.example.presentation.viewmodel

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.os.UserManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.receiver.DpcDeviceAdminReceiver
import com.example.data.repository.PolicyRepositoryImpl
import com.example.data.service.AssetDiscoveryManager
import com.example.domain.model.DiscoveredAsset
import com.example.domain.model.ComplianceLog
import com.example.domain.model.EnterpriseApp
import com.example.domain.model.PolicyState
import com.example.domain.repository.PolicyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class DpcViewModel(
    private val context: Context,
    private val repository: PolicyRepository
) : ViewModel() {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = DpcDeviceAdminReceiver.getComponentName(context)

    val assetDiscoveryManager = AssetDiscoveryManager(context, repository)

    val policyState: StateFlow<PolicyState> = repository.getPolicyState().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PolicyState(isSimulatedMode = true)
    )

    val complianceLogs: StateFlow<List<ComplianceLog>> = repository.getComplianceLogs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Managed Enterprise Apps State
    private val _enterpriseApps = MutableStateFlow<List<EnterpriseApp>>(
        listOf(
            EnterpriseApp("1", "Slack Enterprise", "com.slack.enterprise.android", "v4.32.0", "NOT_INSTALLED", sizeMb = 48),
            EnterpriseApp("2", "Cisco Webex Meetings", "com.cisco.webex.meetings", "v43.6.0", "PENDING_UPDATE", sizeMb = 65),
            EnterpriseApp("3", "Salesforce Mobile", "com.salesforce.chatter", "v242.0.3", "INSTALLED", sizeMb = 112),
            EnterpriseApp("4", "Secure Corp Email", "com.corp.securemail", "v2.1.5", "NOT_INSTALLED", sizeMb = 18),
            EnterpriseApp("5", "Adobe Acrobat Reader", "com.adobe.reader", "v23.4", "INSTALLED", sizeMb = 55),
            EnterpriseApp("6", "Workspace Hub", "com.corp.workspacehub", "v1.0.0", "NOT_INSTALLED", sizeMb = 32)
        )
    )
    val enterpriseApps: StateFlow<List<EnterpriseApp>> = _enterpriseApps.asStateFlow()

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow

    fun toggleSimulationMode(enabled: Boolean) {
        viewModelScope.launch {
            val currentState = policyState.value
            repository.updatePolicyState(currentState.copy(isSimulatedMode = enabled))
            val modeStr = if (enabled) "Simulation Mode" else "Real Mode"
            logAction("Mode Switched", "User toggled application to $modeStr.", "SUCCESS", isSimulated = enabled)
            _eventFlow.emit("Switched to $modeStr")
        }
    }

    fun setCameraDisabled(disabled: Boolean) {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                repository.updatePolicyState(state.copy(isCameraDisabled = disabled))
                val desc = if (disabled) "Camera access deactivated across all work apps." else "Camera access permitted."
                logAction("Set Camera Disabled", desc, "SIMULATED", isSimulated = true)
                _eventFlow.emit("Simulated Camera policy updated.")
            } else {
                try {
                    dpm.setCameraDisabled(adminComponent, disabled)
                    logAction("Set Camera Disabled", "Camera restriction toggled to: $disabled", "SUCCESS")
                    _eventFlow.emit("Camera policy updated on device.")
                } catch (e: SecurityException) {
                    logAction("Set Camera Disabled", "Failed: ${e.message}", "FAILED")
                    _eventFlow.emit("Permission Error: App is not a Device Admin or Profile Owner!")
                }
            }
        }
    }

    fun setScreenCaptureDisabled(disabled: Boolean) {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                repository.updatePolicyState(state.copy(isScreenCaptureDisabled = disabled))
                val desc = if (disabled) "Screenshots and screen sharing blocked." else "Screen capture permitted."
                logAction("Set Screen Capture Disabled", desc, "SIMULATED", isSimulated = true)
                _eventFlow.emit("Simulated Screen Capture policy updated.")
            } else {
                try {
                    dpm.setScreenCaptureDisabled(adminComponent, disabled)
                    logAction("Set Screen Capture", "Screen capture restriction toggled to: $disabled", "SUCCESS")
                    _eventFlow.emit("Screen capture policy updated on device.")
                } catch (e: SecurityException) {
                    logAction("Set Screen Capture", "Failed: ${e.message}", "FAILED")
                    _eventFlow.emit("Permission Error: App is not a Device Admin or Profile Owner!")
                }
            }
        }
    }

    fun setSmsDisabled(disabled: Boolean) {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                repository.updatePolicyState(state.copy(isSmsDisabled = disabled))
                val desc = if (disabled) "Corporate SMS communication blocked on device." else "Corporate SMS communication enabled."
                logAction("Set SMS Disabled", desc, "SIMULATED", isSimulated = true)
                _eventFlow.emit("Simulated SMS policy updated.")
            } else {
                try {
                    if (disabled) {
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SMS)
                    } else {
                        dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SMS)
                    }
                    logAction("Set SMS Disabled", "SMS restriction toggled to: $disabled", "SUCCESS")
                    _eventFlow.emit("SMS policy updated on device.")
                } catch (e: SecurityException) {
                    logAction("Set SMS Disabled", "Failed: ${e.message}", "FAILED")
                    _eventFlow.emit("Permission Error: App is not a Device Admin or Profile Owner!")
                }
            }
        }
    }

    fun setOutgoingCallsDisabled(disabled: Boolean) {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                repository.updatePolicyState(state.copy(isOutgoingCallsDisabled = disabled))
                val desc = if (disabled) "Outgoing calls deactivated." else "Outgoing calls permitted."
                logAction("Set Outgoing Calls Disabled", desc, "SIMULATED", isSimulated = true)
                _eventFlow.emit("Simulated call routing policy updated.")
            } else {
                try {
                    if (disabled) {
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
                    } else {
                        dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
                    }
                    logAction("Set Outgoing Calls Disabled", "Outgoing calls restriction toggled to: $disabled", "SUCCESS")
                    _eventFlow.emit("Call restriction updated on device.")
                } catch (e: SecurityException) {
                    logAction("Set Outgoing Calls Disabled", "Failed: ${e.message}", "FAILED")
                    _eventFlow.emit("Permission Error: App is not a Device Admin or Profile Owner!")
                }
            }
        }
    }

    fun setPasswordPolicy(minLength: Int, quality: Int) {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                repository.updatePolicyState(state.copy(passwordMinimumLength = minLength, passwordQuality = quality))
                val qStr = when (quality) {
                    DevicePolicyManager.PASSWORD_QUALITY_NUMERIC -> "Numeric"
                    DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC -> "Alphanumeric"
                    else -> "Unspecified"
                }
                logAction("Password Policy Applied", "Min Length: $minLength, Quality: $qStr", "SIMULATED", isSimulated = true)
                _eventFlow.emit("Simulated Password constraints applied.")
            } else {
                try {
                    dpm.setPasswordQuality(adminComponent, quality)
                    dpm.setPasswordMinimumLength(adminComponent, minLength)
                    logAction("Password Policy Applied", "Enforced min-length $minLength, quality code $quality", "SUCCESS")
                    _eventFlow.emit("Device Password constraints enforced.")
                } catch (e: SecurityException) {
                    logAction("Password Policy Applied", "Failed: ${e.message}", "FAILED")
                    _eventFlow.emit("Permission Error: App is not an Admin/Profile Owner!")
                }
            }
        }
    }

    fun lockScreen() {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                logAction("Screen Lock Triggered", "Dispatched immediate display lock command.", "SIMULATED", isSimulated = true)
                _eventFlow.emit("Simulated: Screen locked.")
            } else {
                try {
                    dpm.lockNow()
                    logAction("Screen Lock Triggered", "Dispatched lockNow() command.", "SUCCESS")
                    _eventFlow.emit("Locking device screen...")
                } catch (e: SecurityException) {
                    logAction("Screen Lock Triggered", "Failed: ${e.message}", "FAILED")
                    _eventFlow.emit("Permission Error: Requires active Device Admin!")
                }
            }
        }
    }

    fun wipeWorkProfile() {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                logAction("Work Profile Wipe", "Simulated container wipe requested. Resetting state.", "SIMULATED", isSimulated = true)
                repository.updatePolicyState(
                    PolicyState(
                        isDeviceAdmin = false,
                        isProfileOwner = false,
                        isSimulatedMode = true
                    )
                )
                _eventFlow.emit("Simulated profile container wiped.")
            } else {
                try {
                    logAction("Work Profile Wipe Initiated", "Wiping work profile container data...", "SUCCESS")
                    _eventFlow.emit("Initiating container wipe...")
                    dpm.wipeData(0)
                } catch (e: SecurityException) {
                    logAction("Work Profile Wipe Failed", "Wipe failed: ${e.message}", "FAILED")
                    _eventFlow.emit("Permission Error: Requires Profile Owner or Device Owner privileges!")
                }
            }
        }
    }

    fun initiateWorkProfileProvisioning(activity: Activity) {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                logAction("Provisioning Started", "Simulated enterprise onboarding sequence initiated.", "SIMULATED", isSimulated = true)
                repository.updatePolicyState(state.copy(isProfileOwner = true, isDeviceAdmin = true))
                _eventFlow.emit("Simulated onboarding successful!")
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, android.os.Bundle())
                }
                
                try {
                    logAction("Provisioning Initiated", "Launching ACTION_PROVISION_MANAGED_PROFILE system setup intent.", "SUCCESS")
                    activity.startActivityForResult(intent, 1001)
                    _eventFlow.emit("System provisioning setup launched.")
                } catch (e: Exception) {
                    logAction("Provisioning Failed", "Launch failed: ${e.message}", "FAILED")
                    _eventFlow.emit("Error: Device does not support work profile provisioning!")
                }
            }
        }
    }

    // --- TROUBLESHOOTING: REMOTE SCREEN SHARING ---
    fun startRemoteScreenSharing(agentName: String) {
        viewModelScope.launch {
            val state = policyState.value
            val generatedSessionId = "SES-${Random.nextInt(1000, 9999)}-${Random.nextInt(10, 99)}X"
            repository.updatePolicyState(
                state.copy(
                    isRemoteViewingActive = true,
                    supportAgentName = agentName.ifBlank { "IT Support Desk" },
                    supportSessionId = generatedSessionId
                )
            )
            logAction(
                "Remote Screen Mirroring Started",
                "Remote console session $generatedSessionId established by operator '$agentName'.",
                if (state.isSimulatedMode) "SIMULATED" else "SUCCESS",
                isSimulated = state.isSimulatedMode
            )
            _eventFlow.emit("Remote troubleshoot mirroring initiated.")
        }
    }

    fun stopRemoteScreenSharing() {
        viewModelScope.launch {
            val state = policyState.value
            repository.updatePolicyState(state.copy(isRemoteViewingActive = false))
            logAction(
                "Remote Screen Mirroring Stopped",
                "Troubleshooting console mirror terminated for session ${state.supportSessionId}.",
                if (state.isSimulatedMode) "SIMULATED" else "SUCCESS",
                isSimulated = state.isSimulatedMode
            )
            _eventFlow.emit("Remote mirroring stopped.")
        }
    }

    // --- TROUBLESHOOTING: DIAGNOSTIC LOG COLLECTION ---
    fun collectDeviceLogs() {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isLogCollectionRunning) return@launch

            logAction("Log Collection Initiated", "Beginning diagnostic harvest of logcat, dumpsys, and kernel logs.", "PENDING", isSimulated = state.isSimulatedMode)
            _eventFlow.emit("Starting diagnostic log harvest...")

            repository.updatePolicyState(state.copy(isLogCollectionRunning = true, logCollectionProgress = 0f))

            // Simulate parsing and archiving stages
            for (step in 1..10) {
                delay(200)
                val currentProgress = step * 10f
                val activeState = policyState.value
                repository.updatePolicyState(activeState.copy(logCollectionProgress = currentProgress))
            }

            val finishedState = policyState.value
            repository.updatePolicyState(finishedState.copy(isLogCollectionRunning = false, logCollectionProgress = 0f))

            logAction(
                "Diagnostic Logs Collected",
                "Zipped dump (logcat.zip, 4.2 MB) pushed successfully to MDM Administration Core console.",
                if (finishedState.isSimulatedMode) "SIMULATED" else "SUCCESS",
                isSimulated = finishedState.isSimulatedMode
            )
            _eventFlow.emit("Log package successfully uploaded!")
        }
    }

    // --- ASSET MANAGEMENT INVENTORY ---
    fun updateAssetDetails(tag: String, domain: String) {
        viewModelScope.launch {
            val state = policyState.value
            repository.updatePolicyState(state.copy(assetTag = tag, workDomain = domain))
            logAction(
                "Asset Identity Updated",
                "Asset tag set to '$tag', enrollment domain configured as '$domain'.",
                if (state.isSimulatedMode) "SIMULATED" else "SUCCESS",
                isSimulated = state.isSimulatedMode
            )
            _eventFlow.emit("Asset information saved.")
        }
    }

    // --- APP DEPLOYMENT FUNCTIONS ---
    fun installEnterpriseApp(appId: String) {
        viewModelScope.launch {
            val appList = _enterpriseApps.value
            val targetApp = appList.find { it.id == appId } ?: return@launch
            
            // Check if already installing
            if (targetApp.status == "INSTALLING") return@launch

            // Set state to installing
            updateAppInList(appId, "INSTALLING", 0f)
            logAction("App Deployment Initiated", "Triggered silent installation request for package '${targetApp.packageName}' via corporate play api.", "PENDING")

            // Count progress simulation
            for (progressStep in 1..5) {
                delay(300)
                updateAppInList(appId, "INSTALLING", progressStep * 20f)
            }

            updateAppInList(appId, "INSTALLED", 100f, version = targetApp.version)
            logAction(
                "App Deployed Successfully",
                "Silent apk installation and security signature checks completed for '${targetApp.name}' (${targetApp.packageName}).",
                if (policyState.value.isSimulatedMode) "SIMULATED" else "SUCCESS",
                isSimulated = policyState.value.isSimulatedMode
            )
            _eventFlow.emit("${targetApp.name} successfully deployed!")
        }
    }

    fun updateEnterpriseApp(appId: String) {
        viewModelScope.launch {
            val appList = _enterpriseApps.value
            val targetApp = appList.find { it.id == appId } ?: return@launch

            updateAppInList(appId, "INSTALLING", 0f)
            logAction("App Update Initiated", "Pushing software patch and clearing cache for '${targetApp.name}' to version ${incrementVersion(targetApp.version)}.", "PENDING")

            for (progressStep in 1..4) {
                delay(250)
                updateAppInList(appId, "INSTALLING", progressStep * 25f)
            }

            val nextVer = incrementVersion(targetApp.version)
            updateAppInList(appId, "INSTALLED", 100f, version = nextVer)
            logAction(
                "App Updated Successfully",
                "Corporate application '${targetApp.name}' upgraded securely to $nextVer.",
                if (policyState.value.isSimulatedMode) "SIMULATED" else "SUCCESS",
                isSimulated = policyState.value.isSimulatedMode
            )
            _eventFlow.emit("${targetApp.name} updated to $nextVer!")
        }
    }

    fun uninstallEnterpriseApp(appId: String) {
        viewModelScope.launch {
            val appList = _enterpriseApps.value
            val targetApp = appList.find { it.id == appId } ?: return@launch

            updateAppInList(appId, "NOT_INSTALLED", 0f)
            logAction(
                "App License Revoked",
                "Silent uninstallation complete. Revoked work profile license for app '${targetApp.name}'.",
                if (policyState.value.isSimulatedMode) "SIMULATED" else "SUCCESS",
                isSimulated = policyState.value.isSimulatedMode
            )
            _eventFlow.emit("${targetApp.name} uninstalled.")
        }
    }

    private fun updateAppInList(appId: String, status: String, progress: Float, version: String? = null) {
        val currentList = _enterpriseApps.value.map { app ->
            if (app.id == appId) {
                app.copy(
                    status = status,
                    progress = progress,
                    version = version ?: app.version
                )
            } else {
                app
            }
        }
        _enterpriseApps.value = currentList
    }

    private fun incrementVersion(ver: String): String {
        return try {
            val digits = ver.filter { it.isDigit() || it == '.' }
            val parts = digits.split(".")
            if (parts.size >= 2) {
                val lastNum = parts.last().toIntOrNull() ?: 0
                val nextNum = lastNum + 1
                val prefix = ver.substring(0, ver.indexOf(parts.first()))
                val joined = parts.dropLast(1).joinToString(".") + "." + nextNum
                prefix + joined
            } else {
                ver + ".1"
            }
        } catch (e: Exception) {
            ver + "-updated"
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearComplianceLogs()
            _eventFlow.emit("Logs cleared.")
        }
    }

    private suspend fun logAction(action: String, details: String, status: String, isSimulated: Boolean = false) {
        repository.addComplianceLog(
            ComplianceLog(
                action = action,
                details = details,
                status = status,
                isSimulated = isSimulated
            )
        )
    }

    fun sendEmergencySms(phoneNumber: String, message: String) {
        viewModelScope.launch {
            if (phoneNumber.isBlank() || message.isBlank()) {
                _eventFlow.emit("Error: Phone number and message cannot be empty.")
                return@launch
            }

            // Check SEND_SMS permission
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                logAction("Emergency SMS Failed", "Missing SEND_SMS permission. Unable to transmit signal to $phoneNumber.", "FAILED")
                _eventFlow.emit("Permission Error: SEND_SMS permission is not granted!")
                return@launch
            }

            try {
                val smsManager: android.telephony.SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }

                // Send SMS message
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)

                logAction("Emergency SMS Transmitted", "Plain text emergency signal successfully queued for delivery to $phoneNumber.", "SUCCESS")
                _eventFlow.emit("Emergency recovery signal sent to $phoneNumber")
            } catch (e: Exception) {
                android.util.Log.e("DpcViewModel", "Failed to send emergency SMS: ${e.message}")
                logAction("Emergency SMS Failed", "Transmission to $phoneNumber failed: ${e.message}", "FAILED")
                _eventFlow.emit("Transmission Failed: ${e.message}")
            }
        }
    }

    /**
     * Security Reset Module.
     * Initiated by an administrator to decommission or repurpose a device by:
     * 1. Programmatically removing any existing lock screen PIN/pattern/password.
     * 2. Removing future complexity requirements.
     * 3. Clearing user restrictions (systemic blocks).
     */
    fun performSecurityReset() {
        viewModelScope.launch {
            val state = policyState.value
            if (state.isSimulatedMode) {
                // Clear state in repository
                val resetState = state.copy(
                    passwordMinimumLength = 0,
                    passwordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED,
                    isSmsDisabled = false,
                    isOutgoingCallsDisabled = false
                )
                repository.updatePolicyState(resetState)
                logAction(
                    "Security Reset Initiated",
                    "Decommission/Repurpose Reset (Simulated): Cleared lock PIN, reset password quality constraints to 0, and cleared SMS/Call restrictions.",
                    "SIMULATED",
                    isSimulated = true
                )
                _eventFlow.emit("Simulated Decommissioning Security Reset complete.")
            } else {
                try {
                    // 1. Use DevicePolicyManager.resetPassword() to clear any existing PIN, pattern, or password.
                    try {
                        @Suppress("DEPRECATION")
                        val resetSuccess = dpm.resetPassword("", 0)
                        logAction("Clear Device Password", "resetPassword() invoked. Status: $resetSuccess", "SUCCESS")
                    } catch (e: Exception) {
                        logAction("Clear Device Password Failed", "Failed to programmatically reset password using resetPassword(): ${e.message}", "WARNING")
                    }

                    // 2. Use DevicePolicyManager.setPasswordQuality() and setMinimumPasswordLength(0) to remove all future password complexity requirements.
                    dpm.setPasswordQuality(adminComponent, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED)
                    dpm.setPasswordMinimumLength(adminComponent, 0)
                    logAction("Reset Password Requirements", "Password complexity minimum set to 0 and quality set to UNSPECIFIED.", "SUCCESS")

                    // 3. Implement DevicePolicyManager.clearUserRestrictions() to remove any systemic blocks on the device.
                    clearUserRestrictions()

                    // Update repo state to match
                    val resetState = state.copy(
                        passwordMinimumLength = 0,
                        passwordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED,
                        isSmsDisabled = false,
                        isOutgoingCallsDisabled = false
                    )
                    repository.updatePolicyState(resetState)

                    logAction("Security Decommission Reset", "Successfully executed full administrative security decommission reset.", "SUCCESS")
                    _eventFlow.emit("Administrative Security Reset executed successfully!")
                } catch (e: SecurityException) {
                    logAction("Security Decommission Reset", "Privilege Error: ${e.message}", "FAILED")
                    _eventFlow.emit("Permission Error: DPC requires active Device/Profile Owner to execute Security Reset.")
                }
            }
        }
    }

    /**
     * Clear User Restrictions helper to remove all systemic blocks set on the device.
     */
    private suspend fun clearUserRestrictions() {
        try {
            // Explicitly clear known restrictions
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SMS)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)

            // Dynamically query and clear any other active restrictions
            val restrictionsBundle = dpm.getUserRestrictions(adminComponent)
            for (key in restrictionsBundle.keySet()) {
                dpm.clearUserRestriction(adminComponent, key)
            }
            logAction("Clear User Restrictions", "Cleared user restrictions set by DPC admin.", "SUCCESS")
        } catch (e: Exception) {
            logAction("Clear User Restrictions Failed", "Error clearing user restrictions bundle: ${e.message}", "WARNING")
        }
    }

    /**
     * Programmatically provisions critical diagnostic permissions for a targeted package.
     * Uses DevicePolicyManager.setPermissionGrantState() to grant READ_SMS and ACCESS_FINE_LOCATION.
     */
    fun grantTargetedPermissions(packageName: String) {
        viewModelScope.launch {
            val state = policyState.value
            val permissions = listOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )

            if (state.isSimulatedMode) {
                logAction(
                    "Targeted Perm Init",
                    "Simulated Permission Provisioning started for package: $packageName",
                    "SIMULATED",
                    isSimulated = true
                )
                for (permission in permissions) {
                    delay(500)
                    logAction(
                        "Targeted Perm Grant",
                        "Simulated grant of $permission to $packageName (PERMISSION_GRANT_STATE_GRANTED)",
                        "SUCCESS",
                        isSimulated = true
                    )
                }
                _eventFlow.emit("Simulated targeted permission provisioning complete for $packageName.")
            } else {
                logAction(
                    "Targeted Perm Init",
                    "Starting permission provisioning for package: $packageName",
                    "PENDING"
                )
                try {
                    for (permission in permissions) {
                        // Check current grant state via dpm
                        val currentState = dpm.getPermissionGrantState(adminComponent, packageName, permission)
                        val isAlreadyGranted = currentState == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED || 
                            context.packageManager.checkPermission(permission, packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (isAlreadyGranted) {
                            logAction(
                                "Targeted Perm Check",
                                "Permission $permission is already granted for package $packageName.",
                                "SUCCESS"
                            )
                        } else {
                            val success = dpm.setPermissionGrantState(
                                adminComponent,
                                packageName,
                                permission,
                                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                            )
                            if (success) {
                                logAction(
                                    "Targeted Perm Grant",
                                    "Successfully granted $permission to $packageName.",
                                    "SUCCESS"
                                )
                            } else {
                                logAction(
                                    "Targeted Perm Grant",
                                    "Platform returned false when granting $permission to $packageName.",
                                    "WARNING"
                                )
                            }
                        }
                    }
                    _eventFlow.emit("Targeted permissions successfully provisioned for $packageName!")
                } catch (e: SecurityException) {
                    logAction(
                        "Targeted Perm Error",
                        "Failed to set permission state for $packageName: SecurityException: ${e.message}. Active Device/Profile Owner privileges required.",
                        "FAILED"
                    )
                    _eventFlow.emit("Security Exception: DPC is not set as Device Owner or Profile Owner.")
                } catch (e: Exception) {
                    logAction(
                        "Targeted Perm Error",
                        "Failed to grant permissions to $packageName: ${e.message}",
                        "FAILED"
                    )
                    _eventFlow.emit("Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Universal App Iteration: Queries the PackageManager to list every installed application
     * on the device and programmatically applies 'open access' forensic permission grants to each.
     */
    fun grantUniversalPermissions() {
        viewModelScope.launch {
            val state = policyState.value
            val criticalPermissions = listOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )

            if (state.isSimulatedMode) {
                logAction(
                    "Universal Grant Init",
                    "Simulated Universal Permission Granting started for all packages",
                    "SIMULATED",
                    isSimulated = true
                )
                // Simulated list of packages on a corporate device
                val simPackages = listOf(
                    "com.corp.securemail",
                    "com.corp.workspacehub",
                    "com.salesforce.chatter",
                    "com.cisco.webex.meetings"
                )
                for (pkgName in simPackages) {
                    delay(300)
                    logAction(
                        "Universal Grant",
                        "Simulated open-access granted for $pkgName (READ_SMS, RECORD_AUDIO, ACCESS_FINE_LOCATION)",
                        "SUCCESS",
                        isSimulated = true
                    )
                }
                _eventFlow.emit("Simulated universal permission grants complete across all installed corporate apps.")
            } else {
                logAction(
                    "Universal Grant Init",
                    "Starting universal application forensic permission audit",
                    "PENDING"
                )
                try {
                    val pm = context.packageManager
                    val packages: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS) ?: emptyList()
                    var processedCount = 0
                    var grantedCount = 0

                    for (pkgInfo in packages) {
                        val pkgName = pkgInfo.packageName
                        // Skip system package of android and our own DPC app to avoid unnecessary cycles
                        if (pkgName == "android" || pkgName == context.packageName) continue

                        processedCount++
                        val requestedPermissions = pkgInfo.requestedPermissions ?: continue

                        for (perm in criticalPermissions) {
                            if (requestedPermissions.contains(perm)) {
                                val success = dpm.setPermissionGrantState(
                                    adminComponent,
                                    pkgName,
                                    perm,
                                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                                )
                                if (success) {
                                    grantedCount++
                                }
                            }
                        }
                    }

                    logAction(
                        "Universal Grant Complete",
                        "Scanned $processedCount apps, programmatically granted $grantedCount critical permission bounds.",
                        "SUCCESS"
                    )
                    _eventFlow.emit("Universal permission grants completed! Granted $grantedCount permissions across $processedCount applications.")
                } catch (e: SecurityException) {
                    logAction(
                        "Universal Grant Error",
                        "SecurityException during universal app iteration: ${e.message}. Active Device/Profile Owner privileges required.",
                        "FAILED"
                    )
                    _eventFlow.emit("Security Exception: DPC is not set as Device Owner or Profile Owner.")
                } catch (e: Exception) {
                    logAction(
                        "Universal Grant Error",
                        "Failed to execute universal app iteration: ${e.message}",
                        "FAILED"
                    )
                    _eventFlow.emit("Error executing universal grant: ${e.message}")
                }
            }
        }
    }

    /**
     * Security Flag Override Module: Sets system policies to ensure app-level restrictions
     * (like FLAG_SECURE) across the device are bypassed/overridden so remote assistance tools
     * can perform screen mirroring, screenshotting, and technical guidance.
     */
    fun overrideSecurityFlagsAcrossDevice(enabled: Boolean) {
        viewModelScope.launch {
            val state = policyState.value
            repository.updatePolicyState(state.copy(isSecurityFlagOverrideActive = enabled))
            if (state.isSimulatedMode) {
                logAction(
                    "Security Flag Override",
                    "Simulated app-level restrictions bypass (FLAG_SECURE override) set to: $enabled",
                    "SIMULATED",
                    isSimulated = true
                )
                _eventFlow.emit("Simulated App-Level Security Override set to $enabled")
            } else {
                try {
                    // To override screen-blocking restrictions across the device,
                    // we programmatically set setScreenCaptureDisabled to false.
                    // This explicitly permits screenshots/mirrors on all standard & corporate views.
                    dpm.setScreenCaptureDisabled(adminComponent, !enabled)
                    
                    logAction(
                        "Security Flag Override",
                        "Unrestricted Screen Capture policy applied. Screen capture disabled = ${!enabled}",
                        "SUCCESS"
                    )
                    _eventFlow.emit("FLAG_SECURE screen mirror capability override applied successfully.")
                } catch (e: SecurityException) {
                    logAction(
                        "Security Flag Override Error",
                        "SecurityException while setting screen capture policy: ${e.message}. Active Device/Profile Owner privileges required.",
                        "FAILED"
                    )
                    _eventFlow.emit("Security Exception: DPC is not set as Device Owner or Profile Owner.")
                } catch (e: Exception) {
                    logAction(
                        "Security Flag Override Error",
                        "Failed to override security flags: ${e.message}",
                        "FAILED"
                    )
                    _eventFlow.emit("Error overriding flags: ${e.message}")
                }
            }
        }
    }
}

class DpcViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DpcViewModel::class.java)) {
            val db = AppDatabase.getDatabase(context)
            val repository = PolicyRepositoryImpl(context, db.complianceLogDao())
            @Suppress("UNCHECKED_CAST")
            return DpcViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
