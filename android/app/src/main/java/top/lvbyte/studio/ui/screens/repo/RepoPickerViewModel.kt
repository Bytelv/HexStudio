package top.lvbyte.studio.ui.screens.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lvbyte.studio.core.AppContainer
import top.lvbyte.studio.data.ApiException
import top.lvbyte.studio.data.model.GitHubRepo

data class RepoPickerState(
    val loading: Boolean = false,
    val repos: List<GitHubRepo> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val validating: Boolean = false,
    val branches: List<String> = emptyList(),
    val branchDialogRepo: GitHubRepo? = null,
    val branchDialogBranches: List<String> = emptyList(),
    val selectedBranch: String = "",
    val manualDialog: Boolean = false,
    val saved: Boolean = false,
    val savedRepo: String = "",
    val githubLogin: String = ""
)

class RepoPickerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(RepoPickerState())
    val state: StateFlow<RepoPickerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = container.settings.snapshot()
            _state.update { it.copy(githubLogin = s.githubLogin) }
        }
        loadRepos()
    }

    fun loadRepos() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val repos = container.github.listRepos()
                _state.update { it.copy(loading = false, repos = repos) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun selectRepo(repo: GitHubRepo) {
        if (_state.value.validating) return
        _state.update { it.copy(validating = true, error = null) }
        viewModelScope.launch {
            try {
                val branches = container.github.listBranches(repo.owner?.login ?: repo.fullName.substringBefore('/'), repo.name)
                val names = branches.map { it.name }
                val default = repo.defaultBranch?.takeIf { it in names } ?: names.firstOrNull() ?: "main"
                if (names.size <= 1) {
                    validateAndSave(repo, default)
                } else {
                    _state.update {
                        it.copy(
                            validating = false,
                            branchDialogRepo = repo,
                            branchDialogBranches = names,
                            selectedBranch = default
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(validating = false, error = friendly(e)) }
            }
        }
    }

    fun dismissBranchDialog() = _state.update {
        it.copy(branchDialogRepo = null, branchDialogBranches = emptyList(), selectedBranch = "")
    }

    fun setBranch(branch: String) = _state.update { it.copy(selectedBranch = branch) }

    fun confirmBranch() {
        val repo = _state.value.branchDialogRepo ?: return
        val branch = _state.value.selectedBranch.ifBlank { repo.defaultBranch ?: "main" }
        _state.update { it.copy(branchDialogRepo = null) }
        validateAndSave(repo, branch)
    }

    fun openManualDialog() = _state.update { it.copy(manualDialog = true) }
    fun closeManualDialog() = _state.update { it.copy(manualDialog = false) }

    fun manualSave(owner: String, name: String, branch: String) {
        val cleanOwner = owner.trim().trimEnd('/').substringAfterLast('/')
        val cleanName = name.trim().removeSuffix(".git")
        if (cleanOwner.isBlank() || cleanName.isBlank()) {
            _state.update { it.copy(error = "请填写 owner 和仓库名") }
            return
        }
        _state.update { it.copy(manualDialog = false) }
        val synthetic = GitHubRepo(
            name = cleanName,
            fullName = "$cleanOwner/$cleanName",
            defaultBranch = branch.trim().ifBlank { "main" },
            owner = top.lvbyte.studio.data.model.GitHubOwner(cleanOwner)
        )
        validateAndSave(synthetic, branch.trim().ifBlank { "main" })
    }

    private fun validateAndSave(repo: GitHubRepo, branch: String) {
        _state.update { it.copy(validating = true, error = null) }
        viewModelScope.launch {
            try {
                val owner = repo.owner?.login ?: repo.fullName.substringBefore('/')
                val validation = container.github.validateHexo(owner, repo.name, branch)
                if (!validation.ok) {
                    _state.update { it.copy(validating = false, error = validation.message) }
                    return@launch
                }
                container.settings.setRepo(owner, repo.name, branch)
                _state.update {
                    it.copy(
                        validating = false,
                        saved = true,
                        savedRepo = "${repo.fullName}@$branch"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(validating = false, error = friendly(e)) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.secure.clearAll()
            container.settings.clearAccount()
        }
    }

    private fun friendly(e: Exception): String {
        if (e is ApiException) {
            return when (e.status) {
                401 -> "GitHub 授权已失效，请退出后重新登录"
                403 -> "Token 没有访问这个仓库的权限（私有库需要 repo / Contents 权限）"
                404 -> "仓库不存在，或 Token 看不到它"
                else -> e.message ?: "请求失败"
            }
        }
        return e.message ?: "网络异常"
    }
}
