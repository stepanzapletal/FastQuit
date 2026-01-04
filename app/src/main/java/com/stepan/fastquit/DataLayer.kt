package com.stepan.fastquit

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// GLOBAL CONSTANTS
const val HABIT_DB_VERSION = 7  // Increased from 6
const val SETTINGS_DB_VERSION = 2  // Increased from 1

// ================== MAIN DATABASE (HABITS) ==================

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val iconName: String,
    val startTime: Long,
    val lastResetTime: Long,
    val targetSeconds: Long,
    val goalLabel: String,
    val sortIndex: Int,
    val completions: Int = 0,
    val targetChangesCount: Int = 0
)

@Entity(
    tableName = "reset_history",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("habitId")]
)
data class ResetHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val startDate: Long,
    val endDate: Long,
    val durationSeconds: Long
)

@Entity(
    tableName = "goal_change_history",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("habitId")]
)
data class GoalChangeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val changeDate: Long,
    val oldTargetSeconds: Long,
    val newTargetSeconds: Long,
    val oldGoalLabel: String,
    val newGoalLabel: String,
    val changeType: String, // "EXTEND" or "UPDATE"
    val resetTimer: Boolean
)

@Entity(tableName = "db_version_info")
data class DbVersionInfo(
    @PrimaryKey val id: Int = 0,
    val versionCode: Int,  // The stored schema version
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortIndex ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun getHabitFlow(id: Int): Flow<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Int): HabitEntity?

    @Insert
    suspend fun insert(habit: HabitEntity)

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Update
    suspend fun updateHabits(habits: List<HabitEntity>)

    // Reset History
    @Insert
    suspend fun insertHistory(history: ResetHistoryEntity)

    @Query("SELECT * FROM reset_history WHERE habitId = :habitId ORDER BY endDate DESC")
    fun getHistoryForHabit(habitId: Int): Flow<List<ResetHistoryEntity>>

    // Goal Change History
    @Insert
    suspend fun insertGoalChange(goalChange: GoalChangeHistoryEntity)

    @Query("SELECT * FROM goal_change_history WHERE habitId = :habitId ORDER BY changeDate DESC")
    fun getGoalChangesForHabit(habitId: Int): Flow<List<GoalChangeHistoryEntity>>

    // Version Info
    @Query("SELECT * FROM db_version_info WHERE id = 0")
    suspend fun getDbVersion(): DbVersionInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setDbVersion(version: DbVersionInfo)
}

