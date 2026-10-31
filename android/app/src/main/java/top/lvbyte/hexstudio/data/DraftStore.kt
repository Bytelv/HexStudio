package top.lvbyte.hexstudio.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

@Serializable
data class Draft(
    val repo: String,
    val path: String,
    val content: String,
    val savedAt: Long = System.currentTimeMillis()
)

class DraftStore(private val context: Context) {
    private val dir: File by lazy {
        File(context.filesDir, "drafts").also { if (!it.exists()) it.mkdirs() }
    }

    suspend fun save(draft: Draft) = withContext(Dispatchers.IO) {
        fileFor(draft.repo, draft.path).writeText(StudioJson.json.encodeToString(draft))
    }

    suspend fun load(repo: String, path: String): Draft? = withContext(Dispatchers.IO) {
        val file = fileFor(repo, path)
        if (!file.exists()) return@withContext null
        try {
            StudioJson.json.decodeFromString<Draft>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun delete(repo: String, path: String) = withContext(Dispatchers.IO) {
        fileFor(repo, path).delete()
    }

    suspend fun list(repo: String): List<Draft> = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.mapNotNull { file ->
                try {
                    StudioJson.json.decodeFromString<Draft>(file.readText())
                } catch (_: Exception) {
                    null
                }
            }
            ?.filter { it.repo == repo }
            ?.sortedByDescending { it.savedAt }
            ?: emptyList()
    }

    suspend fun clear(repo: String) = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.forEach { file ->
                try {
                    val draft = StudioJson.json.decodeFromString<Draft>(file.readText())
                    if (draft.repo == repo) file.delete()
                } catch (_: Exception) {
                    // ignore broken draft
                }
            }
    }

    private fun fileFor(repo: String, path: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$repo\n$path".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.json")
    }
}
