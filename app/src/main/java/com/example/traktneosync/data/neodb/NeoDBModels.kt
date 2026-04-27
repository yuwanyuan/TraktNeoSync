package com.example.traktneosync.data.neodb

import com.google.gson.annotations.SerializedName

// ========== NeoDB OAuth ==========

data class NeoDBAppRegistrationRequest(
    @SerializedName("client_name") val clientName: String,
    @SerializedName("redirect_uris") val redirectUris: String,
    @SerializedName("website") val website: String = "https://github.com"
)

data class NeoDBAppRegistrationResponse(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String
)

data class NeoDBTokenRequest(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String,
    @SerializedName("code") val code: String,
    @SerializedName("redirect_uri") val redirectUri: String = "traktneosync://neodb",
    @SerializedName("grant_type") val grantType: String = "authorization_code"
)

data class NeoDBTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("scope") val scope: String,
    @SerializedName("created_at") val createdAt: Long
)

// ========== 条目模型 ==========

enum class NeoDBEntryType {
    book, movie, tv, music, game, podcast, performance
}

enum class NeoDBShelfType {
    wishlist, progress, complete, dropped
}

data class NeoDBEntry(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("url") val url: String,
    @SerializedName("api_url") val apiUrl: String,
    @SerializedName("category") val category: String,
    @SerializedName("display_title") val displayTitle: String,
    @SerializedName("external_resources") val externalResources: List<NeoDBExternalResource> = emptyList(),
    @SerializedName("brief") val brief: String = "",
    @SerializedName("cover_image_url") val coverImageUrl: String? = null,
    @SerializedName("rating") val rating: Float? = null,
    @SerializedName("rating_count") val ratingCount: Int = 0
)

data class NeoDBExternalResource(
    @SerializedName("url") val url: String
)

// ========== 书架模型 ==========

data class NeoDBMark(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("url") val url: String,
    @SerializedName("item") val item: NeoDBEntry,
    @SerializedName("shelf_type") val shelfType: String,
    @SerializedName("visibility") val visibility: Int = 0,
    @SerializedName("created_time") val createdTime: String,
    @SerializedName("rating_grade") val ratingGrade: Int? = null,
    @SerializedName("comment_text") val commentText: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList()
)

data class NeoDBPagedMarks(
    @SerializedName("pages") val pages: Int,
    @SerializedName("count") val count: Int,
    @SerializedName("data") val data: List<NeoDBMark>
)

// ========== 标记请求 ==========

data class NeoDBMarkRequest(
    @SerializedName("shelf_type") val shelfType: String,
    @SerializedName("visibility") val visibility: Int = 0,
    @SerializedName("rating_grade") val ratingGrade: Int? = null,
    @SerializedName("comment_text") val commentText: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList()
)

// ========== 搜索 ==========

data class NeoDBSearchResult(
    @SerializedName("pages") val pages: Int,
    @SerializedName("count") val count: Int,
    @SerializedName("data") val data: List<NeoDBEntry>
)

// ========== 实例信息 ==========

data class NeoDBInstance(
    @SerializedName("api_url") val apiUrl: String,
    @SerializedName("domain") val domain: String,
    @SerializedName("nickname") val nickname: String? = null
)

data class NeoDBPublicInstance(
    @SerializedName("domain") val domain: String,
    @SerializedName("users") val users: Int,
    @SerializedName("name") val name: String? = null
)

// ========== 用户 ==========

data class NeoDBUser(
    @SerializedName("url") val url: String,
    @SerializedName("external_acct") val externalAcct: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)
