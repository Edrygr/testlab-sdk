package app.testlab.sdk

import app.testlab.sdk.models.TesterEvent
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

internal object Events {
    const val SESSION_START = "session_start"
    const val SESSION_END = "session_end"
    const val SCREEN_VIEW = "screen_view"
    const val LAUNCHED_FROM_TESTLAB = "launched_from_testlab"
    const val SDK_ACTIVATED = "sdk_activated"
    const val SDK_DEACTIVATED = "sdk_deactivated"
}

internal class EventTracker(
    private val config: TestLabConfig,
    private val network: NetworkManager,
    private val storage: StorageManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = CopyOnWriteArrayList<TesterEvent>()
    private val maxQueueSize = 500
    private val gson = Gson()

    var testerId: String? = null
    var currentSessionId: String? = null

    fun track(name: String, properties: Map<String, Any> = emptyMap()) {
        val sessionId = currentSessionId ?: return
        if (queue.size >= maxQueueSize) return
        queue.add(TesterEvent(sessionId = sessionId, testerId = testerId, name = name, properties = properties))
        if (config.debug) android.util.Log.d("TestLabSDK", "Event tracked: $name")
    }

    fun getQueueSnapshot(): List<Map<String, Any>> = queue.map { event ->
        mapOf(
            "name" to event.name,
            "properties" to event.properties,
            "timestamp" to event.timestamp
        )
    }

    fun flush(clearQueue: Boolean = true, onComplete: (() -> Unit)? = null) {
        if (queue.isEmpty()) { onComplete?.invoke(); return }

        val batch = queue.toList()
        if (clearQueue) queue.clear()

        scope.launch {
            if (network.isConnected()) {
                val payload = batch.map { e ->
                    mapOf("name" to e.name, "properties" to e.properties, "timestamp" to e.timestamp)
                }
                network.sendEventBatch(testerId, payload) { success ->
                    if (!success) scope.launch { persistBatch(batch) }
                    onComplete?.invoke()
                }
            } else {
                persistBatch(batch)
                onComplete?.invoke()
            }
        }
    }

    fun flushPendingLocal() {
        scope.launch {
            if (!network.isConnected()) return@launch
            val pending = storage.getPendingEvents(200)
            if (pending.isEmpty()) return@launch

            val payload = pending.map { entity ->
                mapOf(
                    "name" to entity.name,
                    "properties" to storage.parseProperties(entity.propertiesJson),
                    "timestamp" to entity.timestamp
                )
            }
            network.sendEventBatch(testerId, payload) { success ->
                if (success) scope.launch { storage.deletePendingEvents(pending) }
            }
        }
    }

    private suspend fun persistBatch(events: List<TesterEvent>) {
        events.forEach { event ->
            storage.savePendingEvent(
                sessionId = event.sessionId,
                testerId = event.testerId,
                apiKey = config.apiKey,
                name = event.name,
                properties = event.properties,
                timestamp = event.timestamp
            )
        }
    }

    fun cancel() = scope.cancel()
}
