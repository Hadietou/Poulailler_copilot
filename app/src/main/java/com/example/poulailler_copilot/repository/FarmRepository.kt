package com.example.poulailler_copilot.repository

import com.example.poulailler_copilot.data.FarmInfo
import com.example.poulailler_copilot.data.FarmInfoDao

class FarmRepository(private val farmInfoDao: FarmInfoDao) {

    suspend fun getInfo(): FarmInfo? = farmInfoDao.getInfo()

    suspend fun saveInfo(
        hensCount: Int,
        feedInfo: String,
        mortality: Int,
        expenses: Double,
        setupDate: Long = System.currentTimeMillis()
    ) {
        val currentInfo = getInfo()
        val info = FarmInfo(
            id = currentInfo?.id ?: 1,
            hensCount = hensCount,
            feedInfo = feedInfo,
            mortality = mortality,
            expenses = expenses,
            setupDate = currentInfo?.setupDate ?: setupDate
        )
        farmInfoDao.upsert(info)
    }
}
