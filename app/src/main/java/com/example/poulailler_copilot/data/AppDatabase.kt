package com.example.poulailler_copilot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class, 
        EggEntry::class, 
        FarmInfo::class, 
        EggSale::class, 
        Expense::class, 
        VaccineEntry::class, 
        LoginEntry::class,
        Mortality::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun eggEntryDao(): EggEntryDao
    abstract fun farmInfoDao(): FarmInfoDao
    abstract fun eggSaleDao(): EggSaleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun vaccineEntryDao(): VaccineEntryDao
    abstract fun loginDao(): LoginDao
    abstract fun mortalityDao(): MortalityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "poulailler_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
