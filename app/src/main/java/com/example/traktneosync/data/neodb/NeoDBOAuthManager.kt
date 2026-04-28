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
        currentInstance = instance
        baseUrlProvider.baseUrl = "https://$instance/"

        // 优先使用 BuildConfig 预设的凭证
        if (BuildConfig.NEODB_CLIENT_ID.isNotEmpty()) {
            currentClientId = BuildConfig.NEODB_CLIENT_ID
            currentClientSecret = BuildConfig.NEODB_CLIENT_SECRET
            return@withContext true
        }
        
        // 尝试从持久化存储读取
        val saved = authRepository.getNeoDBAppCredentials()
        if (saved != null) {
            currentClientId = saved.first
            currentClientSecret = saved.second
            return@withContext true
        }
        
        // 动态注册新应用（带重试）
        var lastException: Exception? = null
        repeat(2) { attempt ->
            try {
                AppLogger.info(TAG, "开始注册NeoDB应用", mapOf("instance" to instance, "attempt" to (attempt + 1), "url" to "https://$instance/api/v1/apps"))
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
                AppLogger.info(TAG, "NeoDB应用注册成功", mapOf("instance" to instance, "clientIdPrefix" to currentClientId.take(8)))

                return@withContext true
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: ""
                AppLogger.error(TAG, "注册NeoDB应用HTTP失败", e, mapOf(
                    "instance" to instance,
                    "attempt" to (attempt + 1),
                    "code" to e.code(),
                    "errorBody" to errorBody.take(200)
                ))
                lastException = e
                if (attempt == 0) kotlinx.coroutines.delay(1000)
            } catch (e: java.net.UnknownHostException) {
                AppLogger.error(TAG, "NeoDB实例无法解析", e, mapOf("instance" to instance, "attempt" to (attempt + 1)))
                lastException = e
                return@withContext false
            } catch (e: java.net.SocketTimeoutException) {
                AppLogger.error(TAG, "NeoDB连接超时", e, mapOf("instance" to instance, "attempt" to (attempt + 1)))
                lastException = e
                if (attempt == 0) kotlinx.coroutines.delay(1000)
            } catch (e: Exception) {
                AppLogger.error(TAG, "注册NeoDB应用失败", e, mapOf("instance" to instance, "attempt" to (attempt + 1), "type" to e.javaClass.simpleName))
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getAuthorizationUrl()))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
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
            val tokenRequest = NeoDBTokenRequest(
                clientId = currentClientId,
                clientSecret = currentClientSecret,
                code = code
            )
            AppLogger.debug(TAG, "请求NeoDB Token", mapOf("instance" to currentInstance, "clientIdPrefix" to currentClientId.take(8)))
            val tokenResponse = neoDBApi.exchangeToken(tokenRequest)
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
            return@withContext true
        }

        if (expiresAt == null) {
            return@withContext true
        }

        val refreshToken = authRepository.neodbRefreshToken.first()
            ?: return@withContext false

        // 确保当前实例和凭证已设置
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

        return@withContext try {
            val request = NeoDBRefreshTokenRequest(
                clientId = currentClientId,
                clientSecret = currentClientSecret,
                refreshToken = refreshToken
            )
            val response = neoDBApi.refreshToken(request)
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

    // ========== 应用凭证管理 ==========

    private suspend fun saveAppCredentials(instance: String, clientId: String, clientSecret: String) {
        currentInstance = instance
        currentClientId = clientId
        currentClientSecret = clientSecret
        authRepository.saveNeoDBAppCredentials(clientId, clientSecret)
    }
}
