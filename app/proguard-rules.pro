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

# Keep Hilt - 必须保留所有注入入口点
-keepclassmembers @dagger.hilt.android.HiltAndroidApp class * { *; }
-keepclassmembers @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep all app data classes (Gson 需要空参构造函数)
-keep class com.example.traktneosync.data.** { <fields>; <init>(...); }
-keep class com.example.traktneosync.ui.sync.SyncListItem { <fields>; }
-keep class com.example.traktneosync.ui.sync.SyncUiState { <fields>; }
-keep class com.example.traktneosync.ui.sync.SyncProgress { <fields>; }
-keep class com.example.traktneosync.ui.movies.MovieItem { <fields>; }
-keep class com.example.traktneosync.ui.shows.ShowItem { <fields>; }
-keep class com.example.traktneosync.ui.search.SearchUiState { <fields>; }
-keep class com.example.traktneosync.ui.auth.AuthUiState { <fields>; }
-keep class com.example.traktneosync.data.SyncRepository$* { <fields>; }

# Keep Compose
-keepclassmembers class androidx.compose.** { *; }
