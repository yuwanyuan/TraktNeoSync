package com.example.traktneosync.data.proxy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USERNAME = stringPreferencesKey("proxy_username")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_password")
    }

    val proxyConfig: Flow<ProxyConfig> = dataStore.data.map { prefs ->
        ProxyConfig(
            type = try {
                ProxyType.valueOf(prefs[Keys.PROXY_TYPE] ?: "NONE")
            } catch (_: Exception) {
                ProxyType.NONE
            },
            host = prefs[Keys.PROXY_HOST] ?: "",
            port = prefs[Keys.PROXY_PORT] ?: 0,
            username = prefs[Keys.PROXY_USERNAME] ?: "",
            password = prefs[Keys.PROXY_PASSWORD] ?: ""
        )
    }

    suspend fun saveProxyConfig(config: ProxyConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.PROXY_TYPE] = config.type.name
            prefs[Keys.PROXY_HOST] = config.host
            prefs[Keys.PROXY_PORT] = config.port
            prefs[Keys.PROXY_USERNAME] = config.username
            prefs[Keys.PROXY_PASSWORD] = config.password
        }
    }

    suspend fun clearProxyConfig() {
        dataStore.edit { prefs ->
            prefs[Keys.PROXY_TYPE] = ProxyType.NONE.name
            prefs[Keys.PROXY_HOST] = ""
            prefs[Keys.PROXY_PORT] = 0
            prefs[Keys.PROXY_USERNAME] = ""
            prefs[Keys.PROXY_PASSWORD] = ""
        }
    }
}
