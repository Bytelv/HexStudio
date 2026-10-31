package top.lvbyte.hexstudio.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.data.SettingsStore
import top.lvbyte.hexstudio.work.NotificationScheduler

data class SettingsState(
    val settings: SettingsStoreSnapshot? = null,
    val imagePassword: String = "",
    val sessionOk: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val testing: Boolean = false,
    val testResult: String? = null
)

data class SettingsStoreSnapshot(
    val githubLogin: String = "",
    val repoFullName: String = "",
    val repoBranch: String = "",
    val apiBase: String = "",
    val githubClientId: String = "",
    val imageEndpoint: String = "",
    val imageUser: String = "",
    val notificationsEnabled: Boolean = true,
    val useStellarImageTag: Boolean = false
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settings.settings.collect { s ->
                _state.update {
                    it.copy(
                        settings = SettingsStoreSnapshot(
                            githubLogin = s.githubLogin,
                            repoFullName = s.repoFullName,
                            repoBranch = s.repoBranch,
                            apiBase = s.apiBase,
                            githubClientId = s.githubClientId,
                            imageEndpoint = s.imageEndpoint,
                            imageUser = s.imageUser,
                            notificationsEnabled = s.notificationsEnabled,
                            useStellarImageTag = s.useStellarImageTag
                        ),
                        imagePassword = it.imagePassword.ifBlank { container.secure.imagePassword.orEmpty() },
                        sessionOk = container.secure.hasSession,
                        error = null
                    )
                }
            }
        }
    }

    fun saveService(apiBase: String, clientId: String) {
        viewModelScope.launch {
            container.settings.setApiBase(apiBase)
            container.settings.setGithubClientId(clientId)
            _state.update { it.copy(message = "服务配置已保存") }
        }
    }

    fun saveImage(endpoint: String, user: String, password: String) {
        viewModelScope.launch {
            container.settings.setImageBed(endpoint, user)
            container.secure.imagePassword = password
            _state.update { it.copy(imagePassword = password, message = "图床配置已保存") }
        }
    }

    fun testService() {
        if (_state.value.testing) return
        _state.update { it.copy(testing = true, message = null, error = null, testResult = null) }
        viewModelScope.launch {
            try {
                val ok = container.studio.health()
                _state.update {
                    it.copy(
                        testing = false,
                        message = if (ok) "Cloudflare 服务连接正常" else "服务返回异常"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(testing = false, error = e.message ?: "连接失败") }
            }
        }
    }

    fun testImage() {
        if (_state.value.testing) return
        _state.update { it.copy(testing = true, message = null, error = null, testResult = null) }
        viewModelScope.launch {
            try {
                val uploaded = container.imageBed.testUpload()
                _state.update {
                    it.copy(
                        testing = false,
                        testResult = uploaded.url,
                        message = "图床上传成功"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(testing = false, error = e.message ?: "上传失败") }
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setNotificationsEnabled(enabled)
            NotificationScheduler.schedule(container.appContext, enabled)
            _state.update { it.copy(message = if (enabled) "已开启通知轮询" else "已关闭通知轮询") }
        }
    }

    fun setUseStellarImageTag(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setUseStellarImageTag(enabled)
        }
    }

    fun clearDrafts() {
        viewModelScope.launch {
            val settings = container.settings.snapshot()
            container.drafts.clear(settings.repoFullName)
            _state.update { it.copy(message = "本地草稿已清除") }
        }
    }

    fun resetNotificationCursor() {
        viewModelScope.launch {
            container.settings.resetNotificationCursor()
            _state.update { it.copy(message = "通知游标已重置") }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            container.secure.clearAll()
            container.settings.clearAccount()
            onDone()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null, testResult = null) }
}
