package top.lvbyte.hexstudio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.data.ApiException
import top.lvbyte.hexstudio.data.UpdateInfo

data class UpdateUiState(
    val checking: Boolean = false,
    val info: UpdateInfo? = null,
    val upToDate: Boolean = false,
    val error: String? = null
)

class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var autoStarted = false
    private var dismissedVersion = 0

    fun autoCheck() {
        if (autoStarted) return
        autoStarted = true
        if (!container.updates.shouldAutoCheck()) return
        perform(manual = false)
    }

    fun check() = perform(manual = true)

    fun retry() = perform(manual = true)

    fun dismissResult() = _state.update {
        it.copy(info = null, upToDate = false, error = null)
    }

    fun dismissUpdate() {
        _state.value.info?.let { dismissedVersion = it.versionCode }
        dismissResult()
    }

    private fun perform(manual: Boolean) {
        if (_state.value.checking) return
        _state.update {
            it.copy(checking = true, info = null, upToDate = false, error = null)
        }
        viewModelScope.launch {
            try {
                val info = container.updates.fetch()
                container.updates.markChecked()
                val newer = container.updates.isNewer(info) && info.versionCode != dismissedVersion
                _state.update {
                    it.copy(
                        checking = false,
                        info = if (newer) info else null,
                        upToDate = !newer && manual
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        checking = false,
                        error = if (manual) friendly(e) else null
                    )
                }
            }
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is ApiException -> e.message ?: "检查更新失败"
        else -> e.message ?: "网络异常，请稍后再试"
    }
}
