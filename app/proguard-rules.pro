# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep data classes used in API
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Retrofit models
-keepattributes Signature
-keepattributes Exceptions

# Keep Hilt
-keepclassmembers @dagger.hilt.android.HiltAndroidApp class * extends android.app.Application

# Keep Compose
-keepclassmembers class androidx.compose.** { *; }
