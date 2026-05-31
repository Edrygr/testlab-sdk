package app.testlab.sdk

data class TestLabConfig(
    val apiKey: String,
    val appId: String,
    val syncInterval: Long = 60_000L,
    val debug: Boolean = false,
    val baseUrl: String = "https://api.testlab.app"
)
