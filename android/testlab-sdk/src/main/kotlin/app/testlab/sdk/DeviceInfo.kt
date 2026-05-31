package app.testlab.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import app.testlab.sdk.models.DeviceData
import java.util.Locale
import java.util.TimeZone

internal object DeviceInfo {

    fun collect(context: Context): DeviceData {
        val metrics = getDisplayMetrics(context)
        return DeviceData(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            screenDensity = metrics.density,
            locale = Locale.getDefault().toString(),
            timezone = TimeZone.getDefault().id,
            networkType = getNetworkType(context),
            appVersion = getAppVersion(context),
            appBuildNumber = getAppBuildNumber(context)
        )
    }

    @Suppress("DEPRECATION")
    private fun getDisplayMetrics(context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.getRealMetrics(metrics)
        } else {
            wm.defaultDisplay.getRealMetrics(metrics)
        }
        return metrics
    }

    fun getNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "OFFLINE"
        val caps = cm.getNetworkCapabilities(network) ?: return "OFFLINE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                ) "5G" else "4G"
            }
            else -> "UNKNOWN"
        }
    }

    private fun getAppVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

    @Suppress("DEPRECATION")
    private fun getAppBuildNumber(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    } catch (e: Exception) {
        0
    }
}
