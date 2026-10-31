package top.lvbyte.studio.ui.screens.friends

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
import top.lvbyte.studio.data.ApiException
import top.lvbyte.studio.data.SessionExpiredException
import top.lvbyte.studio.data.model.CreateFriendRequest
import top.lvbyte.studio.data.model.FeedStatus
import top.lvbyte.studio.data.model.Friend
import top.lvbyte.studio.data.model.FriendApplication
import top.lvbyte.studio.data.model.UpdateFriendRequest

data class FriendsState(
    val loading: Boolean = true,
    val friends: List<Friend> = emptyList(),
    val applications: List<FriendApplication> = emptyList(),
    val feedStatus: List<FeedStatus> = emptyList(),
    val unread: Int = 0,
    val error: String? = null,
    val busy: Boolean = false,
    val message: String? = null
)

class FriendsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val appsDeferred = async { container.studio.applications("pending") }
                    val friendsDeferred = async { container.studio.friends() }
                    val feedsDeferred = async { container.studio.feedStatus() }
                    val notificationsDeferred = async {
                        container.studio.notifications(unreadOnly = true, limit = 1)
                    }
                    val apps = appsDeferred.await()
                    val friends = friendsDeferred.await()
                    val feeds = feedsDeferred.await()
                    val notifications = notificationsDeferred.await()
                    _state.update {
                        it.copy(
                            loading = false,
                            applications = apps,
                            friends = friends,
                            feedStatus = feeds,
                            unread = notifications.unreadCount,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun approve(id: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                container.studio.approveApplication(id)
                _state.update { it.copy(busy = false, message = "已通过，友链已上线") }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    fun reject(id: Long, reason: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                container.studio.rejectApplication(id, reason)
                _state.update { it.copy(busy = false, message = "已拒绝") }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    fun addFriend(request: CreateFriendRequest) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                container.studio.createFriend(request)
                _state.update { it.copy(busy = false, message = "已添加友链") }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    fun updateFriend(id: Long, request: UpdateFriendRequest) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                container.studio.updateFriend(id, request)
                _state.update { it.copy(busy = false, message = "已更新友链") }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    fun deleteFriend(id: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                container.studio.deleteFriend(id)
                _state.update { it.copy(busy = false, message = "已删除友链") }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    fun refreshFeeds(friendId: Long? = null) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                container.studio.refreshFeeds(friendId, 3)
                _state.update { it.copy(busy = false, message = "已触发抓取") }
                load()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                container.studio.markAllNotificationsRead()
                _state.update { it.copy(unread = 0) }
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun friendly(e: Exception): String {
        if (e is SessionExpiredException) return "登录状态已失效，请到设置里重新登录"
        if (e is ApiException) {
            return when (e.status) {
                401 -> "登录状态已失效，请重新登录"
                403 -> "没有权限执行这个操作"
                else -> e.message ?: "请求失败"
            }
        }
        return e.message ?: "网络异常"
    }
}
