# Add project specific ProGuard rules here.

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---- Gson ----
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Keep Gson type adapter classes
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---- Coil ----
-dontwarn coil.**

# ---- Koin ----
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module
-keepclassmembers class * {
    org.koin.core.instance.Factory *;
}

# ---- AndroidX Paging ----
-keep class androidx.paging.** { *; }

# ---- AndroidX Security / Tink ----
-dontwarn com.google.errorprone.annotations.**

# ---- General Android ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep data classes used for Gson JSON parsing (field names must match JSON keys)
-keep class com.duty.weibotoy.data.** { *; }
