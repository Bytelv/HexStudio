package top.lvbyte.hexstudio.data.studio

import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import top.lvbyte.hexstudio.data.ApiException
import top.lvbyte.hexstudio.data.HttpResult
import top.lvbyte.hexstudio.data.SecureStore
import top.lvbyte.hexstudio.data.SessionExpiredException
import top.lvbyte.hexstudio.data.SettingsStore
import top.lvbyte.hexstudio.data.StudioJson
import top.lvbyte.hexstudio.data.execute
import top.lvbyte.hexstudio.data.jsonPatch
import top.lvbyte.hexstudio.data.jsonPost
import top.lvbyte.hexstudio.data.model.ApplicationsResponse
import top.lvbyte.hexstudio.data.model.ApproveResponse
import top.lvbyte.hexstudio.data.model.CreateFriendRequest
import top.lvbyte.hexstudio.data.model.CreateTalkRequest
import top.lvbyte.hexstudio.data.model.FeedStatus
import top.lvbyte.hexstudio.data.model.FeedStatusResponse
import top.lvbyte.hexstudio.data.model.Friend
import top.lvbyte.hexstudio.data.model.FriendApplication
import top.lvbyte.hexstudio.data.model.FriendsResponse
import top.lvbyte.hexstudio.data.model.ImportResult
import top.lvbyte.hexstudio.data.model.ImportTalksRequest
import top.lvbyte.hexstudio.data.model.NotificationsResponse
import top.lvbyte.hexstudio.data.model.ReadNotificationsRequest
import top.lvbyte.hexstudio.data.model.RefreshFeedsRequest
import top.lvbyte.hexstudio.data.model.SessionResponse
import top.lvbyte.hexstudio.data.model.SimpleResponse
import top.lvbyte.hexstudio.data.model.StatsResponse
import top.lvbyte.hexstudio.data.model.StudioNotification
import top.lvbyte.hexstudio.data.model.StudioStats
import top.lvbyte.hexstudio.data.model.Talk
import top.lvbyte.hexstudio.data.model.TalkImport
import top.lvbyte.hexstudio.data.model.TalksResponse
import top.lvbyte.hexstudio.data.model.UpdateFriendRequest
import top.lvbyte.hexstudio.data.model.UpdateTalkRequest
import top.lvbyte.hexstudio.data.parseJson
import top.lvbyte.hexstudio.data.requireOk
import top.lvbyte.hexstudio.data.session
import top.lvbyte.hexstudio.data.sharedHttpClient
import top.lvbyte.hexstudio.data.noBodyPost

