package com.stepan.fastquit

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// GLOBAL CONSTANTS
const val HABIT_DB_VERSION = 8
const val SETTINGS_DB_VERSION = 4

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
    val targetChangesCount: Int = 0,
    @ColumnInfo(defaultValue = "1")
    var habitTest: Int = 1
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
    val versionCode: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortIndex ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun getHabitFlow(id: Int): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Int): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity)

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Update
    suspend fun updateHabits(habits: List<HabitEntity>)

    @Insert
    suspend fun insertHistory(history: ResetHistoryEntity)

    @Query("SELECT * FROM reset_history WHERE habitId = :habitId ORDER BY endDate DESC")
    fun getHistoryForHabit(habitId: Int): Flow<List<ResetHistoryEntity>>

    @Insert
    suspend fun insertGoalChange(goalChange: GoalChangeHistoryEntity)

    @Query("SELECT * FROM goal_change_history WHERE habitId = :habitId ORDER BY changeDate DESC")
    fun getGoalChangesForHabit(habitId: Int): Flow<List<GoalChangeHistoryEntity>>

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
    version = HABIT_DB_VERSION,
    autoMigrations = [
        AutoMigration(from = 7, to = 8)
    ],
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fastquit_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun needsUpdate(context: Context): Boolean {
            val db = getDatabase(context)
            val storedVersion = db.habitDao().getDbVersion()?.versionCode ?: 0
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
    val hapticsGlobal: Boolean = true,
    val hapticsEvents: Boolean = true,
    val hapticsTimer: Boolean = true,
    val hapticsUI: Boolean = true,
    val hapticsWarnings: Boolean = true,
    val forceUpdateScreen: Boolean = false
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_preferences WHERE id = 0")
    fun getPreferences(): Flow<UserPreferences?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreferences(prefs: UserPreferences)

    @Query("SELECT * FROM settings_version_info WHERE id = 0")
    suspend fun getSettingsVersion(): SettingsVersionInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSettingsVersion(version: SettingsVersionInfo)
}

@Database(
    entities = [UserPreferences::class, SettingsVersionInfo::class],
    version = SETTINGS_DB_VERSION,
    autoMigrations = [
        AutoMigration(from = 3, to = 4)
    ],
    exportSchema = true
)
abstract class SettingsDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: SettingsDatabase? = null

        fun getDatabase(context: Context): SettingsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SettingsDatabase::class.java,
                    "settings_db"
                )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Ensure version row exists on open
                            db.execSQL(
                                "INSERT OR IGNORE INTO settings_version_info (id, versionCode, lastUpdated) " +
                                        "VALUES (0, $SETTINGS_DB_VERSION, ${System.currentTimeMillis()})"
                            )
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun needsUpdate(context: Context): Boolean {
            val db = getDatabase(context)
            val storedVersion = db.settingsDao().getSettingsVersion()?.versionCode ?: 0
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