@Database(
    entities = [
        HabitEntity::class,
        ResetHistoryEntity::class,
        GoalChangeHistoryEntity::class,
        DbVersionInfo::class
    ],
    version = HABIT_DB_VERSION
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Migration from version 6 to 7
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new tables
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS goal_change_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        habitId INTEGER NOT NULL,
                        changeDate INTEGER NOT NULL,
                        oldTargetSeconds INTEGER NOT NULL,
                        newTargetSeconds INTEGER NOT NULL,
                        oldGoalLabel TEXT NOT NULL,
                        newGoalLabel TEXT NOT NULL,
                        changeType TEXT NOT NULL,
                        resetTimer INTEGER NOT NULL,
                        FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS db_version_info (
                        id INTEGER PRIMARY KEY NOT NULL,
                        versionCode INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Create index
                database.execSQL("CREATE INDEX IF NOT EXISTS index_goal_change_history_habitId ON goal_change_history(habitId)")

                // Check if row exists before inserting
                val cursor = database.query("SELECT COUNT(*) FROM db_version_info WHERE id = 0")
                val rowExists = try {
                    cursor.moveToFirst() && cursor.getInt(0) > 0
                } finally {
                    cursor.close()
                }

                if (!rowExists) {
                    // Insert initial version only if it doesn't exist
                    database.execSQL(
                        """
                        INSERT INTO db_version_info (id, versionCode, lastUpdated) 
                        VALUES (0, 7, ${System.currentTimeMillis()})
                        """.trimIndent()
                    )
                } else {
                    // Update existing row
                    database.execSQL(
                        """
                        UPDATE db_version_info 
                        SET versionCode = 7, lastUpdated = ${System.currentTimeMillis()}
                        WHERE id = 0
                        """.trimIndent()
                    )
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "fastquit_db")
                    .addMigrations(MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Create version table first
                            db.execSQL(
                                """
                            CREATE TABLE IF NOT EXISTS db_version_info (
                                id INTEGER PRIMARY KEY NOT NULL,
                                versionCode INTEGER NOT NULL,
                                lastUpdated INTEGER NOT NULL
                            )
                            """.trimIndent()
                            )
                            // Then insert initial version
                            db.execSQL(
                                "INSERT INTO db_version_info (id, versionCode, lastUpdated) " +
                                        "VALUES (0, $HABIT_DB_VERSION, ${System.currentTimeMillis()})"
                            )
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Just verify the table exists and has data
                            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='db_version_info'").use { cursor ->
                                if (cursor.moveToFirst()) {
                                    // Table exists, verify it has a row
                                    db.query("SELECT COUNT(*) FROM db_version_info WHERE id = 0").use { countCursor ->
                                        if (countCursor.moveToFirst() && countCursor.getInt(0) == 0) {
                                            // Table exists but no row, insert one
                                            db.execSQL(
                                                "INSERT OR REPLACE INTO db_version_info (id, versionCode, lastUpdated) " +
                                                        "VALUES (0, $HABIT_DB_VERSION, ${System.currentTimeMillis()})"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }

        // Helper to get current stored version
        suspend fun getCurrentVersion(context: Context): Int {
            return getDatabase(context).habitDao().getDbVersion()?.versionCode ?: 0
        }

        // Check if database needs update
        suspend fun needsUpdate(context: Context): Boolean {
            val storedVersion = getCurrentVersion(context)
            return storedVersion < HABIT_DB_VERSION
        }
    }
}

// ================== SETTINGS DATABASE (PREFS) ==================

@Entity(tableName = "settings_version_info")
data class SettingsVersionInfo(
    @PrimaryKey val id: Int = 0,
    val versionCode: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 0,
    val language: String = "English",
    val theme: String = "System",
    val notificationsEnabled: Boolean = true,
    val autoUpdateEnabled: Boolean = true,
    val updateFrequency: String = "1 Hour",
    // HAPTICS SECTION
    val hapticsGlobal: Boolean = true,
    val hapticsEvents: Boolean = true,  // Achievements/Goal Reached
    val hapticsTimer: Boolean = true,   // Every second "Tick"
    val hapticsUI: Boolean = true,      // Buttons/Switches
    val hapticsWarnings: Boolean = true // Resets/Nukes
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_preferences WHERE id = 0")
    fun getPreferences(): Flow<UserPreferences?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreferences(prefs: UserPreferences)

    // Version Info for Settings DB
    @Query("SELECT * FROM settings_version_info WHERE id = 0")
    suspend fun getSettingsVersion(): SettingsVersionInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSettingsVersion(version: SettingsVersionInfo)
}

@Database(
    entities = [
        UserPreferences::class,
        SettingsVersionInfo::class
    ],
    version = SETTINGS_DB_VERSION
)
abstract class SettingsDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: SettingsDatabase? = null

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create version info table if it doesn't exist
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS settings_version_info (
                        id INTEGER PRIMARY KEY NOT NULL,
                        versionCode INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Check if row exists before inserting
                val cursor = database.query("SELECT COUNT(*) FROM settings_version_info WHERE id = 0")
                val rowExists = try {
                    cursor.moveToFirst() && cursor.getInt(0) > 0
                } finally {
                    cursor.close()
                }

                if (!rowExists) {
                    // Insert initial version only if it doesn't exist
                    database.execSQL(
                        """
                        INSERT INTO settings_version_info (id, versionCode, lastUpdated) 
                        VALUES (0, 2, ${System.currentTimeMillis()})
                        """.trimIndent()
                    )
                } else {
                    // Update existing row
                    database.execSQL(
                        """
                        UPDATE settings_version_info 
                        SET versionCode = 2, lastUpdated = ${System.currentTimeMillis()}
                        WHERE id = 0
                        """.trimIndent()
                    )
                }
            }
        }

        fun getDatabase(context: Context): SettingsDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, SettingsDatabase::class.java, "settings_db")
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Create version table first
                            db.execSQL(
                                """
                            CREATE TABLE IF NOT EXISTS settings_version_info (
                                id INTEGER PRIMARY KEY NOT NULL,
                                versionCode INTEGER NOT NULL,
                                lastUpdated INTEGER NOT NULL
                            )
                            """.trimIndent()
                            )
                            // Then insert initial version
                            db.execSQL(
                                "INSERT INTO settings_version_info (id, versionCode, lastUpdated) " +
                                        "VALUES (0, $SETTINGS_DB_VERSION, ${System.currentTimeMillis()})"
                            )
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Just verify the table exists and has data
                            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='settings_version_info'").use { cursor ->
                                if (cursor.moveToFirst()) {
                                    // Table exists, verify it has a row
                                    db.query("SELECT COUNT(*) FROM settings_version_info WHERE id = 0").use { countCursor ->
                                        if (countCursor.moveToFirst() && countCursor.getInt(0) == 0) {
                                            // Table exists but no row, insert one
                                            db.execSQL(
                                                "INSERT OR REPLACE INTO settings_version_info (id, versionCode, lastUpdated) " +
                                                        "VALUES (0, $SETTINGS_DB_VERSION, ${System.currentTimeMillis()})"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }

        // Helper to get current stored version
        suspend fun getCurrentVersion(context: Context): Int {
            return getDatabase(context).settingsDao().getSettingsVersion()?.versionCode ?: 0
        }

        // Check if database needs update
        suspend fun needsUpdate(context: Context): Boolean {
            val storedVersion = getCurrentVersion(context)
            return storedVersion < SETTINGS_DB_VERSION
        }
    }
}

// ================== UTILS ==================

object IconMapper {
    val allIcons = mapOf(
        "Energy" to Icons.Rounded.Bolt, "Gaming" to Icons.Rounded.VideogameAsset, "Gym" to Icons.Rounded.FitnessCenter,
        "Code" to Icons.Rounded.Code, "Smoking" to Icons.Rounded.SmokingRooms, "Alcohol" to Icons.Rounded.LocalDrink,
        "Self Harm" to Icons.Rounded.ContentCut, "Spending" to Icons.Rounded.AttachMoney, "Sleep" to Icons.Rounded.Bedtime,
        "Reading" to Icons.Rounded.MenuBook, "Phone" to Icons.Rounded.Smartphone, "Social" to Icons.Rounded.Groups,
        "Food" to Icons.Rounded.Restaurant, "Shopping" to Icons.Rounded.ShoppingBag, "Work" to Icons.Rounded.Work,
        "Study" to Icons.Rounded.School, "Nature" to Icons.Rounded.Forest, "Music" to Icons.Rounded.MusicNote,
        "TV" to Icons.Rounded.Tv, "Love" to Icons.Rounded.Favorite, "Time" to Icons.Rounded.Schedule,
        "Idea" to Icons.Rounded.Lightbulb, "Lock" to Icons.Rounded.Lock, "Key" to Icons.Rounded.VpnKey,
        "Home" to Icons.Rounded.Home, "Car" to Icons.Rounded.DirectionsCar, "Walk" to Icons.Rounded.DirectionsWalk,
        "Run" to Icons.Rounded.DirectionsRun, "Bike" to Icons.Rounded.PedalBike, "Water" to Icons.Rounded.WaterDrop,
        "Fire" to Icons.Rounded.LocalFireDepartment, "Leaf" to Icons.Rounded.Eco, "Build" to Icons.Rounded.Build,
        "Brush" to Icons.Rounded.Brush, "Camera" to Icons.Rounded.PhotoCamera, "Mic" to Icons.Rounded.Mic,
        "Chat" to Icons.Rounded.Chat, "Mail" to Icons.Rounded.Email, "Call" to Icons.Rounded.Call,
        "Delete" to Icons.Rounded.Delete, "Star" to Icons.Rounded.Star, "Heart" to Icons.Rounded.Favorite,
        "Warning" to Icons.Rounded.Warning, "Shield" to Icons.Rounded.Security, "Flag" to Icons.Rounded.Flag,
        "Map" to Icons.Rounded.Map
    )
    val quickIcons = listOf("Energy", "Gaming", "Gym", "Smoking", "Alcohol", "Self Harm", "Code", "Sleep")
    fun getIcon(name: String): ImageVector = allIcons[name] ?: Icons.Rounded.Bolt
}