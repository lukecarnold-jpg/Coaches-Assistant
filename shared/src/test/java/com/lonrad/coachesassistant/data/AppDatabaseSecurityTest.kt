package com.lonrad.coachesassistant.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The file-level settings the app's database opens with; the schema itself is covered elsewhere. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseSecurityTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, "secure-delete-test.db")
            .addCallback(AppDatabase.SecureDelete)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("secure-delete-test.db")
    }

    @Test
    fun secureDelete_isOnForEveryOpen() {
        database.openHelper.readableDatabase.query("PRAGMA secure_delete").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun compactAfterDelete_runsAfterClearingData() = runBlocking {
        GameRepository(database).run {
            addPlayer(Sport.BASKETBALL, "Player 1", 1)
            clearAllData()
        }
        assertEquals(emptyList<Any>(), database.playerDao().getAll())
    }
}
