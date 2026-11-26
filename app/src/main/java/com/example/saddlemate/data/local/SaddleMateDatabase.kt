package com.example.saddlemate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HorseEntity::class,
        RideEntity::class,
        RideSampleEntity::class,
        RideEventEntity::class,
        OwnRouteEntity::class,
        PendingVoicePinEntity::class,
        FeedbackNoteEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class SaddleMateDatabase : RoomDatabase() {
    abstract fun horseDao(): HorseDao
    abstract fun rideDao(): RideDao
    abstract fun ownRouteDao(): OwnRouteDao
    abstract fun pendingVoicePinDao(): PendingVoicePinDao
    abstract fun feedbackNoteDao(): FeedbackNoteDao

    companion object {
        /** v10 -> v11: horse heart rate columns (avg, max, 2-min recovery) on rides. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN avgHorseHrBpm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rides ADD COLUMN maxHorseHrBpm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rides ADD COLUMN recoveryHr2MinBpm INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v9 -> v10: priority and userStory columns on feedback_notes for admin backlog triage. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feedback_notes ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'")
                db.execSQL("ALTER TABLE feedback_notes ADD COLUMN userStory TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v8 -> v9: rider and horse calorie columns on rides. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN riderCalories INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rides ADD COLUMN horseCalories INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v7 -> v8: horse profile fields (breed, weight, height, photo, notes) and feedback_notes table. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE horses ADD COLUMN breed TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE horses ADD COLUMN weightKg REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE horses ADD COLUMN heightCm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE horses ADD COLUMN photoPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE horses ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS feedback_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        text TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v6 -> v7: rides are public by default, so they list as routes. Existing rides become public. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE rides SET visibility = 'public' WHERE visibility = 'private'")
            }
        }

        /** v5 -> v6: queue table for voice pins recorded but not yet turned into text. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_voice_pins (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        audioPath TEXT NOT NULL,
                        lat REAL NOT NULL,
                        lon REAL NOT NULL,
                        timeMillis INTEGER NOT NULL,
                        horseId TEXT NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v4 -> v5: table for the rider's own routes. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS own_routes (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        points TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        visibility TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v3 -> v4: name and note on rides. Old rides get an empty name and note. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rides ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 -> v3: ascent and descent on rides (ca., from GPS altitude). Old rides get 0. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN ascentM REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rides ADD COLUMN descentM REAL NOT NULL DEFAULT 0")
            }
        }

        /** v1 -> v2: route, owner and visibility on rides; new ride_events table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN routeId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rides ADD COLUMN ownerId TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("ALTER TABLE rides ADD COLUMN visibility TEXT NOT NULL DEFAULT 'private'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ride_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        rideId TEXT NOT NULL,
                        lat REAL NOT NULL,
                        lon REAL NOT NULL,
                        timeMillis INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        label TEXT NOT NULL,
                        note TEXT NOT NULL,
                        source TEXT NOT NULL,
                        FOREIGN KEY(rideId) REFERENCES rides(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ride_events_rideId ON ride_events(rideId)")
            }
        }
    }
}
