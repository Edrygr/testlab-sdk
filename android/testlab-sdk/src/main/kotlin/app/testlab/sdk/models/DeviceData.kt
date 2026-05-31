package app.testlab.sdk.models

data class DeviceData(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val screenDensity: Float,
    val locale: String,
    val timezone: String,
    val networkType: String,
    val appVersion: String,
    val appBuildNumber: Int
)
