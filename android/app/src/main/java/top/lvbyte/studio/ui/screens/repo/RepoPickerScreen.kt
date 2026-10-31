package top.lvbyte.studio.ui.screens.repo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.lvbyte.studio.core.AppContainer
import top.lvbyte.studio.data.model.GitHubRepo
import top.lvbyte.studio.ui.appViewModelFactory
import top.lvbyte.studio.ui.components.AppLoading
import top.lvbyte.studio.ui.components.EmptyState
import top.lvbyte.studio.ui.components.ErrorCard
import top.lvbyte.studio.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoPickerScreen(
    container: AppContainer,
    isSwitch: Boolean = false,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
    onLogout: () -> Unit
) {
    val vm: RepoPickerViewModel = viewModel(factory = appViewModelFactory { RepoPickerViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var manualOwner by rememberSaveable { mutableStateOf("") }
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualBranch by rememberSaveable { mutableStateOf("main") }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSwitch) "切换仓库" else "选择博客仓库") },
                navigationIcon = {
                    if (isSwitch && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::loadRepos) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    if (!isSwitch) {
                        TextButton(onClick = {
                            vm.logout()
                            onLogout()
                        }) { Text("退出登录") }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                if (state.githubLogin.isBlank()) "选择一个 Hexo 源码仓库" else "已登录 ${state.githubLogin}，请选择 Hexo 源码仓库",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("搜索仓库") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (state.validating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("正在检查仓库结构…", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (!state.error.isNullOrBlank()) {
                ErrorCard(state.error ?: "", onRetry = vm::loadRepos)
                Spacer(Modifier.height(10.dp))
            }

            if (state.loading && state.repos.isEmpty()) {
                AppLoading("正在读取仓库列表…")
            } else if (state.repos.isEmpty() && !state.loading) {
                EmptyState(
                    icon = Icons.Outlined.Public,
                    title = "没有找到仓库",
                    description = "如果仓库是私有的，请确认 Token 有 repo / Contents 权限。"
                )
            } else {
                val filtered = state.repos.filter {
                    state.query.isBlank() ||
                        it.fullName.contains(state.query, ignoreCase = true) ||
                        (it.description ?: "").contains(state.query, ignoreCase = true)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.fullName }) { repo ->
                        RepoRow(repo = repo, onClick = { vm.selectRepo(repo) })
                    }
                    item {
                        OutlinedButton(
                            onClick = vm::openManualDialog,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text(" 手动输入 owner/repo")
                        }
                    }
                }
            }
        }
    }

    val branchRepo = state.branchDialogRepo
    if (branchRepo != null) {
        AlertDialog(
            onDismissRequest = vm::dismissBranchDialog,
            title = { Text("选择分支") },
            text = {
                Column {
                    Text(branchRepo.fullName, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(state.branchDialogBranches) { branch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.setBranch(branch) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = state.selectedBranch == branch,
                                    onClick = { vm.setBranch(branch) }
                                )
                                Spacer(Modifier.height(0.dp))
                                Text(branch, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = vm::confirmBranch) { Text("使用这个分支") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissBranchDialog) { Text("取消") }
            }
        )
    }

    if (state.manualDialog) {
        AlertDialog(
            onDismissRequest = vm::closeManualDialog,
            title = { Text("手动输入仓库") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualOwner,
                        onValueChange = { manualOwner = it },
                        label = { Text("Owner（用户名/组织）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("仓库名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualBranch,
                        onValueChange = { manualBranch = it },
                        label = { Text("分支") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.manualSave(manualOwner, manualName, manualBranch) }) {
                    Text("检查并使用")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::closeManualDialog) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RepoRow(repo: GitHubRepo, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    if (repo.private) Icons.Outlined.Lock else Icons.Outlined.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repo.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (repo.private) {
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        StatusPill("私有")
                    }
                }
                val desc = repo.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Text(
                    "默认分支 ${repo.defaultBranch ?: "main"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!repo.defaultBranch.isNullOrBlank()) {
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
