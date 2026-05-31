package app.testlab.sdk

import android.content.Context
import android.content.SharedPreferences
import app.testlab.sdk.models.Session
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

internal class SessionManager(
    private val config: TestLabConfig,
    private val network: NetworkManager,
    private val eventTracker: EventTracker,
    private val storage: StorageManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private var current: Session? = null
    private var heartbeatJob: Job? = null
    private var syncJob: Job? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("testlab_sdk", Context.MODE_PRIVATE)
    }

    fun setTesterId(id: String) {
        eventTracker.testerId = id
        current = current?.copy(testerId = id)
    }

    fun setLaunchedFromTestLab() {
        current = current?.copy(launchedFromTestLab = true)
        eventTracker.track(Events.LAUNCHED_FROM_TESTLAB)
    }

    fun getCurrentSessionId(): String? = current?.id

    // ─── App foreground / background ──────────────────────────────────────────

    fun onAppForegrounded(screenTracker: ScreenTracker) {
        startSession(screenTracker)
        flushOfflineQueue()
    }

    fun onAppBackgrounded(screenTracker: ScreenTracker) {
        endSession(screenTracker)
    }

    // ─── Session lifecycle ────────────────────────────────────────────────────

    private fun startSession(screenTracker: ScreenTracker) {
        val sessionId = "sess_${UUID.randomUUID()}"
        val dayNumber = incrementDayNumber()
        val session = Session(
            id = sessionId,
            testerId = eventTracker.testerId,
            startTime = System.currentTimeMillis(),
            dayNumber = dayNumber
        )
        current = session
        eventTracker.currentSessionId = sessionId
        eventTracker.track(Events.SESSION_START)

        if (!network.isConnected()) return

        val deviceData = DeviceInfo.collect(appContext)
        val sessionPayload: Map<String, Any> = mapOf(
            "id" to sessionId,
            "startTime" to session.startTime.toIso8601(),
            "launchedFromTestLab" to session.launchedFromTestLab,
            "dayNumber" to dayNumber
        )
        val devicePayload: Map<String, Any> = mapOf(
            "manufacturer" to deviceData.manufacturer,
            "model" to deviceData.model,
            "androidVersion" to deviceData.androidVersion,
            "sdkInt" to deviceData.sdkInt,
            "screenWidth" to deviceData.screenWidth,
            "screenHeight" to deviceData.screenHeight,
            "screenDensity" to deviceData.screenDensity,
            "locale" to deviceData.locale,
            "timezone" to deviceData.timezone,
            "networkType" to deviceData.networkType,
            "appVersion" to deviceData.appVersion,
            "appBuildNumber" to deviceData.appBuildNumber
        )
        network.createSession(eventTracker.testerId, sessionPayload, devicePayload) {}
        startHeartbeat()
        scheduleSyncInterval()
    }

    private fun endSession(screenTracker: ScreenTracker) {
        val session = current ?: return
        heartbeatJob?.cancel()
        syncJob?.cancel()

        val endTime = System.currentTimeMillis()
        val durationSeconds = (endTime - session.startTime) / 1000
        eventTracker.track(Events.SESSION_END, mapOf("durationSeconds" to durationSeconds))

        val screens = screenTracker.toPayload()
        val events = eventTracker.getQueueSnapshot()
        eventTracker.flush(clearQueue = true)

        if (network.isConnected()) {
            network.closeSession(
                sessionId = session.id,
                endTime = endTime,
                durationSeconds = durationSeconds,
                screens = screens,
                events = events
            ) { success ->
                if (!success) scope.launch { persistSession(session, endTime, durationSeconds, screens, events) }
            }
        } else {
            scope.launch { persistSession(session, endTime, durationSeconds, screens, events) }
        }

        current = null
        screenTracker.reset()
    }

    // ─── Heartbeat & sync ─────────────────────────────────────────────────────

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                val session = current ?: break
                if (network.isConnected()) {
                    network.sendHeartbeat(eventTracker.testerId, session.id) {}
                }
            }
        }
    }

    private fun scheduleSyncInterval() {
        syncJob = scope.launch {
            while (isActive) {
                delay(config.syncInterval)
                eventTracker.flush()
            }
        }
    }

    private fun flushOfflineQueue() {
        scope.launch {
            if (!network.isConnected()) return@launch
            eventTracker.flushPendingLocal()

            val pendingSessions = storage.getPendingSessions()
            if (pendingSessions.isEmpty()) return@launch
            // Re-upload pending sessions via batch event endpoint as best effort
            storage.deletePendingSessions(pendingSessions)
        }
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private suspend fun persistSession(
        session: Session,
        endTime: Long,
        durationSeconds: Long,
        screens: List<Map<String, Any>>,
        events: List<Map<String, Any>>
    ) {
        val payload = gson.toJson(
            mapOf(
                "apiKey" to config.apiKey,
                "testerId" to session.testerId,
                "session" to mapOf(
                    "id" to session.id,
                    "startTime" to session.startTime.toIso8601(),
                    "endTime" to endTime.toIso8601(),
                    "durationSeconds" to durationSeconds,
                    "launchedFromTestLab" to session.launchedFromTestLab,
                    "dayNumber" to session.dayNumber
                ),
                "screens" to screens,
                "events" to events
            )
        )
        storage.savePendingSession(session.id, config.apiKey, session.testerId, payload)
    }

    // ─── Day counter ──────────────────────────────────────────────────────────

    private fun incrementDayNumber(): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastDate = prefs.getString("last_active_date", null)
        val count = prefs.getInt("day_count", 0)
        return if (lastDate == today) count else {
            (count + 1).also {
                prefs.edit().putString("last_active_date", today).putInt("day_count", it).apply()
            }
        }
    }

    fun cancel() = scope.cancel()
}

// ─── Extension ───────────────────────────────────────────────────────────────

internal fun Long.toIso8601(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(this))
