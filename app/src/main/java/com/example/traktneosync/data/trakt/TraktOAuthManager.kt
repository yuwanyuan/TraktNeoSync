package com.example.traktneosync.data.trakt

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.traktneosync.BuildConfig
import com.example.traktneosync.data.AuthRepository
import com.example.traktneosync.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktOAuthManager @Inject constructor(
    private val traktApi: TraktApiService,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TraktOAuthManager"
        const val REDIRECT_URI = "traktneosync://trakt"
    }
    
    // ========== Device Code Flow (推荐) ==========
    
    data class DeviceCodeResult(
        val userCode: String,
        val verificationUrl: String,
        val deviceCode: String,
        val expiresIn: Int,
        val interval: Int
    )
    
    suspend fun startDeviceCodeFlow(): DeviceCodeResult? = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = TraktDeviceCodeRequest(
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
            val response = traktApi.getDeviceCode(request)
            
            DeviceCodeResult(
                userCode = response.userCode,
                verificationUrl = response.verificationUrl,
                deviceCode = response.deviceCode,
                expiresIn = response.expiresIn,
                interval = response.interval
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "启动Device Code Flow失败", e)
            null
        }
    }
    
    // 轮询获取 token
    fun pollForToken(deviceCode: String, interval: Int, expiresIn: Int): Flow<TokenStatus> = flow {
        val startTime = System.currentTimeMillis()
        val expiresAt = startTime + (expiresIn * 1000)
        
        while (System.currentTimeMillis() < expiresAt) {
            delay((interval * 1000).toLong())
            
            try {
                val tokenRequest = TraktTokenRequest(
                    code = deviceCode,
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    clientSecret = BuildConfig.TRAKT_CLIENT_SECRET,
                    grantType = "urn:ietf:params:oauth:grant-type:device_code"
                )
                val tokenResponse = traktApi.exchangeToken(tokenRequest)
                
                // 获取用户信息
                val userProfile = traktApi.getUserProfile("Bearer ${tokenResponse.accessToken}")
                
                // 保存认证信息
                authRepository.setTraktAuth(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    user = userProfile.username,
                    expiresIn = tokenResponse.expiresIn
                )
                
                emit(TokenStatus.Success(userProfile.username))
                return@flow
                
            } catch (e: Exception) {
                // 如果是授权待定的错误，继续轮询
                if (e.message?.contains("pending") == true || 
                    e.message?.contains("authorization_pending") == true) {
                    emit(TokenStatus.Pending)
                } else if (e.message?.contains("slow_down") == true) {
                    // 请求太快，增加间隔
                    delay(5000)
                } else if (e.message?.contains("expired_token") == true) {
                    emit(TokenStatus.Expired)
                    return@flow
                } else {
                    emit(TokenStatus.Error(e.message ?: "Unknown error"))
                    return@flow
                }
            }
        }
        
        emit(TokenStatus.Expired)
    }
    
    sealed class TokenStatus {
        object Pending : TokenStatus()
        data class Success(val username: String) : TokenStatus()
        object Expired : TokenStatus()
        data class Error(val message: String) : TokenStatus()
    }
    
    // ========== 传统 OAuth Flow ==========
    
    fun getAuthorizationUrl(): String {
        val encodedRedirectUri = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "https://trakt.tv/oauth/authorize?" +
                "response_type=code&" +
                "client_id=${BuildConfig.TRAKT_CLIENT_ID}&" +
                "redirect_uri=$encodedRedirectUri"
    }
    
    suspend fun handleCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code") ?: return@withContext false

        AppLogger.info(TAG, "开始Trakt Token交换", mapOf("codePrefix" to code.take(4), "clientIdEmpty" to BuildConfig.TRAKT_CLIENT_ID.isEmpty()))

        return@withContext try {
            val tokenRequest = TraktTokenRequest(
                code = code,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                clientSecret = BuildConfig.TRAKT_CLIENT_SECRET
            )
            AppLogger.debug(TAG, "请求Trakt Token", mapOf("grantType" to tokenRequest.grantType))
            val tokenResponse = traktApi.exchangeToken(tokenRequest)
            AppLogger.info(TAG, "Trakt Token获取成功", mapOf("tokenType" to tokenResponse.tokenType, "expiresIn" to tokenResponse.expiresIn))

            val userProfile = traktApi.getUserProfile("Bearer ${tokenResponse.accessToken}")
            AppLogger.info(TAG, "Trakt用户资料获取成功", mapOf("username" to userProfile.username))

            authRepository.setTraktAuth(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                user = userProfile.username,
                expiresIn = tokenResponse.expiresIn
            )
            AppLogger.info(TAG, "Trakt认证信息已保存")

            true
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            AppLogger.error(TAG, "Trakt OAuth HTTP错误", e, mapOf(
                "code" to e.code(),
                "errorBody" to errorBody.take(200),
                "clientIdEmpty" to BuildConfig.TRAKT_CLIENT_ID.isEmpty()
            ))
            false
        } catch (e: Exception) {
            AppLogger.error(TAG, "Trakt OAuth回调换Token失败", e, mapOf(
                "code" to code.take(4),
                "type" to e.javaClass.simpleName,
                "clientIdEmpty" to BuildConfig.TRAKT_CLIENT_ID.isEmpty()
            ))
            false
        }
    }
    
    // ========== 打开浏览器授权 ==========
    
    fun openAuthorizationInBrowser() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getAuthorizationUrl()))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    // ========== 登出 ==========
    
    suspend fun logout() {
        authRepository.clearTraktAuth()
    }
    
    suspend fun isAuthenticated(): Boolean {
        return authRepository.traktAccessToken.first() != null
    }

    // ========== Token 刷新 ==========

    suspend fun ensureValidToken(): Boolean = withContext(Dispatchers.IO) {
        val accessToken = authRepository.traktAccessToken.first()
            ?: return@withContext false

        val expiresAt = authRepository.traktTokenExpiresAt.first()
        if (expiresAt != null && System.currentTimeMillis() < expiresAt - 300_000) {
            return@withContext true
        }

        if (expiresAt == null) {
            return@withContext true
        }

        val refreshToken = authRepository.traktRefreshToken.first()
            ?: return@withContext false

        return@withContext try {
            val request = TraktRefreshTokenRequest(
                refreshToken = refreshToken,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                clientSecret = BuildConfig.TRAKT_CLIENT_SECRET
            )
            val response = traktApi.refreshToken(request)
            authRepository.updateTraktAccessToken(
                accessToken = response.accessToken,
                expiresIn = response.expiresIn
            )
            true
        } catch (e: Exception) {
            AppLogger.error(TAG, "Trakt Token刷新失败，需重新登录", e)
            authRepository.clearTraktAuth()
            false
        }
    }
}
