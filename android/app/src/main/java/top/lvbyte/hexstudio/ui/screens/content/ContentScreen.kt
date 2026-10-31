package top.lvbyte.hexstudio.ui.screens.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.ui.appViewModelFactory
import top.lvbyte.hexstudio.ui.components.AppLoading
import top.lvbyte.hexstudio.ui.components.EmptyState
import top.lvbyte.hexstudio.ui.components.ErrorCard
import top.lvbyte.hexstudio.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    container: AppContainer,
    startNew: Boolean,
    onOpenEditor: (String) -> Unit
) {
    val vm: ContentViewModel = viewModel(factory = appViewModelFactory { ContentViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var filename by rememberSaveable { mutableStateOf("") }
    var filenameTouched by rememberSaveable { mutableStateOf(false) }
    var abbrlink by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var categories by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var cover by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(startNew) {
        if (startNew) showNew = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.load()
    }

    LaunchedEffect(state.createdPath) {
        val path = state.createdPath
        if (!path.isNullOrBlank()) {
            showNew = false
            onOpenEditor(path)
            vm.clearCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("内容")
                        Text(
                            "${state.repoName} · ${state.branch}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(force = true) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showNew = true
                title = ""
                filename = ""
                filenameTouched = false
                abbrlink = "post-" + System.currentTimeMillis().toString(36)
                tags = ""
                categories = ""
                description = ""
                cover = ""
                vm.clearCreateError()
            }) {
                Icon(Icons.Filled.Add, contentDescription = "新建文章")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("搜索路径或标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ContentKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.filter == kind,
                        onClick = { vm.setFilter(if (state.filter == kind) null else kind) },
                        label = { Text(kind.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.loading && state.items.isEmpty()) {
                AppLoading("读取仓库文件树…")
            } else if (!state.error.isNullOrBlank() && state.items.isEmpty()) {
                ErrorCard(state.error ?: "", onRetry = { vm.load(force = true) })
            } else {
                val query = state.query.trim()
                val filtered = remember(state.items, state.filter, query) {
                    state.items.filter { item ->
                        (state.filter == null || item.kind == state.filter) &&
                            (query.isBlank() ||
                                item.name.contains(query, ignoreCase = true) ||
                                item.path.contains(query, ignoreCase = true))
                    }
                }
                if (filtered.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Description,
                        title = "没有匹配的内容",
                        description = "可以调整搜索条件，或点右下角新建文章。"
                    )
                } else {
                    val groups = remember(filtered) { filtered.groupBy { it.kind } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        ContentKind.entries.forEach { kind ->
                            val list = groups[kind] ?: return@forEach
                            item(key = "header-${kind.name}") {
                                Text(
                                    "${kind.label} · ${list.size}",
                                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(list, key = { it.path }) { item ->
                                ContentRow(item) { onOpenEditor(item.path) }
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { if (!state.creating) showNew = false },
            title = { Text("新建文章") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            if (!filenameTouched) {
                                filename = it.replace(Regex("[\\\\/:*?\"<>|]"), "-")
                            }
                        },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = filename,
                        onValueChange = {
                            filename = it
                            filenameTouched = true
                        },
                        label = { Text("文件名（自动生成，可改）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = abbrlink,
                        onValueChange = { abbrlink = it },
                        label = { Text("abbrlink 永久链接") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("tags（逗号分隔）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = categories,
                        onValueChange = { categories = it },
                        label = { Text("categories（逗号分隔）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("description 摘要") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cover,
                        onValueChange = { cover = it },
                        label = { Text("cover 封面 URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!state.createError.isNullOrBlank()) {
                        Text(
                            state.createError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "文件会提交到 source/_posts/，Cloudflare Pages 自动构建。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !state.creating,
                    onClick = {
                        vm.createPost(title, filename, abbrlink, tags, categories, description, cover)
                    }
                ) {
                    if (state.creating) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("创建并编辑")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNew = false }, enabled = !state.creating) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ContentRow(item: ContentItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val icon = when (item.kind) {
                ContentKind.POST -> Icons.AutoMirrored.Outlined.Article
                ContentKind.PAGE -> Icons.Outlined.Description
                ContentKind.CONFIG -> Icons.Outlined.Settings
                ContentKind.DRAFT -> Icons.Outlined.StickyNote2
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(item.kind.label)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
