package com.example.poulailler_copilot.repository

import com.example.poulailler_copilot.data.EggEntry
import com.example.poulailler_copilot.data.EggEntryDao

class EggRepository(private val eggEntryDao: EggEntryDao) {

    suspend fun addEntry(userId: Long, date: Long, eggs: Int, broken: Int, remarks: String?) {
        eggEntryDao.insert(
            EggEntry(
                userId = userId,
                date = date,
                eggsCount = eggs,
                brokenEggsCount = broken,
                remarks = remarks
            )
        )
    }

    suspend fun getAll(): List<EggEntry> = eggEntryDao.getAll()

    suspend fun getByUser(userId: Long): List<EggEntry> = eggEntryDao.getByUser(userId)

    suspend fun getTotalEggs(): Int = eggEntryDao.getTotalEggs() ?: 0
}
