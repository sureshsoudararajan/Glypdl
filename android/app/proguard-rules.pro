# Add project specific ProGuard rules here.

# YoutubeDL-android
-keep class com.yausername.youtubedl_android.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep,allowobfuscation,allowshrinking interface dagger.hilt.internal.GeneratedEntryPoint
-keep,allowobfuscation,allowshrinking @interface dagger.hilt.internal.Component**

# Standard Android
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
