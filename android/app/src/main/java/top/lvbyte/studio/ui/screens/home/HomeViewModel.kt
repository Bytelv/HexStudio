package top.lvbyte.studio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lvbyte.studio.core.AppContainer
import top.lvbyte.studio.data.model.StatsResponse
import top.lvbyte.studio.data.model.StudioStats

data class HomeState(
    val loading: Boolean = true,
    val repoFullName: String = "",
    val branch: String = "",
    val login: String = "",
    val avatarUrl: String? = null,
    val postCount: Int = 0,
    val pageCount: Int = 0,
    val stats: StudioStats? = null,
    val error: String? = null,
    val serviceError: String? = null
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null, serviceError = null) }
        viewModelScope.launch {
            val settings = container.settings.snapshot()
            if (!settings.hasRepo) {
                _state.update { it.copy(loading = false, error = "还没有选择 Hexo 仓库") }
                return@launch
            }
            coroutineScope {
                val treeDeferred = async {
                    runCatching {
                        container.github.getTree(settings.repoOwner, settings.repoName, settings.repoBranch)
                    }
                }
                val userDeferred = async {
                    runCatching { container.github.getUser() }
                }
                val statsDeferred = async {
                    runCatching { container.studio.stats() }
                }

                val treeResult = treeDeferred.await()
                val user = userDeferred.await().getOrNull()
                val stats = statsDeferred.await().getOrNull()
                val tree = treeResult.getOrNull()
                if (tree == null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            repoFullName = settings.repoFullName,
                            branch = settings.repoBranch,
                            login = settings.githubLogin,
                            error = "读取仓库失败：${treeResult.exceptionOrNull()?.message ?: "网络异常"}"
                        )
                    }
                    return@coroutineScope
                }
                val blobs = tree.tree.filter { it.type == "blob" }
                val posts = blobs.count { it.path.startsWith("source/_posts/") && it.path.endsWith(".md") }
                val pages = blobs.count {
                    it.path.startsWith("source/") && it.path.endsWith(".md") &&
                        !it.path.startsWith("source/_posts/") && !it.path.startsWith("source/_drafts/")
                }
                _state.update {
                    it.copy(
                        loading = false,
                        repoFullName = settings.repoFullName,
                        branch = settings.repoBranch,
                        login = user?.login ?: settings.githubLogin,
                        avatarUrl = user?.avatarUrl,
                        postCount = posts,
                        pageCount = pages,
                        stats = stats,
                        serviceError = if (stats == null) "Cloudflare 服务暂时不可用，说说/友链数据可能加载失败" else null
                    )
                }
            }
        }
    }

    fun greeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..18 -> "下午好"
            else -> "晚上好"
        }
    }
}
