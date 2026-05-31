# Keep SDK public API
-keep class app.testlab.sdk.TestLabSDK { *; }
-keep class app.testlab.sdk.TestLabConfig { *; }

# Keep models for Gson serialization
-keep class app.testlab.sdk.models.** { *; }
-keepclassmembers class app.testlab.sdk.models.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
