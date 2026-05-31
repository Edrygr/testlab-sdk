package app.testlab.sdk

import android.app.Activity
import app.testlab.sdk.models.ScreenView
import java.util.concurrent.CopyOnWriteArrayList

internal class ScreenTracker(private val eventTracker: EventTracker) {

    private val history = CopyOnWriteArrayList<ScreenView>()
    private var current: ScreenView? = null
    private var totalScreenCount: Int? = null

    fun setTotalScreenCount(count: Int) {
        totalScreenCount = count
    }

    fun onActivityResumed(activity: Activity) {
        trackScreen(activity.javaClass.simpleName)
    }

    fun onActivityPaused(activity: Activity) {
        closeCurrentScreen()
    }

    fun trackScreen(name: String) {
        val now = System.currentTimeMillis()
        current?.let { history.add(it.copy(exitTime = now)) }
        current = ScreenView(name = name, entryTime = now)
        eventTracker.track(Events.SCREEN_VIEW, mapOf("screen" to name))
    }

    fun closeCurrentScreen() {
        current?.let {
            history.add(it.copy(exitTime = System.currentTimeMillis()))
            current = null
        }
    }

    fun getHistory(): List<ScreenView> =
        history.toMutableList().also { current?.let { s -> it.add(s) } }

    fun getUniqueScreenNames(): Set<String> =
        (history.map { it.name } + listOfNotNull(current?.name)).toSet()

    fun getCoveragePercentage(): Float? {
        val total = totalScreenCount ?: return null
        return if (total > 0) (getUniqueScreenNames().size.toFloat() / total) * 100f else 0f
    }

    fun reset() {
        history.clear()
        current = null
    }

    fun toPayload(): List<Map<String, Any>> = getHistory().map { screen ->
        mapOf(
            "name" to screen.name,
            "durationSeconds" to screen.durationSeconds,
            "timestamp" to screen.entryTime.toIso8601()
        )
    }
}
