package com.example.domain.model

data class PolicyState(
    val isDeviceAdmin: Boolean = false,
    val isProfileOwner: Boolean = false,
    val isCameraDisabled: Boolean = false,
    val isScreenCaptureDisabled: Boolean = false,
    val isSmsDisabled: Boolean = false,
    val isOutgoingCallsDisabled: Boolean = false,
    val passwordMinimumLength: Int = 0,
    val passwordQuality: Int = 0, // DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED
    val isSimulatedMode: Boolean = false,
    
    // Remote Screen viewing
    val isRemoteViewingActive: Boolean = false,
    val supportAgentName: String = "IT Support Desk",
    val supportSessionId: String = "SES-5910-38A",
    
    // Log troubleshooting
    val isLogCollectionRunning: Boolean = false,
    val logCollectionProgress: Float = 0f,
    
    // Asset inventory specs
    val assetTag: String = "ENT-8392-X",
    val deviceModel: String = "Enterprise Device",
    val osVersion: String = "Android 14 (API 34)",
    val serialNumber: String = "S/N F892H19KSA",
    val batteryLevel: Int = 85,
    val totalStorageGB: Double = 128.0,
    val availableStorageGB: Double = 64.2,
    val ipAddress: String = "10.0.2.15",
    val macAddress: String = "02:00:00:00:00:00",
    val workDomain: String = "enterprise.corp",
    val isSecurityFlagOverrideActive: Boolean = false
)
