package top.lvbyte.studio.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class GitHubOwner(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class GitHubRepo(
    val id: Long = 0,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String? = null,
    val owner: GitHubOwner? = null,
    val description: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class GitHubBranch(val name: String)

@Serializable
data class GitTreeResponse(
    val tree: List<GitTreeItem> = emptyList(),
    val truncated: Boolean = false
)

@Serializable
data class GitTreeItem(
    val path: String,
    val type: String,
    val sha: String? = null,
    val size: Long? = null
)

@Serializable
data class GitHubContent(
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    val size: Long = 0,
    val content: String? = null,
    val encoding: String? = null,
    val type: String = "file",
    @SerialName("download_url") val downloadUrl: String? = null
)

@Serializable
data class PutContentRequest(
    val message: String,
    val content: String,
    val branch: String? = null,
    val sha: String? = null
)

@Serializable
data class GitHubCommitInfo(
    val sha: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class CommitResponse(
    val content: GitHubContent? = null,
    val commit: GitHubCommitInfo? = null
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_uri") val verificationUri: String = "",
    @SerialName("expires_in") val expiresIn: Int = 900,
    val interval: Int = 5,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

data class RemoteFile(
    val path: String,
    val sha: String,
    val content: String
)

data class CommitResult(
    val sha: String?,
    val commitUrl: String?
)

data class HexoValidation(
    val ok: Boolean,
    val message: String,
    val postCount: Int = 0,
    val pageCount: Int = 0,
    val tree: List<GitTreeItem> = emptyList()
)
