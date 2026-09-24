package com.lukearnold.coachesassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlayerEntity::class, GameEntity::class, GameEventEntity::class, GamePlayerEntity::class],
    version = 13,
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
    abstract fun gamePlayerDao(): GamePlayerDao

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
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
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

/**
 * Moves each game's on-court and absent players out of the comma-separated games.onCourtCsv and
 * games.absentPlayerIdsCsv columns into a real game_players table with foreign keys, so deleting a
 * player or game cleans up after itself instead of relying on repository code to do it.
 *
 * Ids of players that no longer exist are dropped rather than copied, since they'd violate the new
 * foreign key. As in [MIGRATION_11_12], games is rebuilt to drop the old columns; that's safe with
 * game_events and game_players referencing it because foreign keys aren't enforced until onOpen.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `game_players` (`gameId` INTEGER NOT NULL,
            `playerId` INTEGER NOT NULL, `onCourt` INTEGER NOT NULL, `absent` INTEGER NOT NULL,
            PRIMARY KEY(`gameId`, `playerId`),
            FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
            FOREIGN KEY(`playerId`) REFERENCES `players`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_players_playerId` ON `game_players` (`playerId`)")

        val existingPlayerIds = mutableSetOf<Long>()
        db.query("SELECT `id` FROM `players`").use { cursor ->
            while (cursor.moveToNext()) existingPlayerIds += cursor.getLong(0)
        }
        fun parseIds(csv: String?): Set<Long> =
            csv.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it in existingPlayerIds }.toSet()

        db.query("SELECT `id`, `onCourtCsv`, `absentPlayerIdsCsv` FROM `games`").use { cursor ->
            while (cursor.moveToNext()) {
                val lineup = GameLineup(onCourtIds = parseIds(cursor.getString(1)), absentIds = parseIds(cursor.getString(2)))
                lineup.toEntities(cursor.getLong(0)).forEach { row ->
                    db.execSQL(
                        "INSERT INTO `game_players` (`gameId`, `playerId`, `onCourt`, `absent`) VALUES (?, ?, ?, ?)",
                        arrayOf<Any>(row.gameId, row.playerId, if (row.onCourt) 1 else 0, if (row.absent) 1 else 0),
                    )
                }
            }
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `games_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `title` TEXT NOT NULL, `opponent` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
            `teamScore` INTEGER NOT NULL, `opponentScore` INTEGER NOT NULL,
            `mvpVotesJson` TEXT NOT NULL, `runningTimeMillis` INTEGER NOT NULL,
            `timerLastStartedAt` INTEGER, `isFinished` INTEGER NOT NULL, `finishedAt` INTEGER,
            `currentPeriod` INTEGER NOT NULL, `periodDurationMillis` INTEGER NOT NULL,
            `notes` TEXT NOT NULL DEFAULT '', `sport` TEXT NOT NULL DEFAULT 'BASKETBALL',
            `onCourtSize` INTEGER NOT NULL DEFAULT 5, `periodCount` INTEGER NOT NULL DEFAULT 2)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `games_new` (`id`, `title`, `opponent`, `createdAt`, `teamScore`,
                `opponentScore`, `mvpVotesJson`, `runningTimeMillis`, `timerLastStartedAt`,
                `isFinished`, `finishedAt`, `currentPeriod`, `periodDurationMillis`, `notes`,
                `sport`, `onCourtSize`, `periodCount`)
            SELECT `id`, `title`, `opponent`, `createdAt`, `teamScore`, `opponentScore`,
                `mvpVotesJson`, `runningTimeMillis`, `timerLastStartedAt`, `isFinished`,
                `finishedAt`, `currentPeriod`, `periodDurationMillis`, `notes`, `sport`,
                `onCourtSize`, `periodCount`
            FROM `games`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `games`")
        db.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
    }
}
