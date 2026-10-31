package top.lvbyte.hexstudio.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
        if (!state.loading) vm.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("首页") },
                actions = {
                    IconButton(onClick = vm::load, enabled = !state.loading) {
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
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                item { ErrorCard(state.error ?: "", onRetry = vm::load) }
            }
            if (!state.serviceError.isNullOrBlank()) {
                item { ErrorCard(state.serviceError ?: "", onRetry = vm::load) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "文章",
                        value = state.postCount.toString(),
                        icon = Icons.Outlined.Article,
                        onClick = { onOpenContent(false) }
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "页面",
                        value = state.pageCount.toString(),
                        icon = Icons.Outlined.Description,
                        onClick = { onOpenContent(false) }
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "待审核友链",
                        value = (state.stats?.pendingApplications ?: 0).toString(),
                        icon = Icons.Outlined.Link,
                        accent = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenFriends
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "未读通知",
                        value = (state.stats?.unreadNotifications ?: 0).toString(),
                        icon = Icons.Outlined.NotificationsNone,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenFriends
                    )
                }
            }
            item {
                SectionCard(title = "快捷操作") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        MiniStat(Modifier.weight(1f), "朋友圈文章", stats.articles.toString())
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
                        "文章保存后直接提交到 GitHub，Cloudflare Pages 会自动构建并上线；说说和友链走 Cloudflare Worker + D1，无需等待构建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill("GitHub")
                        StatusPill("Cloudflare Pages", MaterialTheme.colorScheme.secondary)
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
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(url = avatarUrl, name = login.ifBlank { "L" }, size = 46)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            "$greeting，${login.ifBlank { "站长" }}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (repo.isBlank()) "还没有选择仓库" else repo,
                            color = Color.White.copy(alpha = 0.86f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = "切换仓库",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(onClick = onSwitchRepo)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "分支 $branch · 在手机上继续折腾博客",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
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
