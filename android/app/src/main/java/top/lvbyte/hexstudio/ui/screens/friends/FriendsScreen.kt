package top.lvbyte.hexstudio.ui.screens.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.data.model.CreateFriendRequest
import top.lvbyte.hexstudio.data.model.FeedStatus
import top.lvbyte.hexstudio.data.model.Friend
import top.lvbyte.hexstudio.data.model.FriendApplication
import top.lvbyte.hexstudio.data.model.UpdateFriendRequest
import top.lvbyte.hexstudio.ui.appViewModelFactory
import top.lvbyte.hexstudio.ui.components.AppLoading
import top.lvbyte.hexstudio.ui.components.Avatar
import top.lvbyte.hexstudio.ui.components.EmptyState
import top.lvbyte.hexstudio.ui.components.ErrorCard
import top.lvbyte.hexstudio.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(container: AppContainer) {
    val vm: FriendsViewModel = viewModel(factory = appViewModelFactory { FriendsViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Friend?>(null) }
    var rejecting by remember { mutableStateOf<FriendApplication?>(null) }
    var rejectReason by rememberSaveable { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Friend?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!state.loading) vm.load()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("友链") },
                actions = {
                    BadgedBox(
                        badge = {
                            if (state.unread > 0) Badge { Text(state.unread.toString()) }
                        }
                    ) {
                        IconButton(onClick = { tab = 0 }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "通知")
                        }
                    }
                    IconButton(onClick = vm::load) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEdit = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "添加友链")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("待审核 ${state.applications.size}") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("友链 ${state.friends.size}") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("抓取") })
            }
            if (state.loading && state.friends.isEmpty() && state.applications.isEmpty()) {
                AppLoading("读取友链…")
                return@Column
            }
            if (!state.error.isNullOrBlank() && state.friends.isEmpty()) {
                ErrorCard(
                    state.error ?: "",
                    modifier = Modifier.padding(16.dp),
                    onRetry = vm::load
                )
                return@Column
            }
            if (state.unread > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.13f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${state.unread} 条未读通知",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = vm::markAllRead) { Text("全部已读") }
                    }
                }
            }
            when (tab) {
                0 -> ApplicationsTab(
                    applications = state.applications,
                    busy = state.busy,
                    onApprove = vm::approve,
                    onReject = {
                        rejecting = it
                        rejectReason = ""
                    }
                )
                1 -> FriendsTab(
                    friends = state.friends,
                    onEdit = {
                        editing = it
                        showEdit = true
                    }
                )
                else -> FeedTab(
                    feeds = state.feedStatus,
                    busy = state.busy,
                    onRefreshAll = { vm.refreshFeeds(null) },
                    onRefreshOne = { vm.refreshFeeds(it) }
                )
            }
        }
    }

    if (showEdit) {
        FriendEditDialog(
            initial = editing,
            busy = state.busy,
            onDismiss = { if (!state.busy) showEdit = false },
            onDelete = editing?.let { friend ->
                { toDelete = friend; showEdit = false }
            },
            onSave = { title, url, avatar, description, keywords, feedUrl, feedEnabled, visible ->
                val friend = editing
                if (friend == null) {
                    vm.addFriend(
                        CreateFriendRequest(
                            title = title,
                            url = url,
                            avatar = avatar,
                            description = description,
                            keywords = keywords,
                            feedUrl = feedUrl.ifBlank { null },
                            feedEnabled = feedEnabled,
                            visible = visible
                        )
                    )
                } else {
                    vm.updateFriend(
                        friend.id,
                        UpdateFriendRequest(
                            title = title,
                            url = url,
                            avatar = avatar,
                            description = description,
                            keywords = keywords,
                            feedUrl = feedUrl,
                            feedEnabled = feedEnabled,
                            visible = visible
                        )
                    )
                }
                showEdit = false
            }
        )
    }

    rejecting?.let { application ->
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("拒绝友链申请") },
            text = {
                Column {
                    Text("${application.name}\n${application.url}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("原因（可选，仅自己可见）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val id = application.id
                    rejecting = null
                    vm.reject(id, rejectReason)
                }) { Text("拒绝") }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("取消") } }
        )
    }

    toDelete?.let { friend ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除友链？") },
            text = { Text("${friend.title}\n${friend.url}") },
            confirmButton = {
                Button(onClick = {
                    val id = friend.id
                    toDelete = null
                    vm.deleteFriend(id)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ApplicationsTab(
    applications: List<FriendApplication>,
    busy: Boolean,
    onApprove: (Long) -> Unit,
    onReject: (FriendApplication) -> Unit
) {
    if (applications.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Check,
            title = "没有待审核的申请",
            description = "博客友链页提交的申请会出现在这里。"
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(applications, key = { it.id }) { application ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(application.avatar, application.name, 42)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(application.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                application.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPill("待审核", MaterialTheme.colorScheme.tertiary)
                    }
                    if (application.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(application.description, style = MaterialTheme.typography.bodySmall)
                    }
                    if (application.message.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "留言：${application.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (application.email.isNotBlank()) {
                        Text(
                            "邮箱：${application.email}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        application.created,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { onApprove(application.id) }) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" 通过")
                        }
                        OutlinedButton(enabled = !busy, onClick = { onReject(application) }) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" 拒绝")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsTab(friends: List<Friend>, onEdit: (Friend) -> Unit) {
    if (friends.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.RssFeed,
            title = "友链还是空的",
            description = "点右下角添加，或等待友链申请通过。"
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(friends, key = { it.id }) { friend ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(friend) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(friend.avatar, friend.title, 42)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                friend.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!friend.visible) {
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                StatusPill("隐藏", MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            friend.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!friend.lastError.isNullOrBlank()) {
                            Text(
                                "抓取失败：${friend.lastError}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedTab(
    feeds: List<FeedStatus>,
    busy: Boolean,
    onRefreshAll: () -> Unit,
    onRefreshOne: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("朋友圈抓取", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Cloudflare Cron 每分钟抓取一个友链源，输出给 /friend/rss/。失败会自动退避，可在这里手动触发。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(enabled = !busy, onClick = onRefreshAll) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Text(" 立即抓取 3 个源")
                    }
                }
            }
        }
        items(feeds, key = { it.id }) { feed ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(feed.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (!feed.lastError.isNullOrBlank()) {
                            StatusPill("异常", MaterialTheme.colorScheme.error)
                        } else if (feed.lastFetched != null) {
                            StatusPill("正常")
                        }
                        IconButton(onClick = { onRefreshOne(feed.id) }, enabled = !busy) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                    Text(
                        feed.feedUrl ?: "尚未发现订阅地址",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!feed.lastError.isNullOrBlank()) {
                        Text(
                            feed.lastError ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!feed.lastFetched.isNullOrBlank()) {
                        Text(
                            "上次抓取：${feed.lastFetched}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendEditDialog(
    initial: Friend?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, String, String, String, String, String, Boolean, Boolean) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var url by rememberSaveable { mutableStateOf(initial?.url ?: "") }
    var avatar by rememberSaveable { mutableStateOf(initial?.avatar ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var keywords by rememberSaveable { mutableStateOf(initial?.keywords ?: "") }
    var feedUrl by rememberSaveable { mutableStateOf(initial?.feedUrl ?: "") }
    var feedEnabled by rememberSaveable { mutableStateOf(initial?.feedEnabled ?: true) }
    var visible by rememberSaveable { mutableStateOf(initial?.visible ?: true) }
    var error by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加友链" else "编辑友链") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("网站名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("网址") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = avatar, onValueChange = { avatar = it },
                    label = { Text("头像 URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("简介") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywords, onValueChange = { keywords = it },
                    label = { Text("关键词") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = feedUrl, onValueChange = { feedUrl = it },
                    label = { Text("RSS/Atom 地址（留空自动发现）") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = feedEnabled, onCheckedChange = { feedEnabled = it })
                    Text("参与朋友圈抓取", modifier = Modifier.padding(start = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = visible, onCheckedChange = { visible = it })
                    Text("在友链页展示", modifier = Modifier.padding(start = 8.dp))
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (initial != null && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text(" 删除这条友链")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    if (title.isBlank() || url.isBlank()) {
                        error = "名称和网址不能为空"
                    } else {
                        onSave(title.trim(), url.trim(), avatar.trim(), description.trim(), keywords.trim(), feedUrl.trim(), feedEnabled, visible)
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }
    )
}
