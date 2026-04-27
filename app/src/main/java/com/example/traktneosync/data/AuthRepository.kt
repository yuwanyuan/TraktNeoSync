package com.example.traktneosync.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val TRAKT_ACCESS_TOKEN = stringPreferencesKey("trakt_access_token")
        val TRAKT_REFRESH_TOKEN = stringPreferencesKey("trakt_refresh_token")
        val TRAKT_USER = stringPreferencesKey("trakt_user")
        
        val NEODB_ACCESS_TOKEN = stringPreferencesKey("neodb_access_token")
        val NEODB_INSTANCE = stringPreferencesKey("neodb_instance")
        val NEODB_USER = stringPreferencesKey("neodb_user")
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
        }
    }
    
    suspend fun isLoggedIn(): Boolean {
        return dataStore.data.map { 
            it[Keys.TRAKT_ACCESS_TOKEN] != null && it[Keys.NEODB_ACCESS_TOKEN] != null 
        }.first()
    }
}
