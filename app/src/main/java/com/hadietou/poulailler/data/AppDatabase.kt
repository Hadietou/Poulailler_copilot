package com.hadietou.poulailler.data

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
        Mortality::class,
        Batch::class,
        HealthReminder::class
    ],
    version = 14,
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
    abstract fun batchDao(): BatchDao
    abstract fun healthReminderDao(): HealthReminderDao

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
