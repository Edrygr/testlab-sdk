package app.testlab.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class NetworkManager(
    private val config: TestLabConfig,
    private val context: Context
) {
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor(maxRetries = 3))
        .build()

    // ─── Connectivity ─────────────────────────────────────────────────────────

    fun isConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ─── API calls ────────────────────────────────────────────────────────────

    fun checkStatus(callback: (StatusResponse?) -> Unit) {
        val body = gson.toJson(mapOf("apiKey" to config.apiKey, "appId" to config.appId))
        post("${config.baseUrl}/sdk/status", body) { json ->
            callback(json?.let { parseOrNull<StatusResponse>(it) })
        }
    }

    fun createSession(
        testerId: String?,
        sessionData: Map<String, Any>,
        deviceData: Map<String, Any>,
        callback: (String?) -> Unit
    ) {
        val body = gson.toJson(
            mapOf(
                "apiKey" to config.apiKey,
                "testerId" to testerId,
                "sessionData" to sessionData,
                "deviceData" to deviceData
            )
        )
        post("${config.baseUrl}/sdk/session", body) { json ->
            @Suppress("UNCHECKED_CAST")
            callback(json?.let { (parseOrNull<Map<String, Any>>(it))?.get("sessionId") as? String })
        }
    }

    fun closeSession(
        sessionId: String,
        endTime: Long,
        durationSeconds: Long,
        screens: List<Map<String, Any>>,
        events: List<Map<String, Any>>,
        callback: (Boolean) -> Unit
    ) {
        val body = gson.toJson(
            mapOf(
                "endTime" to endTime,
                "duration" to durationSeconds,
                "screens" to screens,
                "events" to events
            )
        )
        patch("${config.baseUrl}/sdk/session/$sessionId", body) { json ->
            callback(json != null)
        }
    }

    fun sendEventBatch(
        testerId: String?,
        events: List<Map<String, Any>>,
        callback: (Boolean) -> Unit
    ) {
        val body = gson.toJson(
            mapOf(
                "apiKey" to config.apiKey,
                "testerId" to testerId,
                "events" to events
            )
        )
        post("${config.baseUrl}/sdk/events/batch", body) { callback(it != null) }
    }

    fun sendHeartbeat(testerId: String?, sessionId: String, callback: (Boolean) -> Unit) {
        val body = gson.toJson(
            mapOf(
                "apiKey" to config.apiKey,
                "testerId" to testerId,
                "sessionId" to sessionId
            )
        )
        post("${config.baseUrl}/sdk/heartbeat", body) { callback(it != null) }
    }

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    private fun post(url: String, json: String, callback: (String?) -> Unit) {
        execute(Request.Builder().url(url).post(json.toRequestBody(JSON)).build(), callback)
    }

    private fun patch(url: String, json: String, callback: (String?) -> Unit) {
        execute(Request.Builder().url(url).patch(json.toRequestBody(JSON)).build(), callback)
    }

    private fun execute(request: Request, callback: (String?) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null)
            override fun onResponse(call: Call, response: Response) {
                callback(if (response.isSuccessful) response.body?.string() else null)
            }
        })
    }

    private inline fun <reified T> parseOrNull(json: String): T? = try {
        gson.fromJson(json, T::class.java)
    } catch (e: Exception) {
        null
    }

    // ─── Models ───────────────────────────────────────────────────────────────

    data class StatusResponse(
        val active: Boolean = false,
        val testerId: String? = null,
        val status: String = "inactive"
    )
}

// ─── Retry Interceptor ────────────────────────────────────────────────────────

internal class RetryInterceptor(private val maxRetries: Int) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var lastException: IOException? = null

        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(chain.request())
                if (response.isSuccessful || attempt == maxRetries) return response
                response.close()
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxRetries) throw e
            }
            val backoffMs = (1000L shl attempt).coerceAtMost(8_000L)
            Thread.sleep(backoffMs)
            attempt++
        }

        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }
}
