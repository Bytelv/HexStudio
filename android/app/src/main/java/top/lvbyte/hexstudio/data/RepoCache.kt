package top.lvbyte.hexstudio.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import top.lvbyte.hexstudio.data.model.GitHubUser
import top.lvbyte.hexstudio.data.model.GitTreeItem
import top.lvbyte.hexstudio.data.model.GitTreeResponse
import java.io.File
import java.security.MessageDigest

@Serializable
data class CachedTree(
    val repo: String,
    val branch: String,
    val fetchedAt: Long,
    val items: List<GitTreeItem>
)

@Serializable
data class CachedUser(
    val login: String,
    val avatarUrl: String? = null,
    val fetchedAt: Long
)

/**
 * 仓库文件树和 GitHub 用户信息的本地缓存。
 * 冷启动先读缓存，15 分钟内不重复访问 GitHub；编辑提交后由调用方 invalidate。
 */
class RepoCache(context: Context) {
    private val dir = File(context.filesDir, "repo-cache").apply { if (!exists()) mkdirs() }
    private val userFile = File(dir, "user.json")

    suspend fun tree(repo: String, branch: String): CachedTree? = withContext(Dispatchers.IO) {
        val file = treeFile(repo, branch)
        if (!file.exists()) return@withContext null
        try {
            StudioJson.json.decodeFromString<CachedTree>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveTree(repo: String, branch: String, tree: GitTreeResponse) = withContext(Dispatchers.IO) {
        try {
            treeFile(repo, branch).writeText(
                StudioJson.json.encodeToString(
                    CachedTree(
                        repo = repo,
                        branch = branch,
                        fetchedAt = System.currentTimeMillis(),
                        items = tree.tree
                    )
                )
            )
        } catch (_: Exception) {
            // 缓存失败不能影响正常使用
        }
    }

    suspend fun user(): CachedUser? = withContext(Dispatchers.IO) {
        if (!userFile.exists()) return@withContext null
        try {
            StudioJson.json.decodeFromString<CachedUser>(userFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveUser(user: GitHubUser) = withContext(Dispatchers.IO) {
        try {
            userFile.writeText(
                StudioJson.json.encodeToString(
                    CachedUser(
                        login = user.login,
                        avatarUrl = user.avatarUrl,
                        fetchedAt = System.currentTimeMillis()
                    )
                )
            )
        } catch (_: Exception) {
            // ignore
        }
    }

    suspend fun invalidateTrees() = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.isFile && file.name.startsWith("tree-") }?.forEach { it.delete() }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun isFresh(fetchedAt: Long, ttlMillis: Long): Boolean =
        System.currentTimeMillis() - fetchedAt in 0..ttlMillis

    private fun treeFile(repo: String, branch: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$repo\n$branch".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "tree-$digest.json")
    }
}
