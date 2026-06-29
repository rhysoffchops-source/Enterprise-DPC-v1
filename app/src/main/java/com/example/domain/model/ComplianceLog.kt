package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compliance_logs")
data class ComplianceLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val details: String,
    val status: String, // "SUCCESS", "FAILED", "SIMULATED"
    val isSimulated: Boolean = false
)
