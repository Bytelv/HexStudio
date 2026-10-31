package top.lvbyte.studio.data.github

import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import top.lvbyte.studio.data.ApiException
import top.lvbyte.studio.data.SecureStore
import top.lvbyte.studio.data.StudioJson
import top.lvbyte.studio.data.execute
import top.lvbyte.studio.data.githubApi
import top.lvbyte.studio.data.jsonPut
import top.lvbyte.studio.data.model.CommitResponse
import top.lvbyte.studio.data.model.CommitResult
import top.lvbyte.studio.data.model.DeviceCodeResponse
import top.lvbyte.studio.data.model.GitHubBranch
import top.lvbyte.studio.data.model.GitHubContent
import top.lvbyte.studio.data.model.GitHubRepo
import top.lvbyte.studio.data.model.GitHubUser
import top.lvbyte.studio.data.model.GitTreeItem
import top.lvbyte.studio.data.model.GitTreeResponse
import top.lvbyte.studio.data.model.HexoValidation
import top.lvbyte.studio.data.model.PutContentRequest
import kotlinx.serialization.encodeToString
import top.lvbyte.studio.data.model.RemoteFile
import top.lvbyte.studio.data.parseJson
import top.lvbyte.studio.data.requireOk
import top.lvbyte.studio.data.sharedHttpClient
import java.nio.charset.StandardCharsets

class GitHubClient(private val secure: SecureStore) {
    private val base = "https://api.github.com"

    private fun tokenOrThrow(): String =
        secure.githubToken?.takeIf { it.isNotBlank() }
            ?: throw ApiException(401, "还没有登录 GitHub")

    suspend fun getUser(tokenOverride: String? = null): GitHubUser {
        val token = tokenOverride?.takeIf { it.isNotBlank() } ?: tokenOrThrow()
        val req = Request.Builder()
            .url("$base/user")
            .githubApi(token)
            .build()
        val res = sharedHttpClient.execute(req).requireOk()
        return parseJson<GitHubUser>(res.body)
    }

