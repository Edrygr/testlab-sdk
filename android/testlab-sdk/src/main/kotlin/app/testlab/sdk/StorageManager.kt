package app.testlab.sdk

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ─── Room entities ───────────────────────────────────────────────────────────

@Entity(tableName = "pending_events")
internal data class PendingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val testerId: String?,
    val apiKey: String,
    val name: String,
    val propertiesJson: String,
    val timestamp: Long
)

@Entity(tableName = "pending_sessions")
internal data class PendingSessionEntity(
    @PrimaryKey val sessionId: String,
    val apiKey: String,
    val testerId: String?,
    val payloadJson: String,
    val timestamp: Long
)

// ─── DAOs ────────────────────────────────────────────────────────────────────

@Dao
internal interface PendingEventDao {
    @Insert
    suspend fun insert(event: PendingEventEntity)

    @Query("SELECT * FROM pending_events ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 200): List<PendingEventEntity>

    @Delete
    suspend fun delete(events: List<PendingEventEntity>)

    @Query("SELECT COUNT(*) FROM pending_events")
    suspend fun count(): Int
}

@Dao
internal interface PendingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: PendingSessionEntity)

    @Query("SELECT * FROM pending_sessions")
    suspend fun getAll(): List<PendingSessionEntity>

    @Delete
    suspend fun delete(sessions: List<PendingSessionEntity>)
}

// ─── Database ────────────────────────────────────────────────────────────────

@Database(
    entities = [PendingEventEntity::class, PendingSessionEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class TestLabDatabase : RoomDatabase() {
    abstract fun pendingEventDao(): PendingEventDao
    abstract fun pendingSessionDao(): PendingSessionDao

    companion object {
        @Volatile private var instance: TestLabDatabase? = null

        fun getInstance(context: Context): TestLabDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TestLabDatabase::class.java,
                    "testlab_sdk.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

// ─── StorageManager ──────────────────────────────────────────────────────────

internal class StorageManager(context: Context) {
    private val db = TestLabDatabase.getInstance(context)
    private val gson = Gson()

    suspend fun savePendingEvent(
        sessionId: String,
        testerId: String?,
        apiKey: String,
        name: String,
        properties: Map<String, Any>,
        timestamp: Long
    ) {
        db.pendingEventDao().insert(
            PendingEventEntity(
                sessionId = sessionId,
                testerId = testerId,
                apiKey = apiKey,
                name = name,
                propertiesJson = gson.toJson(properties),
                timestamp = timestamp
            )
        )
    }

    suspend fun getPendingEvents(limit: Int = 200): List<PendingEventEntity> =
        db.pendingEventDao().getPending(limit)

    suspend fun deletePendingEvents(events: List<PendingEventEntity>) =
        db.pendingEventDao().delete(events)

    suspend fun pendingEventCount(): Int = db.pendingEventDao().count()

    suspend fun savePendingSession(
        sessionId: String,
        apiKey: String,
        testerId: String?,
        payloadJson: String
    ) {
        db.pendingSessionDao().insert(
            PendingSessionEntity(
                sessionId = sessionId,
                apiKey = apiKey,
                testerId = testerId,
                payloadJson = payloadJson,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getPendingSessions(): List<PendingSessionEntity> =
        db.pendingSessionDao().getAll()

    suspend fun deletePendingSessions(sessions: List<PendingSessionEntity>) =
        db.pendingSessionDao().delete(sessions)

    fun parseProperties(json: String): Map<String, Any> = try {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        gson.fromJson(json, type) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }
}
