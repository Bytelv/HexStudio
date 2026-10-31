package top.lvbyte.studio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lvbyte.studio.core.AppContainer

enum class Gate { Loading, NeedLogin, NeedRepo, Ready }

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val _gate = MutableStateFlow(Gate.Loading)
    val gate: StateFlow<Gate> = _gate.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val settings = container.settings.snapshot()
            val next = when {
                !container.secure.hasGithubToken -> Gate.NeedLogin
                !container.secure.hasSession -> Gate.NeedLogin
                !settings.hasRepo -> Gate.NeedRepo
                else -> Gate.Ready
            }
            _gate.value = next
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.secure.clearAll()
            container.settings.clearAccount()
            _gate.value = Gate.NeedLogin
        }
    }
}
