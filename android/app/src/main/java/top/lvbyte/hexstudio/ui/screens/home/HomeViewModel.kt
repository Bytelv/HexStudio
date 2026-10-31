package top.lvbyte.hexstudio.ui.screens.home

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
import top.lvbyte.hexstudio.data.model.GitTreeItem
import top.lvbyte.hexstudio.data.model.StudioStats

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

    private var loadJob: Job? = null
    private var statsCache: StudioStats? = null
    private var statsFetchedAt: Long = 0L

    init {
        load()
    }

    fun load(force: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val settings = container.settings.snapshot()
            if (!settings.hasRepo) {
                _state.update { it.copy(loading = false, error = "还没有选择 Hexo 仓库") }
                return@launch
            }

            val cached = container.repoCache.tree(settings.repoFullName, settings.repoBranch)
            val cachedUser = container.repoCache.user()
            if (cached != null) {
                val (posts, pages) = countTree(cached.items)
                _state.update {
                    it.copy(
                        loading = false,
                        repoFullName = settings.repoFullName,
                        branch = settings.repoBranch,
                        login = cachedUser?.login ?: settings.githubLogin,
                        avatarUrl = cachedUser?.avatarUrl,
                        postCount = posts,
                        pageCount = pages,
                        error = null,
                        serviceError = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        loading = true,
                        repoFullName = settings.repoFullName,
                        branch = settings.repoBranch,
                        login = settings.githubLogin,
                        error = null,
                        serviceError = null
                    )
                }
            }

            val treeFresh = cached != null &&
                !force &&
                container.repoCache.isFresh(cached.fetchedAt, TREE_TTL)
            if (!treeFresh) {
                try {
                    val tree = container.github.getTree(
                        settings.repoOwner,
                        settings.repoName,
                        settings.repoBranch
                    )
                    container.repoCache.saveTree(settings.repoFullName, settings.repoBranch, tree)
                    val (posts, pages) = countTree(tree.tree)
                    _state.update {
                        it.copy(
                            loading = false,
                            postCount = posts,
                            pageCount = pages,
                            error = null
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = if (cached == null) "读取仓库失败：${friendly(e)}" else null,
                            serviceError = if (cached == null) null else "仓库刷新失败，正在使用本地缓存"
                        )
                    }
                }
            }

            if (cachedUser == null || force ||
                !container.repoCache.isFresh(cachedUser.fetchedAt, USER_TTL)
            ) {
                try {
                    val user = container.github.getUser()
                    container.repoCache.saveUser(user)
                    container.settings.setGithubLogin(user.login)
                    _state.update { it.copy(login = user.login, avatarUrl = user.avatarUrl) }
                } catch (_: Exception) {
                    // 用户信息失败不影响仓库浏览
                }
            }

            val statsFresh = statsCache != null &&
                !force &&
                container.repoCache.isFresh(statsFetchedAt, STATS_TTL)
            if (statsFresh) {
                _state.update { it.copy(stats = statsCache, serviceError = null) }
            } else {
                try {
                    val stats = container.studio.stats()
                    statsCache = stats
                    statsFetchedAt = System.currentTimeMillis()
                    _state.update { it.copy(stats = stats, serviceError = null) }
                } catch (e: Exception) {
                    if (statsCache == null) {
                        _state.update { it.copy(serviceError = "Cloudflare 服务暂时不可用：${friendly(e)}") }
                    }
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

    private fun countTree(items: List<GitTreeItem>): Pair<Int, Int> {
        var posts = 0
        var pages = 0
        for (item in items) {
            if (item.type != "blob") continue
            if (item.path.startsWith("source/_posts/") && item.path.endsWith(".md")) {
                posts++
            } else if (item.path.startsWith("source/") && item.path.endsWith(".md") &&
                !item.path.startsWith("source/_drafts/")
            ) {
                pages++
            }
        }
        return posts to pages
    }

    private fun friendly(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() } ?: "网络异常"

    companion object {
        private const val TREE_TTL = 15 * 60 * 1000L
        private const val USER_TTL = 24 * 60 * 60 * 1000L
        private const val STATS_TTL = 60 * 1000L
    }
}
