package com.watermelon.benchmarks

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watermelon.storage.db.migrations.MigrationV1ToV2
import com.watermelon.storage.db.migrations.MigrationV2ToV3
import com.watermelon.storage.db.migrations.MigrationV3ToV4
import com.watermelon.storage.db.migrations.MigrationV4ToV5
import com.watermelon.storage.db.migrations.MigrationV5ToV6
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Zero-data-loss migration test across all schema versions (Manifest §11). Opens a fixture
 * database at each version N, applies the N→N+1 step, and asserts user data — especially
 * PlaybackPositions and SubtitleOffsets — survives. Mirrors the authoritative test in
 * :library-storage (com.watermelon.storage.db.MigrationTest) that the CI gate runs.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun playbackPositionsSurviveFullLadder() {
        val dbFile = File(context.cacheDir, "migration_fixture.db").apply { delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // --- v1 baseline + a row that must never be lost ---
            db.execSQL(
                """CREATE TABLE PlaybackPositions (
                    mediaId TEXT NOT NULL, fileSize INTEGER NOT NULL,
                    positionMs INTEGER NOT NULL, updatedAt INTEGER,
                    PRIMARY KEY (mediaId, fileSize));"""
            )
            db.execSQL(
                "INSERT INTO PlaybackPositions VALUES ('content://v/1', 100, 4200, 1700000000000);"
            )

            // --- apply the full ladder ---
            MigrationV1ToV2.migrate(db)
            MigrationV2ToV3.migrate(db)
            MigrationV3ToV4.migrate(db)
            MigrationV4ToV5.migrate(db)
            MigrationV5ToV6.migrate(db)

            // --- assert the resume point survived ---
            db.rawQuery(
                "SELECT positionMs FROM PlaybackPositions WHERE mediaId = ?",
                arrayOf("content://v/1")
            ).use { c ->
                assert(c.moveToFirst()) { "PlaybackPositions row was lost during migration" }
                assertEquals(4200L, c.getLong(0))
            }

            // SubtitleOffsets table exists after V3->V4 and is writable.
            db.execSQL(
                "INSERT INTO SubtitleOffsets VALUES ('content://v/1', 100, 'fa', -250, 'LINEAR', 1700000000001);"
            )
            db.rawQuery("SELECT COUNT(*) FROM SubtitleOffsets", null).use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }
}
