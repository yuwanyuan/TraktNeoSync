package com.example.traktneosync.data.neodb

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.traktneosync.BuildConfig
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeoDBOAuthManager @Inject constructor(
    private val neoDBApi: NeoDBApiService,
    private val authRepository: AuthRepository,
    private val baseUrlProvider: NeoDBBaseUrlProvider,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NeoDBOAuthManager"
        const val REDIRECT_URI = "traktneosync://neodb"
        const val DEFAULT_INSTANCE = "neodb.social"
    }
    
    var currentInstance: String = DEFAULT_INSTANCE
    var currentClientId: String = ""
    var currentClientSecret: String = ""
    
    // ========== 注册应用 ==========
    
    suspend fun registerApp(instance: String = DEFAULT_INSTANCE): Boolean = withContext(Dispatchers.IO) {
        // 优先使用手动设置的实例
        val manualInstance = authRepository.getNeoDBManualInstance()
        val effectiveInstance = if (!manualInstance.isNullOrBlank()) {
            manualInstance.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        } else {
            instance
        }

        currentInstance = effectiveInstance
        baseUrlProvider.baseUrl = "https://$effectiveInstance/"

        // 优先使用 BuildConfig 预设的凭证
        if (BuildConfig.NEODB_CLIENT_ID.isNotEmpty()) {
            currentClientId = BuildConfig.NEODB_CLIENT_ID
            currentClientSecret = BuildConfig.NEODB_CLIENT_SECRET
            AppLogger.info(TAG, "使用BuildConfig预设NeoDB凭证", mapOf("clientIdPrefix" to currentClientId.take(8), "instance" to effectiveInstance))
            return@withContext true
        }

        // 尝试从持久化存储读取
        val saved = authRepository.getNeoDBAppCredentials()
        if (saved != null) {
            currentClientId = saved.first
            currentClientSecret = saved.second
            AppLogger.info(TAG, "使用已保存的NeoDB凭证", mapOf("clientIdPrefix" to currentClientId.take(8), "instance" to effectiveInstance))
            return@withContext true
        }

        // 动态注册新应用（带重试）
        AppLogger.info(TAG, "无预设凭证，尝试动态注册NeoDB应用", mapOf("instance" to effectiveInstance))
        var lastException: Exception? = null
        repeat(2) { attempt ->
            try {
                AppLogger.info(TAG, "开始注册NeoDB应用", mapOf("instance" to effectiveInstance, "attempt" to (attempt + 1), "url" to "https://$effectiveInstance/api/v1/apps"))
                val response = neoDBApi.registerApp(
                    clientName = "TraktNeoSync",
                    redirectUris = REDIRECT_URI
                )
                currentClientId = response.clientId
                currentClientSecret = response.clientSecret

                if (currentClientId.isEmpty() || currentClientSecret.isEmpty()) {
                    AppLogger.error(TAG, "NeoDB注册返回空凭证", null, mapOf("clientIdEmpty" to currentClientId.isEmpty(), "clientSecretEmpty" to currentClientSecret.isEmpty()))
                    return@withContext false
                }

                // 持久化保存
                authRepository.saveNeoDBAppCredentials(currentClientId, currentClientSecret)
                AppLogger.info(TAG, "NeoDB应用注册成功", mapOf("instance" to effectiveInstance, "clientIdPrefix" to currentClientId.take(8)))

                return@withContext true
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: ""
                AppLogger.error(TAG, "注册NeoDB应用HTTP失败", e, mapOf(
                    "instance" to effectiveInstance,
                    "attempt" to (attempt + 1),
                    "code" to e.code(),
                    "errorBody" to errorBody.take(200)
                ))
                lastException = e
                if (attempt == 0) kotlinx.coroutines.delay(1000)
            } catch (e: java.net.UnknownHostException) {
                AppLogger.error(TAG, "NeoDB实例无法解析", e, mapOf("instance" to effectiveInstance, "attempt" to (attempt + 1)))
                lastException = e
                return@withContext false
            } catch (e: java.net.SocketTimeoutException) {
                AppLogger.error(TAG, "NeoDB连接超时", e, mapOf("instance" to effectiveInstance, "attempt" to (attempt + 1)))
                lastException = e
                if (attempt == 0) kotlinx.coroutines.delay(1000)
            } catch (e: Exception) {
                AppLogger.error(TAG, "注册NeoDB应用失败", e, mapOf("instance" to effectiveInstance, "attempt" to (attempt + 1), "type" to e.javaClass.simpleName))
                lastException = e
                if (attempt == 0) kotlinx.coroutines.delay(1000)
            }
        }

        return@withContext false
    }
    
    // ========== OAuth URL ==========
    
    fun getAuthorizationUrl(): String {
        val encodedRedirectUri = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "https://$currentInstance/oauth/authorize?" +
                "response_type=code&" +
                "client_id=$currentClientId&" +
                "redirect_uri=$encodedRedirectUri&" +
                "scope=read+write"
    }
    
    fun openAuthorizationInBrowser() {
        val url = getAuthorizationUrl()
        AppLogger.info(TAG, "打开NeoDB授权页面", mapOf("url" to url, "instance" to currentInstance, "clientIdEmpty" to currentClientId.isEmpty()))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            AppLogger.info(TAG, "NeoDB授权页面已打开")
        } catch (e: Exception) {
            AppLogger.error(TAG, "打开NeoDB授权页面失败", e, mapOf("url" to url))
            throw e
        }
    }
    
    // ========== 处理回调 ==========
    
    suspend fun handleCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code") ?: return@withContext false

        if (currentClientId.isEmpty() || currentClientSecret.isEmpty()) {
            val saved = authRepository.getNeoDBAppCredentials()
            if (saved != null) {
                currentClientId = saved.first
                currentClientSecret = saved.second
            } else if (BuildConfig.NEODB_CLIENT_ID.isNotEmpty()) {
                currentClientId = BuildConfig.NEODB_CLIENT_ID
                currentClientSecret = BuildConfig.NEODB_CLIENT_SECRET
            }
        }

        if (currentClientId.isEmpty() || currentClientSecret.isEmpty()) {
            AppLogger.error(TAG, "NeoDB回调时无应用凭证", null, mapOf("clientIdEmpty" to currentClientId.isEmpty(), "clientSecretEmpty" to currentClientSecret.isEmpty()))
            return@withContext false
        }

        val savedInstance = authRepository.neodbInstance.first()
        if (savedInstance != null && currentInstance != savedInstance) {
            currentInstance = savedInstance
            baseUrlProvider.baseUrl = "https://$savedInstance/"
        }

        return@withContext try {
            AppLogger.debug(TAG, "请求NeoDB Token", mapOf("instance" to currentInstance, "clientIdPrefix" to currentClientId.take(8)))
            val tokenResponse = neoDBApi.exchangeTokenForm(
                clientId = currentClientId,
                clientSecret = currentClientSecret,
                code = code
            )
            AppLogger.info(TAG, "NeoDB Token获取成功", mapOf("instance" to currentInstance, "tokenType" to tokenResponse.tokenType))

            val userProfile = neoDBApi.getUserProfile("Bearer ${tokenResponse.accessToken}")
            AppLogger.info(TAG, "NeoDB用户资料获取成功", mapOf("displayName" to userProfile.displayName))

            authRepository.setNeoDBAuth(
                accessToken = tokenResponse.accessToken,
                instance = currentInstance,
                user = userProfile.displayName,
                refreshToken = tokenResponse.refreshToken,
                expiresIn = 3600L // NeoDB tokens typically expire in 1 hour
            )
            AppLogger.info(TAG, "NeoDB认证信息已保存", mapOf("instance" to currentInstance))

            // 如果 BuildConfig 中没有设置，保存到本地
            if (BuildConfig.NEODB_CLIENT_ID.isEmpty()) {
                saveAppCredentials(currentInstance, currentClientId, currentClientSecret)
            }

            true
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            AppLogger.error(TAG, "NeoDB OAuth HTTP错误", e, mapOf(
                "instance" to currentInstance,
                "code" to e.code(),
                "errorBody" to errorBody.take(200)
            ))
            false
        } catch (e: Exception) {
            AppLogger.error(TAG, "NeoDB OAuth回调换Token失败", e, mapOf(
                "instance" to currentInstance,
                "type" to e.javaClass.simpleName
            ))
            false
        }
    }
    
    // ========== 登出 ==========
    
    suspend fun logout() {
        authRepository.clearNeoDBAuth()
    }
    
    suspend fun isAuthenticated(): Boolean {
        return authRepository.neodbAccessToken.first() != null
    }

    // ========== Token 刷新 ==========

    suspend fun ensureValidToken(): Boolean = withContext(Dispatchers.IO) {
        val accessToken = authRepository.neodbAccessToken.first()
            ?: return@withContext false

        val expiresAt = authRepository.neodbTokenExpiresAt.first()
        if (expiresAt != null && System.currentTimeMillis() < expiresAt - 300_000) {
            restoreInstanceIfNeeded()
            return@withContext true
        }

        if (expiresAt == null) {
            restoreInstanceIfNeeded()
            return@withContext true
        }

        val refreshToken = authRepository.neodbRefreshToken.first()
            ?: return@withContext false

        if (currentClientId.isEmpty()) {
            val saved = authRepository.getNeoDBAppCredentials()
            if (saved != null) {
                currentClientId = saved.first
                currentClientSecret = saved.second
            } else if (BuildConfig.NEODB_CLIENT_ID.isNotEmpty()) {
                currentClientId = BuildConfig.NEODB_CLIENT_ID
                currentClientSecret = BuildConfig.NEODB_CLIENT_SECRET
            } else {
                return@withContext false
            }
        }

        restoreInstanceIfNeeded()

        return@withContext try {
            val response = neoDBApi.refreshTokenForm(
                clientId = currentClientId,
                clientSecret = currentClientSecret,
                refreshToken = refreshToken
            )
            authRepository.updateNeoDBAccessToken(
                accessToken = response.accessToken,
                expiresIn = 3600L
            )
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "NeoDB Token刷新失败，需重新登录", e, mapOf("instance" to currentInstance))
            authRepository.clearNeoDBAuth()
            false
        }
    }

    private suspend fun restoreInstanceIfNeeded() {
        val savedInstance = authRepository.neodbInstance.first()
        if (savedInstance != null && savedInstance.isNotBlank()) {
            currentInstance = savedInstance
            baseUrlProvider.baseUrl = "https://$savedInstance/"
        }
    }

    // ========== 应用凭证管理 ==========

    private suspend fun saveAppCredentials(instance: String, clientId: String, clientSecret: String) {
        currentInstance = instance
        currentClientId = clientId
        currentClientSecret = clientSecret
        authRepository.saveNeoDBAppCredentials(clientId, clientSecret)
    }
}
