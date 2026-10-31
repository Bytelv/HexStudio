package top.lvbyte.hexstudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.data.ApiException
import top.lvbyte.hexstudio.data.Draft
import top.lvbyte.hexstudio.util.StellarTags

data class EditorState(
    val loading: Boolean = true,
    val path: String = "",
    val fileName: String = "",
    val repoFullName: String = "",
    val branch: String = "",
    val kind: String = "post",
    val sha: String? = null,
    val body: String = "",
    val frontMatter: String = "",
    val fields: Map<String, String> = emptyMap(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val draftFound: String? = null,
    val uploading: Boolean = false,
    val uploadDone: Int = 0,
    val uploadCount: Int = 0,
    val uploadLabel: String = "",
    val useStellarImageTag: Boolean = false
) {
    val title: String
        get() = fields["title"]?.trim()?.trim('\'', '"')?.takeIf { it.isNotBlank() }
            ?: fileName.removeSuffix(".md")

    val isConfig: Boolean get() = kind == "config"
}

sealed interface EditorEvent {
    data class Insert(val text: String) : EditorEvent
    data class Replace(val text: String) : EditorEvent
    data class Toast(val text: String) : EditorEvent
}

class EditorViewModel(
    private val container: AppContainer,
    private val path: String
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState(path = path, fileName = path.substringAfterLast('/')))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 24)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val settings = container.settings.snapshot()
            if (!settings.hasRepo) {
                _state.update { it.copy(loading = false, error = "还没有选择仓库") }
                return@launch
            }
            try {
                val remote = container.github.getFile(
                    settings.repoOwner,
                    settings.repoName,
                    path,
                    settings.repoBranch
                )
                if (remote == null) {
                    _state.update { it.copy(loading = false, error = "文件不存在：$path") }
                    return@launch
                }
                val parts = FrontMatter.split(remote.content)
                val kind = kindOf(path)
                val fields = parts.fields
                val draft = container.drafts.load(settings.repoFullName, path)
                _state.update {
                    it.copy(
                        loading = false,
                        sha = remote.sha,
                        body = if (kind == "config") remote.content else parts.body,
                        frontMatter = parts.frontMatter,
                        fields = fields,
                        kind = kind,
                        repoFullName = settings.repoFullName,
                        branch = settings.repoBranch,
                        dirty = false,
                        error = null,
                        draftFound = draft?.content?.takeIf { content -> content != remote.content },
                        useStellarImageTag = settings.useStellarImageTag
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun setBody(text: String) {
        _state.update {
            if (it.body == text) it else it.copy(body = text, dirty = true)
        }
    }

    fun applyFrontMatter(data: FrontMatterData) {
        val front = FrontMatter.update(_state.value.frontMatter, data)
        _state.update {
            it.copy(
                frontMatter = front,
                fields = FrontMatter.fields(front),
                dirty = true
            )
        }
    }

    fun saveDraft() {
        val current = _state.value
        if (!current.dirty || current.loading) return
        viewModelScope.launch {
            container.drafts.save(
                Draft(
                    repo = current.repoFullName,
                    path = current.path,
                    content = fullContent(current),
                    savedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun restoreDraft() {
        val content = _state.value.draftFound ?: return
        val parts = FrontMatter.split(content)
        val nextBody = if (_state.value.isConfig) content else parts.body
        _state.update {
            it.copy(
                body = nextBody,
                frontMatter = parts.frontMatter,
                fields = parts.fields,
                dirty = true,
                draftFound = null,
                message = "已恢复本地草稿"
            )
        }
        viewModelScope.launch { _events.emit(EditorEvent.Replace(nextBody)) }
    }

    fun discardDraft() {
        val current = _state.value
        viewModelScope.launch {
            container.drafts.delete(current.repoFullName, current.path)
            _state.update { it.copy(draftFound = null) }
        }
    }

    fun save(commitMessage: String, force: Boolean = false) {
        val current = _state.value
        if (current.saving || current.loading) return
        if (!current.dirty) {
            _state.update { it.copy(message = "没有需要保存的修改") }
            return
        }
        val blocking = validate().filter {
            it.contains("没有闭合") || it.contains("闭合不匹配") || it.contains("about")
        }
        if (blocking.isNotEmpty() && !force) {
            _state.update { it.copy(error = "标签校验未通过：${blocking.first()}") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val settings = container.settings.snapshot()
            try {
                val result = container.github.putFile(
                    owner = settings.repoOwner,
                    repo = settings.repoName,
                    branch = settings.repoBranch,
                    path = current.path,
                    content = fullContent(current),
                    message = commitMessage.trim().ifBlank { defaultCommitMessage(current) },
                    sha = current.sha
                )
                container.drafts.delete(settings.repoFullName, current.path)
                container.repoCache.invalidateTrees()
                _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        sha = result.sha ?: it.sha,
                        error = null,
                        message = "已提交到 GitHub，Cloudflare Pages 会自动构建",
                        draftFound = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = friendly(e)) }
            }
        }
    }

    fun validate(): List<String> {
        val current = _state.value
        if (current.isConfig) return emptyList()
        return StellarTags.validate(current.body, current.fields)
    }

    fun defaultCommitMessage(state: EditorState = _state.value): String {
        return when (state.kind) {
            "post" -> "post: 更新 ${state.title}"
            "config" -> "config: 更新 ${state.path}"
            else -> "page: 更新 ${state.path}"
        }
    }

    fun uploadImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty() || _state.value.uploading) return
        val useStellar = _state.value.useStellarImageTag
        _state.update {
            it.copy(uploading = true, uploadDone = 0, uploadCount = uris.size, uploadLabel = "准备上传…", error = null)
        }
        viewModelScope.launch {
            try {
                uris.forEachIndexed { index, uri ->
                    _state.update { it.copy(uploadLabel = "上传第 ${index + 1}/${uris.size} 张…") }
                    val uploaded = container.imageBed.upload(uri)
                    val alt = uploaded.filename.substringBeforeLast('.').ifBlank { "图片" }
                    val snippet = if (useStellar) {
                        "{% image ${uploaded.url} ${alt} %}"
                    } else {
                        "![${alt}](${uploaded.url})"
                    }
                    _events.emit(EditorEvent.Insert(snippet))
                    if (!uploaded.warning.isNullOrBlank()) {
                        _events.emit(EditorEvent.Toast(uploaded.warning ?: ""))
                    }
                    _state.update { it.copy(uploadDone = index + 1) }
                    if (index != uris.lastIndex) delay(3500)
                }
                _state.update { it.copy(uploading = false, uploadLabel = "上传完成") }
            } catch (e: Exception) {
                _state.update { it.copy(uploading = false, uploadLabel = "") }
                _events.emit(EditorEvent.Toast(friendly(e)))
            }
        }
    }

    private fun fullContent(state: EditorState = _state.value): String {
        if (state.isConfig) return state.body
        val front = state.frontMatter.ifBlank {
            FrontMatter.update("---\n---\n", FrontMatter.dataFromFields(""))
        }
        return front.trimEnd() + "\n\n" + state.body.trimStart()
    }

    private fun kindOf(path: String): String {
        return when {
            path == "_config.yml" || path == "_config.stellar.yml" ||
                path.startsWith("source/_data/") || path.startsWith("scaffolds/") ||
                path.endsWith(".yml") || path.endsWith(".yaml") ||
                path == "package.json" -> "config"
            path.startsWith("source/_posts/") -> "post"
            else -> "page"
        }
    }

    private fun friendly(e: Exception): String {
        if (e is ApiException) {
            return when (e.status) {
                401 -> "GitHub 授权已失效，请到设置里重新登录"
                403 -> "没有写入权限，请检查 Token 的 Contents 权限"
                404 -> "文件或仓库不存在"
                409, 422 -> "远程文件已经被更新，请返回重新打开这个文件再改"
                else -> e.message ?: "提交失败"
            }
        }
        return e.message ?: "网络异常"
    }
}
