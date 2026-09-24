package com.example.basketballrotation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlayerEntity::class, GameEntity::class, GameEventEntity::class],
    version = 12,
    autoMigrations = [
        androidx.room.AutoMigration(from = 5, to = 6),
        androidx.room.AutoMigration(from = 6, to = 7),
        androidx.room.AutoMigration(from = 7, to = 8),
        androidx.room.AutoMigration(from = 8, to = 9),
        androidx.room.AutoMigration(from = 9, to = 10),
        // Adds the game_events foreign keys (games -> CASCADE, players -> SET_NULL); no column
        // changes, so no AutoMigrationSpec is needed to backfill anything.
        androidx.room.AutoMigration(from = 10, to = 11),
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
                    .addMigrations(MIGRATION_11_12)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Replaces games.useQuarters (4 periods if set, otherwise 2) with games.periodCount, so a sport
 * like Ice Hockey can have 3. This is a manual migration because the new column has to be
 * backfilled from the one being dropped, which an AutoMigration can't do in one step.
 *
 * SQLite on older Android versions can't DROP COLUMN, so the table is rebuilt instead. Dropping
 * `games` is safe here even though game_events cascades on delete from it: Room only turns on
 * `PRAGMA foreign_keys` in onOpen, after migrations have run.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `games_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `title` TEXT NOT NULL, `opponent` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
            `teamScore` INTEGER NOT NULL, `opponentScore` INTEGER NOT NULL,
            `mvpVotesJson` TEXT NOT NULL, `onCourtCsv` TEXT NOT NULL,
            `runningTimeMillis` INTEGER NOT NULL, `timerLastStartedAt` INTEGER,
            `isFinished` INTEGER NOT NULL, `finishedAt` INTEGER, `currentPeriod` INTEGER NOT NULL,
            `periodDurationMillis` INTEGER NOT NULL, `absentPlayerIdsCsv` TEXT NOT NULL DEFAULT '',
            `notes` TEXT NOT NULL DEFAULT '', `sport` TEXT NOT NULL DEFAULT 'BASKETBALL',
            `onCourtSize` INTEGER NOT NULL DEFAULT 5, `periodCount` INTEGER NOT NULL DEFAULT 2)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `games_new` (`id`, `title`, `opponent`, `createdAt`, `teamScore`,
                `opponentScore`, `mvpVotesJson`, `onCourtCsv`, `runningTimeMillis`,
                `timerLastStartedAt`, `isFinished`, `finishedAt`, `currentPeriod`,
                `periodDurationMillis`, `absentPlayerIdsCsv`, `notes`, `sport`, `onCourtSize`,
                `periodCount`)
            SELECT `id`, `title`, `opponent`, `createdAt`, `teamScore`, `opponentScore`,
                `mvpVotesJson`, `onCourtCsv`, `runningTimeMillis`, `timerLastStartedAt`,
                `isFinished`, `finishedAt`, `currentPeriod`, `periodDurationMillis`,
                `absentPlayerIdsCsv`, `notes`, `sport`, `onCourtSize`,
                CASE WHEN `useQuarters` THEN 4 ELSE 2 END
            FROM `games`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `games`")
        db.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
    }
}
