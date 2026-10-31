package top.lvbyte.studio.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.lvbyte.studio.core.AppContainer
import top.lvbyte.studio.data.ApiException
import top.lvbyte.studio.data.model.DeviceCodeResponse

data class LoginState(
    val apiBase: String = "",
    val clientId: String = "",
    val busy: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val device: DeviceCodeResponse? = null,
    val polling: Boolean = false,
    val authenticated: Boolean = false,
    val advancedOpen: Boolean = false,
    val configMessage: String? = null
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = container.settings.snapshot()
            _state.update { it.copy(apiBase = settings.apiBase, clientId = settings.githubClientId) }
        }
    }

    fun setAdvancedOpen(open: Boolean) = _state.update { it.copy(advancedOpen = open) }

    fun saveAdvanced(apiBase: String, clientId: String) {
        viewModelScope.launch {
            container.settings.setApiBase(apiBase)
            container.settings.setGithubClientId(clientId)
            val s = container.settings.snapshot()
            _state.update {
                it.copy(
                    apiBase = s.apiBase,
                    clientId = s.githubClientId,
                    configMessage = "已保存",
                    error = null
                )
            }
        }
    }

    fun startDeviceFlow() {
        if (_state.value.busy) return
        val clientId = _state.value.clientId.trim()
        if (clientId.isBlank()) {
            _state.update { it.copy(error = "请先在高级设置里填入 GitHub OAuth App 的 Client ID") }
            return
        }
        _state.update {
            it.copy(
                busy = true,
                error = null,
                status = "正在向 GitHub 申请设备码…",
                device = null,
                polling = false,
                authenticated = false
            )
        }
        viewModelScope.launch {
            try {
                val device = container.deviceFlow.requestDeviceCode(clientId)
                if (!device.error.isNullOrBlank()) {
                    throw ApiException(400, device.errorDescription ?: device.error)
                }
                if (device.deviceCode.isBlank() || device.userCode.isBlank()) {
                    throw ApiException(502, "GitHub 没有返回设备码")
                }
                _state.update {
                    it.copy(
                        busy = false,
                        device = device,
                        polling = true,
                        status = "等待你在 GitHub 上确认授权…"
                    )
                }
                startPolling(clientId, device)
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, polling = false, error = friendly(e), status = "")
                }
            }
        }
    }

    private fun startPolling(clientId: String, device: DeviceCodeResponse) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var interval = device.interval.coerceAtLeast(5)
            val deadline = System.currentTimeMillis() + device.expiresIn.coerceAtLeast(60) * 1000L
            while (isActive) {
                delay(interval * 1000L)
                if (System.currentTimeMillis() > deadline) {
                    _state.update { it.copy(polling = false, error = "设备码已过期，请重新登录") }
                    return@launch
                }
                val result = try {
                    container.deviceFlow.poll(clientId, device.deviceCode)
                } catch (e: Exception) {
                    _state.update { it.copy(polling = false, error = friendly(e)) }
                    return@launch
                }
                when (result.error) {
                    null, "" -> {
                        val token = result.accessToken
                        if (token.isNullOrBlank()) {
                            _state.update { it.copy(polling = false, error = "GitHub 没有返回 Token") }
                        } else {
                            finishLogin(token)
                        }
                        return@launch
                    }
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    "expired_token" -> {
                        _state.update { it.copy(polling = false, error = "设备码已过期，请重新登录") }
                        return@launch
                    }
                    "access_denied" -> {
                        _state.update { it.copy(polling = false, error = "你在 GitHub 上取消了授权") }
                        return@launch
                    }
                    else -> {
                        _state.update {
                            it.copy(polling = false, error = result.errorDescription ?: result.error)
                        }
                        return@launch
                    }
                }
            }
        }
    }

    fun loginWithPersonalToken(token: String) {
        if (token.isBlank()) {
            _state.update { it.copy(error = "Token 不能为空") }
            return
        }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, status = "正在验证 GitHub Token…") }
        viewModelScope.launch { finishLogin(token.trim()) }
    }

    private suspend fun finishLogin(githubToken: String) {
        try {
            val user = container.github.getUser(githubToken)
            _state.update { it.copy(status = "GitHub 账号 ${user.login}，正在连接 Cloudflare 服务…") }
            val session = container.studio.loginWithGithub(githubToken)
            container.secure.githubToken = githubToken
            container.secure.sessionToken = session.token
            container.settings.setGithubLogin(user.login)
            _state.update {
                it.copy(
                    busy = false,
                    polling = false,
                    status = "登录成功",
                    error = null,
                    authenticated = true
                )
            }
        } catch (e: Exception) {
            container.secure.clearSession()
            _state.update {
                it.copy(
                    busy = false,
                    polling = false,
                    status = "",
                    error = friendly(e)
                )
            }
        }
    }

    fun cancelDeviceFlow() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(device = null, polling = false, busy = false, status = "", error = null) }
    }

    private fun friendly(e: Exception): String {
        if (e is ApiException) {
            return when (e.status) {
                403 -> "这个 GitHub 账号不是本站管理员，无法登录工作台"
                401 -> "GitHub Token 无效或已过期"
                404 -> "接口不存在，请检查 API 地址"
                else -> e.message ?: "请求失败"
            }
        }
        val message = e.message ?: "网络异常"
        return if (message.contains("SSL", true) || message.contains("timeout", true) ||
            message.contains("Failed to connect", true) || message.contains("Unable to resolve host", true)
        ) {
            "网络连接失败，请检查代理和 API 地址"
        } else {
            message
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
