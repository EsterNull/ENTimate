package com.example.entimate.data.local

import androidx.room.*

@Dao
interface SubmissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubmission(submission: SubmissionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubmissionValue(value: SubmissionFieldValueEntity)

    @Query("DELETE FROM submissions")
    suspend fun deleteAll()

    @Transaction
    @Query("SELECT * FROM submissions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getWithValuesInPeriod(from: Long, to: Long): List<SubmissionWithValues>

    @Query("SELECT MIN(timestamp) FROM submissions")
    suspend fun getEarliestTime(): Long?
}
