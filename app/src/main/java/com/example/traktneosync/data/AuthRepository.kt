package com.example.traktneosync.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.traktneosync.data.tmdb.TmdbApiKeyProvider
import com.example.traktneosync.data.tmdb.TmdbLanguageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val tmdbKeyProvider: TmdbApiKeyProvider,
    private val tmdbLanguageProvider: TmdbLanguageProvider,
) {
    private object Keys {
        val TRAKT_ACCESS_TOKEN = stringPreferencesKey("trakt_access_token")
        val TRAKT_REFRESH_TOKEN = stringPreferencesKey("trakt_refresh_token")
        val TRAKT_USER = stringPreferencesKey("trakt_user")
        
        val NEODB_ACCESS_TOKEN = stringPreferencesKey("neodb_access_token")
        val NEODB_INSTANCE = stringPreferencesKey("neodb_instance")
        val NEODB_USER = stringPreferencesKey("neodb_user")
        
        // NeoDB 动态注册的应用凭证
        val NEODB_APP_CLIENT_ID = stringPreferencesKey("neodb_app_client_id")
        val NEODB_APP_CLIENT_SECRET = stringPreferencesKey("neodb_app_client_secret")

        // TMDB API Key
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")

        // 首选显示语言（影响TMDB请求language参数）
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")

        // 深色模式偏好
        val DARK_THEME = stringPreferencesKey("dark_theme")
    }
    
    // ========== Trakt Auth ==========
    
    val traktAccessToken: Flow<String?> = dataStore.data.map { it[Keys.TRAKT_ACCESS_TOKEN] }
    val traktRefreshToken: Flow<String?> = dataStore.data.map { it[Keys.TRAKT_REFRESH_TOKEN] }
    val traktUser: Flow<String?> = dataStore.data.map { it[Keys.TRAKT_USER] }
    
    suspend fun setTraktAuth(accessToken: String, refreshToken: String, user: String) {
        dataStore.edit { prefs ->
            prefs[Keys.TRAKT_ACCESS_TOKEN] = accessToken
            prefs[Keys.TRAKT_REFRESH_TOKEN] = refreshToken
            prefs[Keys.TRAKT_USER] = user
        }
    }
    
    suspend fun clearTraktAuth() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.TRAKT_ACCESS_TOKEN)
            prefs.remove(Keys.TRAKT_REFRESH_TOKEN)
            prefs.remove(Keys.TRAKT_USER)
        }
    }
    
    // ========== NeoDB Auth ==========
    
    val neodbAccessToken: Flow<String?> = dataStore.data.map { it[Keys.NEODB_ACCESS_TOKEN] }
    val neodbInstance: Flow<String?> = dataStore.data.map { it[Keys.NEODB_INSTANCE] }
    val neodbUser: Flow<String?> = dataStore.data.map { it[Keys.NEODB_USER] }
    
    suspend fun setNeoDBAuth(accessToken: String, instance: String, user: String) {
        dataStore.edit { prefs ->
            prefs[Keys.NEODB_ACCESS_TOKEN] = accessToken
            prefs[Keys.NEODB_INSTANCE] = instance
            prefs[Keys.NEODB_USER] = user
        }
    }
    
    suspend fun clearNeoDBAuth() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.NEODB_ACCESS_TOKEN)
            prefs.remove(Keys.NEODB_INSTANCE)
            prefs.remove(Keys.NEODB_USER)
            prefs.remove(Keys.NEODB_APP_CLIENT_ID)
            prefs.remove(Keys.NEODB_APP_CLIENT_SECRET)
        }
    }
    
    // ========== NeoDB App 凭证（动态注册） ==========
    
    suspend fun saveNeoDBAppCredentials(clientId: String, clientSecret: String) {
        dataStore.edit { prefs ->
            prefs[Keys.NEODB_APP_CLIENT_ID] = clientId
            prefs[Keys.NEODB_APP_CLIENT_SECRET] = clientSecret
        }
    }
    
    suspend fun getNeoDBAppCredentials(): Pair<String, String>? {
        val prefs = dataStore.data.first()
        val clientId = prefs[Keys.NEODB_APP_CLIENT_ID] ?: return null
        val clientSecret = prefs[Keys.NEODB_APP_CLIENT_SECRET] ?: return null
        return Pair(clientId, clientSecret)
    }

    // ========== TMDB API Key ==========

    val tmdbApiKey: Flow<String?> = dataStore.data.map { it[Keys.TMDB_API_KEY] }

    suspend fun initTmdbKey() {
        val key = dataStore.data.first()[Keys.TMDB_API_KEY] ?: ""
        tmdbKeyProvider.apiKey = key
    }

    suspend fun setTmdbApiKey(key: String) {
        val trimmed = key.trim()
        dataStore.edit { prefs ->
            if (trimmed.isBlank()) {
                prefs.remove(Keys.TMDB_API_KEY)
            } else {
                prefs[Keys.TMDB_API_KEY] = trimmed
            }
        }
        tmdbKeyProvider.apiKey = trimmed
    }

    // ========== 首选显示语言 ==========

    val preferredLanguage: Flow<String?> = dataStore.data.map { it[Keys.PREFERRED_LANGUAGE] }

    suspend fun setPreferredLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[Keys.PREFERRED_LANGUAGE] = language
        }
        tmdbLanguageProvider.language = language
    }

    suspend fun initLanguage() {
        val lang = dataStore.data.first()[Keys.PREFERRED_LANGUAGE] ?: "zh-CN"
        tmdbLanguageProvider.language = lang
    }

    // ========== 深色模式 ==========

    val darkTheme: Flow<String?> = dataStore.data.map { it[Keys.DARK_THEME] }

    suspend fun setDarkTheme(mode: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = mode
        }
    }
}
