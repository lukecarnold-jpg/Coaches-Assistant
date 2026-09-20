package com.example.basketballrotation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlayerEntity::class, GameEntity::class, GameEventEntity::class],
    version = 10,
    autoMigrations = [
        androidx.room.AutoMigration(from = 5, to = 6),
        androidx.room.AutoMigration(from = 6, to = 7),
        androidx.room.AutoMigration(from = 7, to = 8),
        androidx.room.AutoMigration(from = 8, to = 9),
        androidx.room.AutoMigration(from = 9, to = 10),
    ],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao
    abstract fun gameEventDao(): GameEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "basketball_rotation.db",
                )
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}