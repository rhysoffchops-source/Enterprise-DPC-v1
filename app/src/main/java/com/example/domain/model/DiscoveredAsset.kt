package com.example.domain.model

data class DiscoveredAsset(
    val id: String,
    val deviceName: String,
    val deviceAddress: String,
    val isCorporate: Boolean,
    val assetTag: String = "",
    val verificationStatus: String = "UNVERIFIED", // "UNVERIFIED", "VERIFYING", "VERIFIED", "FAILED"
    val hasPendingUpdate: Boolean = isCorporate,
    val patchDeploymentStatus: String = "NOT_STARTED" // "NOT_STARTED", "DEPLOYING", "DEPLOYED", "FAILED"
)
