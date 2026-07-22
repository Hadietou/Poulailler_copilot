package com.hadietou.poulailler.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthReminderDao {
    @Insert
    suspend fun insert(reminder: HealthReminder)

    @Update
    suspend fun update(reminder: HealthReminder)

    @Delete
    suspend fun delete(reminder: HealthReminder)

    @Query("SELECT * FROM health_reminders WHERE isDone = 0 ORDER BY dueDate ASC")
    fun getActiveReminders(): Flow<List<HealthReminder>>

    @Query("SELECT * FROM health_reminders WHERE batchId = :batchId AND isDone = 0 ORDER BY dueDate ASC")
    fun getActiveRemindersForBatch(batchId: String): Flow<List<HealthReminder>>

    @Query("SELECT * FROM health_reminders ORDER BY dueDate DESC")
    fun getAllReminders(): Flow<List<HealthReminder>>

    @Query("SELECT * FROM health_reminders WHERE title = :title AND batchId = :batchId LIMIT 1")
    suspend fun getReminderByTitle(title: String, batchId: String): HealthReminder?
}
