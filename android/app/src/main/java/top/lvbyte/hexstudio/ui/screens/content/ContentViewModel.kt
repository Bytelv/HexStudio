package top.lvbyte.hexstudio.ui.screens.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.data.ApiException
import top.lvbyte.hexstudio.data.model.GitTreeItem
import top.lvbyte.hexstudio.ui.screens.editor.FrontMatter

enum class ContentKind(val label: String) {
    POST("文章"),
    PAGE("页面"),
    CONFIG("配置"),
    DRAFT("草稿")
}

data class ContentItem(
    val path: String,
    val name: String,
    val kind: ContentKind,
    val subtitle: String = ""
)

data class ContentState(
    val loading: Boolean = true,
    val items: List<ContentItem> = emptyList(),
    val query: String = "",
    val filter: ContentKind? = null,
    val error: String? = null,
    val creating: Boolean = false,
    val createError: String? = null,
    val createdPath: String? = null,
    val repoName: String = "",
    val branch: String = ""
)

class ContentViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ContentState())
    val state: StateFlow<ContentState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load(force: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val settings = container.settings.snapshot()
            if (!settings.hasRepo) {
                _state.update { it.copy(loading = false, error = "还没有选择仓库") }
                return@launch
            }

            val cached = container.repoCache.tree(settings.repoFullName, settings.repoBranch)
            if (cached != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        items = buildItems(cached.items),
                        error = null,
                        repoName = settings.repoFullName,
                        branch = settings.repoBranch
                    )
                }
                if (!force && container.repoCache.isFresh(cached.fetchedAt, TTL)) {
                    return@launch
                }
            } else {
                _state.update {
                    it.copy(
                        loading = true,
                        error = null,
                        repoName = settings.repoFullName,
                        branch = settings.repoBranch
                    )
                }
            }

            try {
                val tree = container.github.getTree(
                    settings.repoOwner,
                    settings.repoName,
                    settings.repoBranch
                )
                container.repoCache.saveTree(settings.repoFullName, settings.repoBranch, tree)
                _state.update {
                    it.copy(
                        loading = false,
                        items = buildItems(tree.tree),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = if (cached == null) friendly(e) else "刷新失败，正在使用本地缓存",
                        items = it.items
                    )
                }
            }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }
    fun setFilter(filter: ContentKind?) = _state.update { it.copy(filter = filter) }

    fun createPost(
        title: String,
        filenameInput: String,
        abbrlinkInput: String,
        tags: String,
        categories: String,
        description: String,
        cover: String
    ) {
        if (_state.value.creating) return
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            _state.update { it.copy(createError = "标题不能为空") }
            return
        }
        val filename = (filenameInput.trim().ifBlank { cleanTitle })
            .removeSuffix(".md")
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .trim()
        if (filename.isBlank()) {
            _state.update { it.copy(createError = "文件名不能为空") }
            return
        }
        val abbrlink = abbrlinkInput.trim().ifBlank {
            "post-" + System.currentTimeMillis().toString(36)
        }.replace(Regex("\\s+"), "-")
        val path = "source/_posts/$filename.md"
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            val settings = container.settings.snapshot()
            try {
                val existing = container.github.getFile(
                    settings.repoOwner, settings.repoName, path, settings.repoBranch
                )
                if (existing != null) {
                    _state.update { it.copy(creating = false, createError = "文件已存在：$path") }
                    return@launch
                }
                val content = FrontMatter.newPost(
                    title = cleanTitle,
                    abbrlink = abbrlink,
                    tags = tags,
                    categories = categories,
                    description = description,
                    cover = cover
                )
                container.github.putFile(
                    owner = settings.repoOwner,
                    repo = settings.repoName,
                    branch = settings.repoBranch,
                    path = path,
                    content = content,
                    message = "post: 新增 ${cleanTitle}",
                    sha = null
                )
                container.repoCache.invalidateTrees()
                _state.update { it.copy(creating = false, createdPath = path) }
                load(force = true)
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, createError = friendly(e)) }
            }
        }
    }

    fun clearCreated() = _state.update { it.copy(createdPath = null, createError = null) }
    fun clearCreateError() = _state.update { it.copy(createError = null) }

    private fun buildItems(all: List<GitTreeItem>): List<ContentItem> {
        val blobs = all.filter { it.type == "blob" }.map { it.path }
        val posts = blobs.filter { it.startsWith("source/_posts/") && it.endsWith(".md") }
            .map {
                ContentItem(
                    path = it,
                    name = it.removePrefix("source/_posts/").removeSuffix(".md"),
                    kind = ContentKind.POST,
                    subtitle = "source/_posts/"
                )
            }
        val drafts = blobs.filter { it.startsWith("source/_drafts/") && it.endsWith(".md") }
            .map {
                ContentItem(
                    path = it,
                    name = it.removePrefix("source/_drafts/").removeSuffix(".md"),
                    kind = ContentKind.DRAFT,
                    subtitle = "source/_drafts/"
                )
            }
        val pages = blobs.filter {
            it.startsWith("source/") && it.endsWith(".md") &&
                !it.startsWith("source/_posts/") && !it.startsWith("source/_drafts/")
        }.map {
            ContentItem(
                path = it,
                name = it.removePrefix("source/").removeSuffix(".md").ifBlank { "index" },
                kind = ContentKind.PAGE,
                subtitle = it.substringBeforeLast('/', "")
            )
        }
        val configs = blobs.filter {
            it == "_config.yml" || it == "_config.stellar.yml" ||
                it.startsWith("source/_data/") ||
                it.startsWith("scaffolds/") ||
                it == "package.json"
        }.map {
            ContentItem(
                path = it,
                name = it.substringAfterLast('/'),
                kind = ContentKind.CONFIG,
                subtitle = it.substringBeforeLast('/', "")
            )
        }
        return posts.sortedBy { it.name } + drafts.sortedBy { it.name } +
            pages.sortedBy { it.name } + configs.sortedBy { it.path }
    }

    private fun friendly(e: Exception): String {
        if (e is ApiException) {
            return when (e.status) {
                401 -> "GitHub 授权已失效，请重新登录"
                403 -> "没有仓库写入权限"
                404 -> "仓库不存在"
                else -> e.message ?: "请求失败"
            }
        }
        return e.message ?: "网络异常"
    }

    companion object {
        private const val TTL = 15 * 60 * 1000L
    }
}
