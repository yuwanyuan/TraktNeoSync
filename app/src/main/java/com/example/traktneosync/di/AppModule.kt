package com.example.traktneosync.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.traktneosync.BuildConfig
import com.example.traktneosync.data.cache.AppDatabase
import com.example.traktneosync.data.cache.CacheDao
import com.example.traktneosync.data.neodb.NeoDBApiService
import com.example.traktneosync.data.neodb.NeoDBBaseUrlProvider
import com.example.traktneosync.data.tmdb.TmdbApiKeyProvider
import com.example.traktneosync.data.tmdb.TmdbApiService
import com.example.traktneosync.data.tmdb.TmdbLanguageProvider
import com.example.traktneosync.data.trakt.TraktApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
    
    @Provides
    @Singleton
    @TraktHttpClient
    fun provideTraktHttpClient(langProvider: TmdbLanguageProvider): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(Interceptor { chain ->
                val lang = langProvider.language.takeIf { it.isNotEmpty() } ?: "zh-CN"
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("trakt-api-version", "2")
                    .addHeader("trakt-api-key", BuildConfig.TRAKT_CLIENT_ID)
                    .addHeader("Accept-Language", lang.take(2))
                    .build()
                chain.proceed(request)
            })
            .build()
    }
    
    @Provides
    @Singleton
    @NeoDBHttpClient
    fun provideNeoDBHttpClient(baseUrlProvider: NeoDBBaseUrlProvider): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val defaultBase = "https://neodb.social/"

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val url = original.url.toString()
                // Dynamic base URL rewrite for custom NeoDB instances
                val newUrl = if (url.startsWith(defaultBase)) {
                    url.replaceFirst(defaultBase, baseUrlProvider.baseUrl.let {
                        if (!it.endsWith("/")) "$it/" else it
                    })
                } else {
                    url
                }
                val request = original.newBuilder()
                    .url(newUrl)
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            })
            .build()
    }
    
    @Provides
    @Singleton
    fun provideTraktApiService(@TraktHttpClient client: OkHttpClient): TraktApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.trakt.tv/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TraktApiService::class.java)
    }
    
    @Provides
    @Singleton
    @NeoDBRetrofit
    fun provideNeoDBRetrofit(@NeoDBHttpClient client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://neodb.social/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideNeoDBApiService(@NeoDBRetrofit retrofit: Retrofit): NeoDBApiService {
        return retrofit.create(NeoDBApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTmdbLanguageProvider(): TmdbLanguageProvider {
        return TmdbLanguageProvider()
    }

    @Provides
    @Singleton
    fun provideTmdbHttpClient(
        keyProvider: TmdbApiKeyProvider,
        langProvider: TmdbLanguageProvider
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(Interceptor { chain ->
                val apiKey = keyProvider.apiKey.takeIf { it.isNotEmpty() } ?: ""
                val language = langProvider.language.takeIf { it.isNotEmpty() } ?: "zh-CN"
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("api_key", apiKey)
                    .addQueryParameter("language", language)
                    .build()
                val request = original.newBuilder().url(url).build()
                chain.proceed(request)
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(client: OkHttpClient): TmdbApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "traktneosync_cache.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideCacheDao(database: AppDatabase): CacheDao {
        return database.cacheDao()
    }
}

@Qualifier
annotation class TraktHttpClient

@Qualifier
annotation class NeoDBHttpClient

@Qualifier
annotation class NeoDBRetrofit