class StudioClient(
    private val settings: SettingsStore,
    private val secure: SecureStore
) {
    private suspend fun baseUrl(): String = settings.settings.first().apiBase.trimEnd('/')

    private fun sessionToken(): String =
        secure.sessionToken?.takeIf { it.isNotBlank() } ?: throw SessionExpiredException()

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        auth: Boolean = true
    ): HttpResult {
        val builder = Request.Builder().url(baseUrl() + path)
        if (auth) builder.session(sessionToken())
        when (method) {
            "GET" -> builder.get()
            "POST" -> if (body != null) builder.jsonPost(body) else builder.noBodyPost()
            "PATCH" -> builder.jsonPatch(body ?: "{}")
            "DELETE" -> builder.delete()
            else -> throw IllegalArgumentException("method $method")
        }
        val res = sharedHttpClient.execute(builder.build())
        if (auth && res.code == 401) throw SessionExpiredException()
        return res
    }

    private fun <T> parseResult(res: HttpResult, parser: (String) -> T): T {
        res.requireOk()
        return try {
            parser(res.body)
        } catch (e: Exception) {
            throw ApiException(500, "服务返回的数据格式不正确")
        }
    }

    suspend fun loginWithGithub(accessToken: String): SessionResponse {
        val body = buildJsonObject { put("access_token", accessToken) }.toString()
        val res = request("POST", "/auth/github", body, auth = false)
        res.requireOk()
        val session = parseJson<SessionResponse>(res.body)
        if (session.token.isBlank()) throw ApiException(401, "没有拿到登录凭证")
        return session
    }

    suspend fun health(): Boolean {
        val res = request("GET", "/health", auth = false)
        return res.successful
    }

    suspend fun stats(): StudioStats =
        parseResult(request("GET", "/admin/stats")) { parseJson<StatsResponse>(it).stats }

    suspend fun applications(status: String = "pending"): List<FriendApplication> =
        parseResult(request("GET", "/admin/applications?status=$status&limit=200")) {
            parseJson<ApplicationsResponse>(it).content
        }

    suspend fun approveApplication(id: Long): ApproveResponse =
        parseResult(request("POST", "/admin/applications/$id/approve")) {
            parseJson<ApproveResponse>(it)
        }

    suspend fun rejectApplication(id: Long, reason: String): SimpleResponse {
        val body = buildJsonObject { put("reason", reason) }.toString()
        return parseResult(request("POST", "/admin/applications/$id/reject", body)) {
            parseJson<SimpleResponse>(it)
        }
    }

    suspend fun friends(): List<Friend> =
        parseResult(request("GET", "/admin/friends")) { parseJson<FriendsResponse>(it).content }

    suspend fun createFriend(body: CreateFriendRequest): SimpleResponse =
        parseResult(request("POST", "/admin/friends", StudioJson.json.encodeToString(body))) {
            parseJson<SimpleResponse>(it)
        }

    suspend fun updateFriend(id: Long, body: UpdateFriendRequest): SimpleResponse =
        parseResult(request("PATCH", "/admin/friends/$id", StudioJson.json.encodeToString(body))) {
            parseJson<SimpleResponse>(it)
        }

    suspend fun deleteFriend(id: Long): SimpleResponse =
        parseResult(request("DELETE", "/admin/friends/$id")) { parseJson<SimpleResponse>(it) }

    suspend fun feedStatus(): List<FeedStatus> =
        parseResult(request("GET", "/admin/feed-status")) { parseJson<FeedStatusResponse>(it).content }

    suspend fun refreshFeeds(friendId: Long? = null, limit: Int = 3): SimpleResponse {
        val body = StudioJson.json.encodeToString(RefreshFeedsRequest(friendId, limit))
        return parseResult(request("POST", "/admin/feeds/refresh", body)) {
            parseJson<SimpleResponse>(it)
        }
    }

    suspend fun notifications(unreadOnly: Boolean = true, limit: Int = 50): NotificationsResponse =
        parseResult(request("GET", "/admin/notifications?unread=${if (unreadOnly) 1 else 0}&limit=$limit")) {
            parseJson<NotificationsResponse>(it)
        }

    suspend fun markNotificationsRead(ids: List<Long>): SimpleResponse {
        val body = StudioJson.json.encodeToString(ReadNotificationsRequest(ids = ids))
        return parseResult(request("POST", "/admin/notifications/read", body)) {
            parseJson<SimpleResponse>(it)
        }
    }

    suspend fun markAllNotificationsRead(): SimpleResponse {
        val body = StudioJson.json.encodeToString(ReadNotificationsRequest(all = true))
        return parseResult(request("POST", "/admin/notifications/read", body)) {
            parseJson<SimpleResponse>(it)
        }
    }

    suspend fun talks(limit: Int = 100, before: Long? = null): List<Talk> {
        val query = buildString {
            append("/admin/talks?limit=$limit")
            if (before != null) append("&before=$before")
        }
        return parseResult(request("GET", query)) { parseJson<TalksResponse>(it).content }
    }

    suspend fun createTalk(body: CreateTalkRequest): SimpleResponse =
        parseResult(request("POST", "/admin/talks", StudioJson.json.encodeToString(body))) {
            parseJson<SimpleResponse>(it)
        }

    suspend fun updateTalk(id: String, body: UpdateTalkRequest): SimpleResponse =
        parseResult(request("PATCH", "/admin/talks/$id", StudioJson.json.encodeToString(body))) {
            parseJson<SimpleResponse>(it)
        }

    suspend fun deleteTalk(id: String): SimpleResponse =
        parseResult(request("DELETE", "/admin/talks/$id")) { parseJson<SimpleResponse>(it) }

    suspend fun importTalks(talks: List<TalkImport>): ImportResult =
        parseResult(request("POST", "/admin/talks/import", StudioJson.json.encodeToString(ImportTalksRequest(talks)))) {
            parseJson<ImportResult>(it)
        }

    suspend fun notificationById(id: Long): StudioNotification? {
        val all = notifications(unreadOnly = false, limit = 200).content
        return all.firstOrNull { it.id == id }
    }
}
