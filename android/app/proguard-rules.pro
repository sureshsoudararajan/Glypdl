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



# ---- Added when R8 minification was enabled ----

# Keep readable stack traces (upload mapping.txt from CI to de-obfuscate crash reports)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Jackson (used reflectively by youtubedl-android to parse yt-dlp JSON)
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <init>(...);
}

# Optional/JVM-only classes referenced by libraries but absent on Android
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**

# Enums are looked up by name (Enum.valueOf) from stored strings
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Android components declared only in the manifest (tiles, services, receivers, providers)
-keep class * extends android.service.quicksettings.TileService
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
