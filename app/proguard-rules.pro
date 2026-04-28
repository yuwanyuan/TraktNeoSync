# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ========== Retrofit ==========
-keepattributes Signature
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**

# ========== OkHttp ==========
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ========== Gson ==========
-keepattributes Signature
-keepattributes *Annotation*

-keep class sun.misc.Unsafe { *; }
-dontwarn java.lang.invoke.StringConcatFactory

-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ========== Kotlin Coroutines ==========
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile **;
}

# ========== Hilt / Dagger ==========
-keepclassmembers @dagger.hilt.android.HiltAndroidApp class * { *; }
-keepclassmembers @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ========== App data classes (Gson serialization) ==========
-keep class com.example.traktneosync.data.** { <fields>; <init>(...); }
-keep class com.example.traktneosync.ui.sync.SyncListItem { <fields>; }
-keep class com.example.traktneosync.ui.sync.SyncUiState { <fields>; }
-keep class com.example.traktneosync.ui.sync.SyncProgress { <fields>; }
-keep class com.example.traktneosync.ui.movies.MovieItem { <fields>; }
-keep class com.example.traktneosync.ui.shows.ShowItem { <fields>; }
-keep class com.example.traktneosync.ui.search.SearchUiState { <fields>; }
-keep class com.example.traktneosync.ui.auth.AuthUiState { <fields>; }
-keep class com.example.traktneosync.data.SyncRepository$* { <fields>; }

# ========== Retrofit API interfaces ==========
-keep,allowobfuscation interface com.example.traktneosync.data.trakt.TraktApiService
-keep,allowobfuscation interface com.example.traktneosync.data.neodb.NeoDBApiService
-keep,allowobfuscation interface com.example.traktneosync.data.tmdb.TmdbApiService

# ========== Compose ==========
-keepclassmembers class androidx.compose.** { *; }
