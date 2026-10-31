package top.lvbyte.hexstudio.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class StudioUser(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class SessionResponse(
    val ok: Boolean = true,
    val token: String,
    @SerialName("expires_at") val expiresAt: Long = 0,
    val user: StudioUser
)

@Serializable
data class StudioStats(
    val friends: Int = 0,
    val talks: Int = 0,
    @SerialName("pending_applications") val pendingApplications: Int = 0,
    @SerialName("unread_notifications") val unreadNotifications: Int = 0,
    val articles: Int = 0,
    @SerialName("feed_errors") val feedErrors: Int = 0
)

@Serializable
data class StatsResponse(val ok: Boolean = true, val stats: StudioStats = StudioStats())

@Serializable
data class Friend(
    val id: Long,
    val title: String,
    val url: String,
    val avatar: String = "",
    val description: String = "",
    val keywords: String = "",
    @SerialName("feed_url") val feedUrl: String? = null,
    @SerialName("feed_kind") val feedKind: String = "feed",
    @SerialName("feed_enabled") val feedEnabled: Boolean = true,
    val visible: Boolean = true,
    val sort: Int = 0,
    @SerialName("last_fetched") val lastFetched: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("fail_count") val failCount: Int = 0,
    @SerialName("next_fetch") val nextFetch: String? = null
)

@Serializable
data class FriendApplication(
    val id: Long,
    val name: String,
    val url: String,
    val avatar: String = "",
    val description: String = "",
    val email: String = "",
    val message: String = "",
    val status: String = "pending",
    val note: String = "",
    val created: String = "",
    @SerialName("reviewed") val reviewed: String = ""
)

@Serializable
data class Talk(
    val id: String,
    val content: String,
    val images: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    val created: String = "",
    @SerialName("updated_at") val updatedAt: Long = 0,
    val visible: Boolean = true,
    val source: String? = null,
    @SerialName("source_id") val sourceId: String? = null
)

@Serializable
data class StudioNotification(
    val id: Long,
    val type: String,
    val title: String,
    val body: String = "",
    val data: JsonObject = JsonObject(emptyMap()),
    val read: Boolean = false,
    val created: String = "",
    @SerialName("created_at") val createdAt: Long = 0
)

@Serializable
data class FeedStatus(
    val id: Long,
    val title: String,
    val url: String,
    @SerialName("feed_url") val feedUrl: String? = null,
    @SerialName("feed_kind") val feedKind: String = "feed",
    @SerialName("feed_enabled") val feedEnabled: Boolean = true,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("fail_count") val failCount: Int = 0,
    @SerialName("last_fetched") val lastFetched: String? = null,
    @SerialName("next_fetch") val nextFetch: String? = null
)

@Serializable
data class ApplicationsResponse(val ok: Boolean = true, val content: List<FriendApplication> = emptyList())

@Serializable
data class FriendsResponse(val ok: Boolean = true, val content: List<Friend> = emptyList())

@Serializable
data class TalksResponse(val ok: Boolean = true, val content: List<Talk> = emptyList())

@Serializable
data class NotificationsResponse(
    val ok: Boolean = true,
    @SerialName("unread_count") val unreadCount: Int = 0,
    val content: List<StudioNotification> = emptyList()
)

@Serializable
data class FeedStatusResponse(val ok: Boolean = true, val content: List<FeedStatus> = emptyList())

@Serializable
data class SimpleResponse(val ok: Boolean = true, val message: String? = null, val error: String? = null)

@Serializable
data class CreateFriendRequest(
    val title: String,
    val url: String,
    val avatar: String = "",
    val description: String = "",
    val keywords: String = "",
    @SerialName("feed_url") val feedUrl: String? = null,
    @SerialName("feed_kind") val feedKind: String = "feed",
    @SerialName("feed_enabled") val feedEnabled: Boolean = true,
    val visible: Boolean = true
)

@Serializable
data class UpdateFriendRequest(
    val title: String? = null,
    val url: String? = null,
    val avatar: String? = null,
    val description: String? = null,
    val keywords: String? = null,
    @SerialName("feed_url") val feedUrl: String? = null,
    @SerialName("feed_enabled") val feedEnabled: Boolean? = null,
    val visible: Boolean? = null
)

@Serializable
data class CreateTalkRequest(
    val content: String,
    val images: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("source_id") val sourceId: String? = null,
    val source: String = "apk"
)

@Serializable
data class UpdateTalkRequest(
    val content: String? = null,
    val images: List<String>? = null,
    val tags: List<String>? = null,
    val visible: Boolean? = null
)

@Serializable
data class TalkImport(
    val content: String,
    val images: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    @SerialName("source_id") val sourceId: String? = null,
    val source: String = "tgtalk"
)

@Serializable
data class ImportTalksRequest(val talks: List<TalkImport>)

@Serializable
data class ImportResult(val ok: Boolean = true, val imported: Int = 0, val skipped: Int = 0)

@Serializable
data class ApproveResponse(val ok: Boolean = true, @SerialName("friend_id") val friendId: Long = 0, val message: String? = null)

@Serializable
data class ReadNotificationsRequest(val ids: List<Long>? = null, val all: Boolean? = null)

@Serializable
data class RefreshFeedsRequest(@SerialName("friend_id") val friendId: Long? = null, val limit: Int? = null)
