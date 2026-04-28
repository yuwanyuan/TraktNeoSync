# Add project specific ProGuard rules here.

# ========== Common Attributes ==========
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes *Annotation*

# ========== Retrofit ==========
-keep class retrofit2.** { *; }
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit ServiceMethod and HttpServiceMethod for reflection
-keep class retrofit2.ServiceMethod { *; }
-keep class retrofit2.HttpServiceMethod { *; }
-keep class retrofit2.HttpServiceMethod$* { *; }
-keep class retrofit2.Retrofit { *; }
-keep class retrofit2.Retrofit$1 { *; }
-keep class retrofit2.RequestFactory { *; }
-keep class retrofit2.RequestFactory$* { *; }
-keep class retrofit2.ParameterHandler { *; }
-keep class retrofit2.ParameterHandler$* { *; }
-keep class retrofit2.CallAdapter { *; }
-keep class retrofit2.CallAdapter$* { *; }
-keep class retrofit2.Converter { *; }
-keep class retrofit2.Converter$* { *; }
-keep class retrofit2.Utils { *; }

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
-keep class sun.misc.Unsafe { *; }
-dontwarn java.lang.invoke.StringConcatFactory
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# ========== Kotlin ==========
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ========== Kotlin Coroutines ==========
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Kotlin Continuation for suspend functions
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.CoroutineContext { *; }
-keep class kotlin.coroutines.EmptyCoroutineContext { *; }
-keep class kotlin.coroutines.intrinsics.CoroutineSingletons { *; }

# ========== Hilt / Dagger ==========
-keepclassmembers @dagger.hilt.android.HiltAndroidApp class * { *; }
-keepclassmembers @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ========== Retrofit API interfaces (CRITICAL: must keep full method signatures) ==========
-keep interface com.example.traktneosync.data.trakt.TraktApiService { *; }
-keep interface com.example.traktneosync.data.neodb.NeoDBApiService { *; }
-keep interface com.example.traktneosync.data.tmdb.TmdbApiService { *; }

# Keep all suspend function signatures and generic return types
-keepclassmembers interface com.example.traktneosync.data.trakt.TraktApiService { *; }
-keepclassmembers interface com.example.traktneosync.data.neodb.NeoDBApiService { *; }
-keepclassmembers interface com.example.traktneosync.data.tmdb.TmdbApiService { *; }

# ========== App data classes - NeoDB ==========
-keep class com.example.traktneosync.data.neodb.** { *; }

# ========== App data classes - TMDB ==========
-keep class com.example.traktneosync.data.tmdb.** { *; }

# ========== App data classes - Trakt ==========
-keep class com.example.traktneosync.data.trakt.** { *; }

# ========== App ViewModels & UI state ==========
-keep class com.example.traktneosync.ui.** { *; }

# ========== App data repositories ==========
-keep class com.example.traktneosync.data.SyncRepository { *; }
-keep class com.example.traktneosync.data.SyncRepository$* { *; }
-keep class com.example.traktneosync.data.AuthRepository { *; }
-keep class com.example.traktneosync.data.AuthRepository$* { *; }

# ========== Compose ==========
-keepclassmembers class androidx.compose.** { *; }
