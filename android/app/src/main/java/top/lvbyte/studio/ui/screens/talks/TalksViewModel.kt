package top.lvbyte.studio.ui.screens.talks

import android.net.Uri
import android.text.Html
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import top.lvbyte.studio.core.AppContainer
import top.lvbyte.studio.data.ApiException
import top.lvbyte.studio.data.SessionExpiredException
import top.lvbyte.studio.data.executeBinary
import top.lvbyte.studio.data.image.ImageBedClient
import top.lvbyte.studio.data.model.CreateTalkRequest
import top.lvbyte.studio.data.model.ImportResult
import top.lvbyte.studio.data.model.Talk
import top.lvbyte.studio.data.model.TalkImport
import top.lvbyte.studio.data.model.UpdateTalkRequest
import top.lvbyte.studio.data.sharedHttpClient

data class TalksState(
    val loading: Boolean = true,
    val talks: List<Talk> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class ImportOutcome(val imported: Int, val skipped: Int)

class TalksViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TalksState())
    val state: StateFlow<TalksState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val talks = container.studio.talks(limit = 100)
                _state.update { it.copy(loading = false, talks = talks, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    suspend fun publish(content: String, images: List<String>, editId: String? = null): String? {
        return try {
            if (editId == null) {
                container.studio.createTalk(
                    CreateTalkRequest(content = content, images = images, tags = extractTags(content))
                )
            } else {
                container.studio.updateTalk(
                    editId,
                    UpdateTalkRequest(content = content, images = images, tags = extractTags(content))
                )
            }
            load()
            null
        } catch (e: Exception) {
            friendly(e)
        }
    }

    suspend fun delete(id: String): String? {
        return try {
            container.studio.deleteTalk(id)
            load()
            null
        } catch (e: Exception) {
            friendly(e)
        }
    }

    suspend fun uploadImages(
        uris: List<Uri>,
        onProgress: (done: Int, total: Int, label: String) -> Unit
    ): List<String> {
        val urls = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            onProgress(index, uris.size, "上传第 ${index + 1}/${uris.size} 张…")
            val uploaded = container.imageBed.upload(uri)
            urls += uploaded.url
            onProgress(index + 1, uris.size, "已上传 ${index + 1}/${uris.size} 张")
            if (index != uris.lastIndex) delay(3500)
        }
        return urls
    }

    suspend fun importFromTgtalk(
        url: String,
        rehost: Boolean,
        onProgress: (String) -> Unit
    ): Pair<ImportOutcome?, String?> {
        return try {
            onProgress("正在读取 tgtalk JSON…")
            val request = Request.Builder()
                .url(url.trim())
                .header("Accept", "application/json")
                .header("User-Agent", "LvbyteStudio-Android/1.0")
                .build()
            val res = withContext(Dispatchers.IO) { sharedHttpClient.executeBinary(request) }
            if (!res.successful) {
                return null to "读取失败（HTTP ${res.code}）"
            }
            val root = Json.parseToJsonElement(String(res.bytes, Charsets.UTF_8))
            val array = root.jsonArray
            onProgress("解析到 ${array.size} 条说说，正在整理…")
            val imports = mutableListOf<TalkImport>()
            for (element in array) {
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                val rawContent = obj["content"]?.jsonPrimitive?.content ?: ""
                val content = cleanTgtalkHtml(rawContent).ifBlank { "（图片）" }
                val createdSeconds = obj["createdTs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val imageUrls = obj["resourceList"]?.jsonArray?.mapNotNull { item ->
                    item.jsonObject["externalLink"]?.jsonPrimitive?.content
                        ?: item.jsonObject["url"]?.jsonPrimitive?.content
                }?.filter { it.startsWith("http") } ?: emptyList()
                imports += TalkImport(
                    content = content,
                    images = imageUrls,
                    tags = extractTags(content),
                    createdAt = if (createdSeconds > 0) createdSeconds * 1000 else System.currentTimeMillis(),
                    sourceId = id,
                    source = "tgtalk"
                )
            }
            if (imports.isEmpty()) return null to "没有解析到说说"
            if (rehost) {
                var imageIndex = 0
                val totalImages = imports.sumOf { it.images.size }
                for ((talkIndex, talk) in imports.withIndex()) {
                    if (talk.images.isEmpty()) continue
                    val replaced = mutableListOf<String>()
                    for (image in talk.images) {
                        imageIndex++
                        onProgress("转存图片 $imageIndex/$totalImages…")
                        val newUrl = try {
                            rehostImage(image)
                        } catch (_: Exception) {
                            image
                        }
                        replaced += newUrl
                        if (imageIndex != totalImages) delay(3500)
                    }
                    imports[talkIndex] = talk.copy(images = replaced)
                }
            }
            onProgress("正在写入 Cloudflare D1…")
            val result: ImportResult = container.studio.importTalks(imports)
            load()
            onProgress("完成：导入 ${result.imported} 条，跳过 ${result.skipped} 条")
            ImportOutcome(result.imported, result.skipped) to null
        } catch (e: Exception) {
            null to friendly(e)
        }
    }

    private suspend fun rehostImage(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LvbyteStudio-Android/1.0")
            .build()
        val binary = withContext(Dispatchers.IO) { sharedHttpClient.executeBinary(request) }
        if (!binary.successful || binary.bytes.isEmpty()) throw ApiException(binary.code, "图片下载失败")
        val mime = binary.contentType.substringBefore(';').ifBlank { "image/jpeg" }
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
        val name = url.substringAfterLast('/').substringBefore('?')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .takeIf { it.isNotBlank() && it.length <= 80 }
            ?: "tgtalk_${System.currentTimeMillis()}.$ext"
        val filename = if (name.contains('.')) name else "$name.$ext"
        return container.imageBed.uploadPrepared(
            ImageBedClient.PreparedImage(binary.bytes, mime, filename)
        ).url
    }

    private fun cleanTgtalkHtml(html: String): String {
        val text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        return text
            .replace("#SFCN", " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun extractTags(content: String): List<String> {
        val regex = Regex("""(^|[\s([（])#([\p{L}\p{N}_-]{1,30})""")
        return regex.findAll(content)
            .map { it.groupValues[2] }
            .filter { it.isNotBlank() && it != "SFCN" }
            .distinct()
            .take(10)
            .toList()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun friendly(e: Exception): String {
        if (e is SessionExpiredException) return "登录状态已失效，请到设置里重新登录"
        if (e is ApiException) {
            return when (e.status) {
                401 -> "登录状态已失效，请重新登录"
                403 -> "没有权限执行这个操作"
                404 -> "接口不存在，请检查 API 地址"
                else -> e.message ?: "请求失败"
            }
        }
        return e.message ?: "网络异常"
    }
}
