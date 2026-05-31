package app.testlab.sdk.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String,
    val testerId: String?,
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val launchedFromTestLab: Boolean = false,
    val dayNumber: Int = 1,
    val synced: Boolean = false
)
