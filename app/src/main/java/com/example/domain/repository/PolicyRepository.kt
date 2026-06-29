package com.example.domain.repository

import com.example.domain.model.ComplianceLog
import com.example.domain.model.PolicyState
import kotlinx.coroutines.flow.Flow

interface PolicyRepository {
    fun getPolicyState(): Flow<PolicyState>
    fun getComplianceLogs(): Flow<List<ComplianceLog>>
    suspend fun addComplianceLog(log: ComplianceLog)
    suspend fun clearComplianceLogs()
    suspend fun updatePolicyState(state: PolicyState)
}
