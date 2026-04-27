package com.example.traktneosync.data.neodb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.traktneosync.BuildConfig
import com.example.traktneosync.data.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
        
        // 动态注册新应用
        return@withContext try {
            val request = NeoDBAppRegistrationRequest(
                clientName = "TraktNeoSync",
                redirectUris = REDIRECT_URI
            )
            
            val response = neoDBApi.registerApp(request)
            currentClientId = response.clientId
            currentClientSecret = response.clientSecret
            
            // 持久化保存
            authRepository.saveNeoDBAppCredentials(currentClientId, currentClientSecret)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering app: ${e.message}")
            false
        }
    }
    
    // ========== OAuth URL ==========
    
    fun getAuthorizationUrl(): String {
        return "https://$currentInstance/oauth/authorize?" +
                "response_type=code&" +
                "client_id=$currentClientId&" +
                "redirect_uri=$REDIRECT_URI&" +
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
        
        return@withContext try {
            val tokenRequest = NeoDBTokenRequest(
                clientId = currentClientId,
                clientSecret = currentClientSecret,
                code = code
            )
            val tokenResponse = neoDBApi.exchangeToken(tokenRequest)
            
            val userProfile = neoDBApi.getUserProfile("Bearer ${tokenResponse.accessToken}")
            
            authRepository.setNeoDBAuth(
                accessToken = tokenResponse.accessToken,
                instance = currentInstance,
                user = userProfile.displayName
            )
            
            // 如果 BuildConfig 中没有设置，保存到本地
            if (BuildConfig.NEODB_CLIENT_ID.isEmpty()) {
                saveAppCredentials(currentInstance, currentClientId, currentClientSecret)
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging code for token: ${e.message}")
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
    
    // ========== 应用凭证管理 ==========
    
    private suspend fun saveAppCredentials(instance: String, clientId: String, clientSecret: String) {
        currentInstance = instance
        currentClientId = clientId
        currentClientSecret = clientSecret
        authRepository.saveNeoDBAppCredentials(clientId, clientSecret)
    }
}
