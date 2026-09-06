# Add project specific ProGuard rules here.

# YoutubeDL-android & FFmpeg
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }
-dontwarn com.yausername.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.io.**

# Glypdl Models & Entities
-keep class com.glypdl.android.data.model.** { *; }
-keep class com.glypdl.android.data.local.entity.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.glypdl.android.data.local.** { *; }
-dontwarn androidx.room.paging.**

# Hilt & Dependency Injection
-keep class * extends android.app.Application
-keep class * extends androidx.activity.ComponentActivity
-keep class * extends androidx.lifecycle.ViewModel
-keep class dagger.hilt.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedEntryPoint { *; }
-keep interface dagger.hilt.internal.GeneratedEntryPoint { *; }
-keep class com.glypdl.android.**_HiltModules* { *; }
-keep class com.glypdl.android.di.** { *; }
-dontwarn dagger.hilt.**

# Standard Android & Kotlin Coroutines / Reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}


