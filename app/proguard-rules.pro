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

# ========== App data classes - Trakt ==========
-keep class com.example.traktneosync.data.trakt.TraktTokenResponse { *; }
-keep class com.example.traktneosync.data.trakt.TraktTokenRequest { *; }
-keep class com.example.traktneosync.data.trakt.TraktRefreshTokenRequest { *; }
-keep class com.example.traktneosync.data.trakt.TraktDeviceCodeRequest { *; }
-keep class com.example.traktneosync.data.trakt.TraktDeviceCodeResponse { *; }
-keep class com.example.traktneosync.data.trakt.TraktWatchedItem { *; }
-keep class com.example.traktneosync.data.trakt.TraktMovie { *; }
-keep class com.example.traktneosync.data.trakt.TraktShow { *; }
-keep class com.example.traktneosync.data.trakt.TraktIds { *; }
-keep class com.example.traktneosync.data.trakt.TraktWatchlistItem { *; }
-keep class com.example.traktneosync.data.trakt.TraktHistoryItem { *; }
-keep class com.example.traktneosync.data.trakt.TraktEpisode { *; }
-keep class com.example.traktneosync.data.trakt.TraktPlaybackItem { *; }
-keep class com.example.traktneosync.data.trakt.TraktUser { *; }
-keep class com.example.traktneosync.data.trakt.TraktUserIds { *; }
-keep class com.example.traktneosync.data.trakt.TraktSyncRequest { *; }
-keep class com.example.traktneosync.data.trakt.TraktSyncMovie { *; }
-keep class com.example.traktneosync.data.trakt.TraktSyncShow { *; }
-keep class com.example.traktneosync.data.trakt.TraktSyncSeason { *; }
-keep class com.example.traktneosync.data.trakt.TraktSyncEpisode { *; }
-keep class com.example.traktneosync.data.trakt.TraktSyncResponse { *; }
-keep class com.example.traktneosync.data.trakt.TraktSyncCounts { *; }
-keep class com.example.traktneosync.data.trakt.TraktSyncNotFound { *; }

# ========== App data classes - NeoDB ==========
-keep class com.example.traktneosync.data.neodb.NeoDBAppRegistrationRequest { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBAppRegistrationResponse { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBTokenRequest { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBRefreshTokenRequest { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBTokenResponse { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBUser { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBEntry { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBExternalResource { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBMark { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBPagedMarks { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBMarkRequest { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBSearchResult { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBInstance { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBPublicInstance { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBPaginatedPosts { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBPost { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBAccount { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBExtNeoDB { *; }
-keep class com.example.traktneosync.data.neodb.NeoDBRelatedItem { *; }

# ========== App data classes - TMDB ==========
-keep class com.example.traktneosync.data.tmdb.TmdbMovieDetail { *; }
-keep class com.example.traktneosync.data.tmdb.TmdbTvDetail { *; }
-keep class com.example.traktneosync.data.tmdb.TmdbImagesResponse { *; }
-keep class com.example.traktneosync.data.tmdb.TmdbImageItem { *; }
-keep class com.example.traktneosync.data.tmdb.TmdbAlternativeTitles { *; }
-keep class com.example.traktneosync.data.tmdb.TmdbAltTitle { *; }
-keep class com.example.traktneosync.data.tmdb.TmdbSearchResponse { *; }
-keep class com.example.traktneosync.data.tmdb.TmdbSearchResult { *; }
-keep class com.example.traktneosync.data.tmdb.TmdbExternalIds { *; }

# ========== UI model classes ==========
-keep class com.example.traktneosync.ui.sync.SyncListItem { <fields>; }
-keep class com.example.traktneosync.ui.sync.SyncUiState { <fields>; }
-keep class com.example.traktneosync.ui.sync.SyncProgress { <fields>; }
-keep class com.example.traktneosync.ui.movies.MovieItem { <fields>; }
-keep class com.example.traktneosync.ui.shows.ShowItem { <fields>; }
-keep class com.example.traktneosync.ui.search.SearchUiState { <fields>; }
-keep class com.example.traktneosync.ui.auth.AuthUiState { <fields>; }
-keep class com.example.traktneosync.data.SyncRepository$* { <fields>; }

# ========== Compose ==========
-keepclassmembers class androidx.compose.** { *; }
