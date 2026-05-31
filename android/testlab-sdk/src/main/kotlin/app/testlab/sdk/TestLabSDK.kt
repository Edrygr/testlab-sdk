package app.testlab.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.*

/**
 * TestLab SDK — identity and engagement tracking layer for TestLab-verified testers.
 *
 * Usage:
 *   TestLabSDK.init(context, TestLabConfig(apiKey = "tl_live_...", appId = "com.myapp"))
 *   TestLabSDK.identify("usr_tester_id")
 */
object TestLabSDK {

    private var config: TestLabConfig? = null
    private var network: NetworkManager? = null
    private var storage: StorageManager? = null
    private var eventTracker: EventTracker? = null
    private var screenTracker: ScreenTracker? = null
    private var sessionManager: SessionManager? = null

    @Volatile private var isActive = false
    private val initScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var foregroundCount = 0
    private val foregroundLock = Any()

    // ─── Public API ───────────────────────────────────────────────────────────

    fun init(context: Context, config: TestLabConfig) {
        val appContext = context.applicationContext
        this.config = config

        val net = NetworkManager(config, appContext)
        net.checkStatus { response ->
            if (response?.active == true) {
                activate(appContext, config, net, response.testerId)
            }
        }
    }

    fun identify(testerId: String) {
        if (!isActive) return
        sessionManager?.setTesterId(testerId)
        log("Tester identified: $testerId")
    }

    fun trackScreen(screenName: String) {
        if (!isActive) return
        screenTracker?.trackScreen(screenName)
    }

    fun trackEvent(eventName: String, properties: Map<String, Any> = emptyMap()) {
        if (!isActive) return
        eventTracker?.track(eventName, properties)
    }

    fun launchedFromTestLab() {
        if (!isActive) return
        sessionManager?.setLaunchedFromTestLab()
    }

    fun registerTotalScreenCount(count: Int) {
        screenTracker?.setTotalScreenCount(count)
    }

    fun getScreenCoverage(): Float? = screenTracker?.getCoveragePercentage()

    fun shutdown() {
        isActive = false
        eventTracker?.cancel()
        sessionManager?.cancel()
        eventTracker?.track(Events.SDK_DEACTIVATED)
        log("SDK shut down remotely")
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun activate(
        context: Context,
        config: TestLabConfig,
        net: NetworkManager,
        remoteTester: String?
    ) {
        val store = StorageManager(context)
        val events = EventTracker(config, net, store)
        val screens = ScreenTracker(events)
        val sessions = SessionManager(config, net, events, store)
        sessions.init(context)

        if (remoteTester != null) {
            events.testerId = remoteTester
        }

        storage = store
        network = net
        eventTracker = events
        screenTracker = screens
        sessionManager = sessions

        val application = context as? Application
            ?: throw IllegalArgumentException("TestLabSDK.init() requires Application context")

        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)

        isActive = true
        events.track(Events.SDK_ACTIVATED)
        log("SDK activated")

        scheduleStatusPolling(config, net)
    }

    private fun scheduleStatusPolling(config: TestLabConfig, net: NetworkManager) {
        initScope.launch {
            while (isActive) {
                delay(config.syncInterval)
                net.checkStatus { response ->
                    when {
                        response == null -> Unit
                        !response.active && isActive -> shutdown()
                        response.active && !isActive -> Unit
                    }
                }
            }
        }
    }

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            synchronized(foregroundLock) {
                if (++foregroundCount == 1) {
                    sessionManager?.onAppForegrounded(screenTracker ?: return)
                }
            }
        }

        override fun onActivityStopped(activity: Activity) {
            synchronized(foregroundLock) {
                if (--foregroundCount == 0) {
                    sessionManager?.onAppBackgrounded(screenTracker ?: return)
                }
            }
        }

        override fun onActivityResumed(activity: Activity) {
            screenTracker?.onActivityResumed(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            screenTracker?.onActivityPaused(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private fun log(msg: String) {
        if (config?.debug == true) android.util.Log.d("TestLabSDK", msg)
    }
}
