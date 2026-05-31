package app.testlab.sdk.models

data class TesterEvent(
    val sessionId: String,
    val testerId: String?,
    val name: String,
    val properties: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)
