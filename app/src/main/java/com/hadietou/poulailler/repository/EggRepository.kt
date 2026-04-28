package com.hadietou.poulailler.repository

import com.hadietou.poulailler.data.EggEntry
import com.hadietou.poulailler.data.EggEntryDao

class EggRepository(private val eggEntryDao: EggEntryDao) {

    suspend fun addEntry(userId: String, date: Long, eggs: Int, broken: Int, remarks: String?) {
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

    suspend fun getByUser(userId: String): List<EggEntry> = eggEntryDao.getByUser(userId)

    suspend fun getTotalEggs(): Int = eggEntryDao.getTotalEggs() ?: 0
}
