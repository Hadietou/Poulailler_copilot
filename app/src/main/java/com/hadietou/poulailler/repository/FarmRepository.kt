package com.hadietou.poulailler.repository

import com.hadietou.poulailler.data.FarmInfo
import com.hadietou.poulailler.data.FarmInfoDao

class FarmRepository(private val farmInfoDao: FarmInfoDao) {

    suspend fun getInfo(): FarmInfo? = farmInfoDao.getInfo()

    suspend fun saveInfo(
        farmName: String,
        currency: String,
        setupDate: Long = System.currentTimeMillis()
    ) {
        val currentInfo = getInfo()
        val info = FarmInfo(
            id = currentInfo?.id ?: 1,
            farmName = farmName,
            currency = currency,
            setupDate = currentInfo?.setupDate ?: setupDate
        )
        farmInfoDao.upsert(info)
    }
}
