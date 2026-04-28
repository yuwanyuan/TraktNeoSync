package com.example.traktneosync.data.neodb

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NeoDBApiService {
    
    // ========== OAuth ==========
    
    @FormUrlEncoded
    @POST("api/v1/apps")
    suspend fun registerApp(
        @Field("client_name") clientName: String,
        @Field("redirect_uris") redirectUris: String,
        @Field("website") website: String = "https://github.com",
        @Field("scopes") scopes: String = "read write"
    ): NeoDBAppRegistrationResponse
    
    @POST("oauth/token")
    suspend fun exchangeToken(
        @Body request: NeoDBTokenRequest
    ): NeoDBTokenResponse

    @POST("oauth/token")
    suspend fun refreshToken(
        @Body request: NeoDBRefreshTokenRequest
    ): NeoDBTokenResponse
    
    // ========== 用户资料 ==========
    
    @GET("api/me")
    suspend fun getUserProfile(
        @Header("Authorization") token: String
    ): NeoDBUser
    
    // ========== 书架 ==========
    
    @GET("api/me/shelf/{shelfType}")
    suspend fun getShelf(
        @Header("Authorization") token: String,
        @Path("shelfType") shelfType: String,
        @Query("page") page: Int = 1
    ): NeoDBPagedMarks
    
    // ========== 条目详情 ==========
    
    @GET("api/{category}/{uuid}")
    suspend fun getItemDetail(
        @Header("Authorization") token: String,
        @Path("category") category: String,
        @Path("uuid") uuid: String
    ): NeoDBEntry
    
    // ========== 检查条目是否在书架 ==========
    
    @GET("api/me/shelf/item/{uuid}")
    suspend fun getItemMark(
        @Header("Authorization") token: String,
        @Path("uuid") uuid: String
    ): NeoDBMark
    
    // ========== 添加/更新标记 ==========
    
    @POST("api/me/shelf/item/{uuid}")
    suspend fun addOrUpdateMark(
        @Header("Authorization") token: String,
        @Path("uuid") uuid: String,
        @Body request: NeoDBMarkRequest
    ): NeoDBMark
    
    // ========== 删除标记 ==========
    
    @DELETE("api/me/shelf/item/{uuid}")
    suspend fun deleteMark(
        @Header("Authorization") token: String,
        @Path("uuid") uuid: String
    )
    
    // ========== 搜索 ==========
    
    @GET("api/catalog/search")
    suspend fun search(
        @Header("Authorization") token: String,
        @Query("query") query: String,
        @Query("category") category: String? = null,
        @Query("page") page: Int = 1
    ): NeoDBSearchResult
    
    // ========== 热门条目 ==========
    
    @GET("api/trending/{category}")
    suspend fun getTrending(
        @Path("category") category: String
    ): List<NeoDBEntry>
    
    // ========== 评论 ==========

    @GET("api/item/{uuid}/posts/")
    suspend fun getItemPosts(
        @Header("Authorization") token: String,
        @Path("uuid") uuid: String,
        @Query("type") type: String = "comment",
        @Query("page") page: Int = 1,
    ): NeoDBPaginatedPosts

    // ========== 实例列表 ==========

    @GET("https://api.neodb.app/servers")
    suspend fun getPublicInstances(): List<NeoDBPublicInstance>
}
