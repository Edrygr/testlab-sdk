package app.testlab.sdk.models

data class ScreenView(
    val name: String,
    val entryTime: Long,
    val exitTime: Long? = null
) {
    val durationSeconds: Long
        get() = exitTime?.let { (it - entryTime) / 1000 } ?: ((System.currentTimeMillis() - entryTime) / 1000)
}
