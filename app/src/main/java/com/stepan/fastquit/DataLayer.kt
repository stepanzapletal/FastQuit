package com.stepan.fastquit

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
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
@SuppressLint("StaticFieldLeak")
object IconMapper {

    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private fun s(@StringRes id: Int): String =
        requireNotNull(context) {
            "IconMapper not initialized. Call IconMapper.init(context) first."
        }.getString(id)

    val allIcons: Map<String, ImageVector> by lazy {
        mapOf(
            s(R.string.energy) to Icons.Rounded.Bolt,
            s(R.string.gaming) to Icons.Rounded.VideogameAsset,
            s(R.string.self_harm) to Icons.Rounded.ContentCut,
            s(R.string.gym) to Icons.Rounded.FitnessCenter,
            s(R.string.code) to Icons.Rounded.Code,
            s(R.string.smoking) to Icons.Rounded.SmokingRooms,
            s(R.string.alcohol) to Icons.Rounded.LocalDrink,
            s(R.string.spending) to Icons.Rounded.AttachMoney,
            s(R.string.sleep) to Icons.Rounded.Bedtime,
            s(R.string.reading) to Icons.Rounded.MenuBook,
            s(R.string.phone) to Icons.Rounded.Smartphone,
            s(R.string.social) to Icons.Rounded.Groups,
            s(R.string.food) to Icons.Rounded.Restaurant,
            s(R.string.shopping) to Icons.Rounded.ShoppingBag,
            s(R.string.work) to Icons.Rounded.Work,
            s(R.string.study) to Icons.Rounded.School,
            s(R.string.nature) to Icons.Rounded.Forest,
            s(R.string.music) to Icons.Rounded.MusicNote,
            s(R.string.tv) to Icons.Rounded.Tv,
            s(R.string.love) to Icons.Rounded.Favorite,
            s(R.string.time) to Icons.Rounded.Schedule,
            s(R.string.idea) to Icons.Rounded.Lightbulb,
            s(R.string.lock) to Icons.Rounded.Lock,
            s(R.string.key) to Icons.Rounded.VpnKey,
            s(R.string.home) to Icons.Rounded.Home,
            s(R.string.car) to Icons.Rounded.DirectionsCar,
            s(R.string.walk) to Icons.Rounded.DirectionsWalk,
            s(R.string.run) to Icons.Rounded.DirectionsRun,
            s(R.string.bike) to Icons.Rounded.PedalBike,
            s(R.string.water) to Icons.Rounded.WaterDrop,
            s(R.string.fire) to Icons.Rounded.LocalFireDepartment,
            s(R.string.leaf) to Icons.Rounded.Eco,
            s(R.string.build) to Icons.Rounded.Build,
            s(R.string.brush) to Icons.Rounded.Brush,
            s(R.string.camera) to Icons.Rounded.PhotoCamera,
            s(R.string.mic) to Icons.Rounded.Mic,
            s(R.string.chat) to Icons.Rounded.Chat,
            s(R.string.mail) to Icons.Rounded.Email,
            s(R.string.call) to Icons.Rounded.Call,
            s(R.string.delete) to Icons.Rounded.Delete,
            s(R.string.star) to Icons.Rounded.Star,
            s(R.string.heart) to Icons.Rounded.Favorite,
            s(R.string.warning) to Icons.Rounded.Warning,
            s(R.string.shield) to Icons.Rounded.Security,
            s(R.string.flag) to Icons.Rounded.Flag,
            s(R.string.map) to Icons.Rounded.Map
        )
    }

    val quickIcons: List<String> by lazy {
        listOf(
            s(R.string.energy),
            s(R.string.gaming),
            s(R.string.self_harm),
            s(R.string.gym),
            s(R.string.smoking),
            s(R.string.alcohol),
            s(R.string.code),
            s(R.string.sleep)
        )
    }

    fun getIcon(name: String): ImageVector =
        allIcons[name] ?: Icons.Rounded.Bolt
}
