package top.lvbyte.hexstudio.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.data.model.StudioStats
import top.lvbyte.hexstudio.ui.appViewModelFactory
import top.lvbyte.hexstudio.ui.components.AppLoading
import top.lvbyte.hexstudio.ui.components.Avatar
import top.lvbyte.hexstudio.ui.components.ErrorCard
import top.lvbyte.hexstudio.ui.components.SectionCard
import top.lvbyte.hexstudio.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenContent: (Boolean) -> Unit,
    onOpenTalks: () -> Unit,
    onOpenFriends: () -> Unit,
    onSwitchRepo: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = appViewModelFactory { HomeViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HexStudio", style = MaterialTheme.typography.titleLarge)
                        Text(
                            state.repoFullName.ifBlank { "博客工作台" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(force = true) }, enabled = !state.loading) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading && state.repoFullName.isBlank()) {
            AppLoading("正在读取仓库…")
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }
            item {
                HeroCard(
                    greeting = vm.greeting(),
                    login = state.login,
                    avatarUrl = state.avatarUrl,
                    repo = state.repoFullName,
                    branch = state.branch,
                    onSwitchRepo = onSwitchRepo
                )
            }
            if (!state.error.isNullOrBlank()) {
                item { ErrorCard(state.error ?: "", onRetry = { vm.load(force = true) }) }
            }
            if (!state.serviceError.isNullOrBlank()) {
                item { ErrorCard(state.serviceError ?: "", onRetry = { vm.load(force = true) }) }
            }
            item {
                OverviewStrip(
                    posts = state.postCount,
                    pages = state.pageCount,
                    pending = state.stats?.pendingApplications ?: 0,
                    unread = state.stats?.unreadNotifications ?: 0,
                    onOpenContent = { onOpenContent(false) },
                    onOpenFriends = onOpenFriends
                )
            }
            item {
                SectionCard(title = "快捷操作") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        QuickAction(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.AddCircleOutline,
                            label = "写文章",
                            onClick = { onOpenContent(true) }
                        )
                        QuickAction(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.ChatBubbleOutline,
                            label = "发说说",
                            onClick = onOpenTalks
                        )
                        QuickAction(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Link,
                            label = "审友链",
                            onClick = onOpenFriends
                        )
                        QuickAction(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.CloudDownload,
                            label = "导入",
                            onClick = onOpenTalks
                        )
                    }
                }
            }
            item {
                val stats = state.stats ?: StudioStats()
                SectionCard(title = "站点数据") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat(Modifier.weight(1f), "友链", stats.friends.toString())
                        MiniStat(Modifier.weight(1f), "说说", stats.talks.toString())
                        MiniStat(Modifier.weight(1f), "朋友圈", stats.articles.toString())
                        MiniStat(
                            Modifier.weight(1f),
                            "抓取异常",
                            stats.feedErrors.toString(),
                            if (stats.feedErrors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            item {
                SectionCard(title = "发布链路") {
                    Text(
                        "文章保存后提交到 GitHub，Cloudflare Pages 自动构建；说说和友链走 Worker + D1，即时生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill("GitHub")
                        StatusPill("Pages", MaterialTheme.colorScheme.secondary)
                        StatusPill("Worker + D1", MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun HeroCard(
    greeting: String,
    login: String,
    avatarUrl: String?,
    repo: String,
    branch: String,
    onSwitchRepo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                        .padding(2.dp)
                ) {
                    Avatar(url = avatarUrl, name = login.ifBlank { "L" }, size = 44)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        "$greeting，${login.ifBlank { "站长" }}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (repo.isBlank()) "还没有选择仓库" else repo,
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onSwitchRepo),
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.SwapHoriz,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("切换", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "分支 $branch",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun OverviewStrip(
    posts: Int,
    pages: Int,
    pending: Int,
    unread: Int,
    onOpenContent: () -> Unit,
    onOpenFriends: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.large
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverviewItem(
                modifier = Modifier.weight(1f),
                label = "文章",
                value = posts.toString(),
                onClick = onOpenContent
            )
            OverviewDivider()
            OverviewItem(
                modifier = Modifier.weight(1f),
                label = "页面",
                value = pages.toString(),
                onClick = onOpenContent
            )
            OverviewDivider()
            OverviewItem(
                modifier = Modifier.weight(1f),
                label = "待审",
                value = pending.toString(),
                accent = if (pending > 0) MaterialTheme.colorScheme.tertiary else null,
                onClick = onOpenFriends
            )
            OverviewDivider()
            OverviewItem(
                modifier = Modifier.weight(1f),
                label = "未读",
                value = unread.toString(),
                accent = if (unread > 0) MaterialTheme.colorScheme.secondary else null,
                onClick = onOpenFriends
            )
        }
    }
}

@Composable
private fun OverviewItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OverviewDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun MiniStat(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
