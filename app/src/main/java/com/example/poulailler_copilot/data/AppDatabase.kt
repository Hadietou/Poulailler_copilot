package com.example.poulailler_copilot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [User::class, EggEntry::class, FarmInfo::class, EggSale::class, Expense::class, VaccineEntry::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun eggEntryDao(): EggEntryDao
    abstract fun farmInfoDao(): FarmInfoDao
    abstract fun eggSaleDao(): EggSaleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun vaccineEntryDao(): VaccineEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE egg_entries ADD COLUMN brokenEggsCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `vaccine_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `date` INTEGER NOT NULL, `remarks` TEXT)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "poulailler_db"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
