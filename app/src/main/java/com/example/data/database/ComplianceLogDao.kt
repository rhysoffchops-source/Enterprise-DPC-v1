package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.domain.model.ComplianceLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ComplianceLogDao {
    @Query("SELECT * FROM compliance_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ComplianceLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ComplianceLog)

    @Query("DELETE FROM compliance_logs")
    suspend fun deleteAllLogs()
}
