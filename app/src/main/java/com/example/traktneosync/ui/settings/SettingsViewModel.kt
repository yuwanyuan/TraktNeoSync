package com.example.traktneosync.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.data.cache.CacheDao
import com.example.traktneosync.data.neodb.NeoDBOAuthManager
import com.example.traktneosync.data.proxy.ProxyConfig
import com.example.traktneosync.data.proxy.ProxyProvider
import com.example.traktneosync.data.proxy.ProxyRepository
import com.example.traktneosync.data.proxy.ProxyType
import com.example.traktneosync.data.tmdb.TmdbApiKeyProvider
import com.example.traktneosync.data.tmdb.TmdbApiService
import com.example.traktneosync.data.trakt.TraktOAuthManager
import com.example.traktneosync.util.AppLogger
import com.example.traktneosync.util.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val traktOAuthManager: TraktOAuthManager,
    private val neodbOAuthManager: NeoDBOAuthManager,
    private val authRepository: AuthRepository,
    private val tmdbApi: TmdbApiService,
    private val tmdbKeyProvider: TmdbApiKeyProvider,
    private val cacheDao: CacheDao,
    private val proxyRepository: ProxyRepository,
    private val proxyProvider: ProxyProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
        const val GITHUB_REPO_URL = "https://github.com/yuwanyuan/TraktNeoSync"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            refreshCacheSize()

            combine(
                authRepository.traktAccessToken,
                authRepository.traktUser,
                authRepository.neodbAccessToken,
                authRepository.neodbUser,
                authRepository.tmdbApiKey,
                authRepository.preferredLanguage,
                authRepository.darkTheme,
                authRepository.neodbManualInstance,
                proxyRepository.proxyConfig
            ) { values: Array<Any?> ->
                val traktToken = values[0] as? String
                val traktUser = values[1] as? String
                val neodbToken = values[2] as? String
                val neodbUser = values[3] as? String
                val tmdbKey = values[4] as? String
                val lang = values[5] as? String
                val dark = values[6] as? String
                val neodbInstance = values[7] as? String
                val proxyCfg = values[8] as? ProxyConfig ?: ProxyConfig()
                _uiState.value.copy(
                    traktConnected = traktToken != null,
                    traktUsername = traktUser,
                    neodbConnected = neodbToken != null,
                    neodbUsername = neodbUser,
                    tmdbApiKey = tmdbKey ?: "",
                    preferredLanguage = lang ?: "zh-CN",
                    darkThemeMode = dark ?: "system",
                    neodbInstance = neodbInstance ?: "",
                    proxyConfig = proxyCfg
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = calculateCacheSize()
            _uiState.value = _uiState.value.copy(cacheSize = size)
        }
    }

    private fun calculateCacheSize(): String {
        return try {
            val dbFile = context.getDatabasePath("traktneosync_cache.db")
            val walFile = File(dbFile.parent, "${dbFile.name}-wal")
            val shmFile = File(dbFile.parent, "${dbFile.name}-shm")
            val logFile = File(context.filesDir, "app_crash_log.txt")

            var totalBytes = 0L
            listOf(dbFile, walFile, shmFile, logFile).forEach { f ->
                if (f.exists()) totalBytes += f.length()
            }
            formatBytes(totalBytes)
        } catch (e: Exception) {
            "0 B"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun connectTrakt() {
        traktOAuthManager.openAuthorizationInBrowser()
    }

    fun disconnectTrakt() {
        viewModelScope.launch {
            traktOAuthManager.logout()
        }
    }

    fun connectNeoDB() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(neodbLoading = true, neodbError = null)
            val registered = try {
                neodbOAuthManager.registerApp()
            } catch (e: Exception) {
                AppLogger.error(TAG, "NeoDB注册异常", e)
                false
            }
            if (!registered) {
                AppLogger.error(TAG, "注册NeoDB应用失败，无法打开授权页面")
                _uiState.value = _uiState.value.copy(
                    neodbLoading = false,
                    neodbError = "无法连接 NeoDB 服务器，请检查网络或稍后重试"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(neodbLoading = false)
            try {
                neodbOAuthManager.openAuthorizationInBrowser()
            } catch (e: Exception) {
                AppLogger.error(TAG, "打开NeoDB浏览器授权失败", e)
                _uiState.value = _uiState.value.copy(
                    neodbError = "无法打开浏览器: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    fun disconnectNeoDB() {
        viewModelScope.launch {
            neodbOAuthManager.logout()
        }
    }

    fun clearNeoDBError() {
        _uiState.value = _uiState.value.copy(neodbError = null)
    }

    fun saveNeoDBCredentials(clientId: String, clientSecret: String, instance: String) {
        viewModelScope.launch {
            val trimmedId = clientId.trim()
            val trimmedSecret = clientSecret.trim()
            val trimmedInstance = instance.trim()
            if (trimmedId.isNotBlank() && trimmedSecret.isNotBlank()) {
                authRepository.saveNeoDBAppCredentials(trimmedId, trimmedSecret)
            }
            if (trimmedInstance.isNotBlank()) {
                authRepository.setNeoDBManualInstance(trimmedInstance)
            } else {
                authRepository.setNeoDBManualInstance("")
            }
            AppLogger.info(TAG, "手动保存NeoDB凭证", mapOf("clientIdPrefix" to trimmedId.take(8), "instance" to trimmedInstance))
        }
    }

    fun saveTmdbApiKey(key: String) {
        viewModelScope.launch {
            authRepository.setTmdbApiKey(key)
        }
    }

    fun testTmdbApiKey(key: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tmdbKeyTesting = true, tmdbKeyValid = null)
            val trimmed = key.trim()
            if (trimmed.isEmpty()) {
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = false)
                return@launch
            }
            val originalKey = tmdbKeyProvider.apiKey
            tmdbKeyProvider.apiKey = trimmed
            try {
                tmdbApi.getMovieDetail(550)
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = true)
            } catch (e: Exception) {
                AppLogger.error(TAG, "TMDB Key测试失败", e)
                _uiState.value = _uiState.value.copy(tmdbKeyTesting = false, tmdbKeyValid = false)
            } finally {
                if (originalKey != trimmed) {
                    tmdbKeyProvider.apiKey = originalKey
                }
            }
        }
    }

    fun setPreferredLanguage(language: String) {
        viewModelScope.launch {
            authRepository.setPreferredLanguage(language)
            AppLogger.info(TAG, "首选语言切换为 $language")
        }
    }

    fun setDarkTheme(mode: String) {
        viewModelScope.launch {
            authRepository.setDarkTheme(mode)
            AppLogger.info(TAG, "深色模式切换为 $mode")
        }
    }

    fun setLogLevel(level: LogLevel) {
        AppLogger.setLogLevel(level)
        AppLogger.info(TAG, "日志级别切换为 ${level.name}")
    }

    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                cacheDao.clearAllCache()
                refreshCacheSize()
                AppLogger.info(TAG, "用户手动清理了全部缓存")
                onDone()
            } catch (e: Exception) {
                AppLogger.error(TAG, "清理缓存失败", e)
            }
        }
    }

    fun saveProxyConfig(config: ProxyConfig) {
        viewModelScope.launch {
            proxyRepository.saveProxyConfig(config)
            proxyProvider.config = config
            AppLogger.info(TAG, "代理配置已保存", mapOf("type" to config.type.name, "host" to config.host, "port" to config.port.toString()))
        }
    }

    fun testProxy(config: ProxyConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(proxyTesting = true, proxyTestResult = null)
            if (!config.isEnabled) {
                _uiState.value = _uiState.value.copy(
                    proxyTesting = false,
                    proxyTestResult = ProxyTestResult(success = false, message = "代理未启用或配置不完整")
                )
                return@launch
            }

            try {
                val proxy = Proxy(
                    when (config.type) {
                        ProxyType.HTTP -> Proxy.Type.HTTP
                        ProxyType.SOCKS5 -> Proxy.Type.SOCKS
                        ProxyType.NONE -> throw IllegalArgumentException("代理类型未设置")
                    },
                    InetSocketAddress.createUnresolved(config.host, config.port)
                )

                val clientBuilder = OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)

                if (config.username.isNotBlank()) {
                    val credential = Credentials.basic(config.username, config.password)
                    clientBuilder.addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                        chain.proceed(request)
                    }
                }

                val client = clientBuilder.build()
                val request = Request.Builder()
                    .url("https://www.google.com/generate_204")
                    .head()
                    .build()

                val response = client.newCall(request).execute()
                val success = response.isSuccessful || response.code == 204
                val message = if (success) {
                    "连接成功 (${response.code})"
                } else {
                    "连接失败 (HTTP ${response.code})"
                }

                _uiState.value = _uiState.value.copy(
                    proxyTesting = false,
                    proxyTestResult = ProxyTestResult(success = success, message = message)
                )
                AppLogger.info(TAG, "代理测试完成", mapOf("success" to success, "message" to message))
            } catch (e: Exception) {
                val message = when (e) {
                    is java.net.ConnectException -> "连接被拒绝，请检查地址和端口"
                    is java.net.SocketTimeoutException -> "连接超时，请检查代理是否可用"
                    is java.net.UnknownHostException -> "无法解析主机名"
                    is java.io.IOException -> "认证失败或网络错误: ${e.message}"
                    else -> "连接失败: ${e.message ?: e.javaClass.simpleName}"
                }
                _uiState.value = _uiState.value.copy(
                    proxyTesting = false,
                    proxyTestResult = ProxyTestResult(success = false, message = message)
                )
                AppLogger.warn(TAG, "代理测试失败", e)
            }
        }
    }

    fun clearProxyTestResult() {
        _uiState.value = _uiState.value.copy(proxyTestResult = null)
    }
}

data class SettingsUiState(
    val traktConnected: Boolean = false,
    val traktUsername: String? = null,
    val neodbConnected: Boolean = false,
    val neodbUsername: String? = null,
    val neodbLoading: Boolean = false,
    val neodbError: String? = null,
    val neodbInstance: String = "",
    val tmdbApiKey: String = "",
    val tmdbKeyTesting: Boolean = false,
    val tmdbKeyValid: Boolean? = null,
    val preferredLanguage: String = "zh-CN",
    val darkThemeMode: String = "system",
    val cacheSize: String = "0 B",
    val proxyConfig: ProxyConfig = ProxyConfig(),
    val proxyTesting: Boolean = false,
    val proxyTestResult: ProxyTestResult? = null
)

data class ProxyTestResult(
    val success: Boolean,
    val message: String
)