    suspend fun listRepos(): List<GitHubRepo> {
        val token = tokenOrThrow()
        val out = mutableListOf<GitHubRepo>()
        for (page in 1..4) {
            val url = "$base/user/repos".toHttpUrl().newBuilder()
                .addQueryParameter("per_page", "100")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "updated")
                .addQueryParameter("affiliation", "owner,collaborator,organization_member")
                .build()
            val req = Request.Builder().url(url).githubApi(token).build()
            val res = sharedHttpClient.execute(req).requireOk()
            val pageItems = parseJson<List<GitHubRepo>>(res.body)
            out += pageItems
            if (pageItems.size < 100) break
        }
        return out
    }

    suspend fun listBranches(owner: String, repo: String): List<GitHubBranch> {
        val token = tokenOrThrow()
        val url = "$base/repos/${enc(owner)}/${enc(repo)}/branches".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "100")
            .build()
        val req = Request.Builder().url(url).githubApi(token).build()
        val res = sharedHttpClient.execute(req).requireOk()
        return parseJson<List<GitHubBranch>>(res.body)
    }

    suspend fun getTree(owner: String, repo: String, branch: String): GitTreeResponse {
        val token = tokenOrThrow()
        val url = "$base/repos/${enc(owner)}/${enc(repo)}/git/trees/${enc(branch)}".toHttpUrl().newBuilder()
            .addQueryParameter("recursive", "1")
            .build()
        val req = Request.Builder().url(url).githubApi(token).build()
        val res = sharedHttpClient.execute(req).requireOk()
        return parseJson<GitTreeResponse>(res.body)
    }

    suspend fun getFile(owner: String, repo: String, path: String, branch: String): RemoteFile? {
        val token = tokenOrThrow()
        val encoded = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { enc(it) }
        val url = "$base/repos/${enc(owner)}/${enc(repo)}/contents/$encoded".toHttpUrl().newBuilder()
            .addQueryParameter("ref", branch)
            .build()
        val req = Request.Builder().url(url).githubApi(token).build()
        val res = sharedHttpClient.execute(req)
        if (res.code == 404) return null
        res.requireOk()
        val content = parseJson<GitHubContent>(res.body)
        val raw = content.content ?: return null
        val bytes = Base64.decode(raw.replace("\n", "").replace("\r", ""), Base64.DEFAULT)
        return RemoteFile(
            path = content.path.ifBlank { path },
            sha = content.sha,
            content = String(bytes, StandardCharsets.UTF_8)
        )
    }

    suspend fun putFile(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        content: String,
        message: String,
        sha: String?
    ): CommitResult {
        val token = tokenOrThrow()
        val encoded = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { enc(it) }
        val body = StudioJson.json.encodeToString(
            PutContentRequest(
                message = message,
                content = Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP),
                branch = branch,
                sha = sha
            )
        )
        val req = Request.Builder()
            .url("$base/repos/${enc(owner)}/${enc(repo)}/contents/$encoded")
            .githubApi(token)
            .jsonPut(body)
            .build()
        val res = sharedHttpClient.execute(req).requireOk()
        val parsed = parseJson<CommitResponse>(res.body)
        return CommitResult(parsed.content?.sha, parsed.commit?.htmlUrl)
    }

    suspend fun validateHexo(owner: String, repo: String, branch: String): HexoValidation {
        val config = getFile(owner, repo, "_config.yml", branch)
            ?: return HexoValidation(false, "仓库根目录缺少 _config.yml，不像是 Hexo 源码仓库")
        if (!config.content.contains("theme") && !config.content.contains("deploy")) {
            return HexoValidation(false, "_config.yml 存在，但看起来不是 Hexo 配置")
        }
        val tree = getTree(owner, repo, branch)
        val blobs = tree.tree.filter { it.type == "blob" }
        val posts = blobs.count { it.path.startsWith("source/_posts/") && it.path.endsWith(".md") }
        val pages = blobs.count {
            it.path.startsWith("source/") && it.path.endsWith(".md") &&
                !it.path.startsWith("source/_posts/") && !it.path.startsWith("source/_drafts/")
        }
        if (!blobs.any { it.path.startsWith("source/") }) {
            return HexoValidation(false, "仓库里没有 source/ 目录，不是 Hexo 源码")
        }
        return HexoValidation(
            ok = true,
            message = "Hexo 仓库：$posts 篇文章，$pages 个页面",
            postCount = posts,
            pageCount = pages,
            tree = tree.tree
        )
    }

    private fun enc(value: String): String = top.lvbyte.studio.data.encodePathSegment(value)

    companion object {
        val BLOCK_TAGS = setOf("folding", "tabs", "grid", "timeline")
        val COMMON_CONFIG_FILES = listOf("_config.yml", "_config.stellar.yml")
    }
}

class DeviceFlowClient {
    private val client = sharedHttpClient

    suspend fun requestDeviceCode(clientId: String, scope: String = "repo read:user"): DeviceCodeResponse {
        val form = okhttp3.FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", scope)
            .build()
        val req = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .header("User-Agent", "LvbyteStudio-Android/1.0")
            .post(form)
            .build()
        val res = client.execute(req).requireOk()
        return parseJson<DeviceCodeResponse>(res.body)
    }

    suspend fun poll(clientId: String, deviceCode: String): top.lvbyte.studio.data.model.AccessTokenResponse {
        val form = okhttp3.FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        val req = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .header("Accept", "application/json")
            .header("User-Agent", "LvbyteStudio-Android/1.0")
            .post(form)
            .build()
        val res = client.execute(req).requireOk()
        return parseJson(res.body)
    }
}

fun List<GitTreeItem>.onlyFiles(): List<GitTreeItem> = filter { it.type == "blob" }
