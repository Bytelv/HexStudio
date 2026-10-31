package top.lvbyte.studio.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.lvbyte.studio.BuildConfig
import top.lvbyte.studio.core.AppContainer
import top.lvbyte.studio.ui.appViewModelFactory
import top.lvbyte.studio.ui.components.Avatar
import top.lvbyte.studio.ui.components.SectionCard
import top.lvbyte.studio.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onSwitchRepo: () -> Unit,
    onLogout: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = appViewModelFactory { SettingsViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var apiBase by rememberSaveable { mutableStateOf("") }
    var clientId by rememberSaveable { mutableStateOf("") }
    var imageEndpoint by rememberSaveable { mutableStateOf("") }
    var imageUser by rememberSaveable { mutableStateOf("") }
    var imagePassword by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var showLogout by rememberSaveable { mutableStateOf(false) }
    var showClearDrafts by rememberSaveable { mutableStateOf(false) }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsGranted = it }

    LaunchedEffect(state.settings) {
        val s = state.settings
        if (!initialized && s != null) {
            apiBase = s.apiBase
            clientId = s.githubClientId
            imageEndpoint = s.imageEndpoint
            imageUser = s.imageUser
            imagePassword = state.imagePassword
            initialized = true
        }
    }

    LaunchedEffect(state.message, state.error) {
        val message = state.message ?: state.error
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }
            item {
                SectionCard(title = "账号") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(null, state.settings?.githubLogin ?: "L", 46)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                state.settings?.githubLogin?.ifBlank { "未登录" } ?: "未登录",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                if (state.sessionOk) "GitHub + Cloudflare 会话正常" else "会话已失效，请重新登录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.sessionOk) StatusPill("在线") else StatusPill("离线", MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSwitchRepo) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                            Text(" 切换仓库")
                        }
                        TextButton(onClick = { showLogout = true }) {
                            Icon(Icons.Outlined.Logout, contentDescription = null)
                            Text(" 退出登录")
                        }
                    }
                }
            }
            item {
                SectionCard(title = "当前仓库") {
                    Text(
                        state.settings?.repoFullName?.ifBlank { "未选择" } ?: "未选择",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "分支 ${state.settings?.repoBranch?.ifBlank { "main" } ?: "main"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SectionCard(title = "图床") {
                    OutlinedTextField(
                        value = imageEndpoint,
                        onValueChange = { imageEndpoint = it },
                        label = { Text("上传接口") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = imageUser,
                        onValueChange = { imageUser = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = imagePassword,
                        onValueChange = { imagePassword = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.saveImage(imageEndpoint, imageUser, imagePassword) }) {
                            Text("保存")
                        }
                        OutlinedButton(enabled = !state.testing, onClick = vm::testImage) {
                            if (state.testing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("测试上传")
                            }
                        }
                    }
                    if (!state.testResult.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "测试成功：${state.testResult}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "图床会把 PNG 透明区域压成白底；批量上传按 Telegram 限制约 4 秒一张。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SectionCard(title = "Cloudflare 服务") {
                    OutlinedTextField(
                        value = apiBase,
                        onValueChange = { apiBase = it },
                        label = { Text("API 地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("GitHub OAuth Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.saveService(apiBase, clientId) }) { Text("保存") }
                        OutlinedButton(enabled = !state.testing, onClick = vm::testService) {
                            if (state.testing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Text(" 测试连接")
                            }
                        }
                    }
                    Text(
                        "服务端：Worker + D1，负责友链、说说、通知和朋友圈抓取。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SectionCard(title = "通知") {
                    SettingsSwitchRow(
                        title = "后台轮询通知",
                        subtitle = if (notificationsGranted) "每 15 分钟检查一次；打开 App 会立即同步" else "通知权限未授予，去系统设置里允许通知",
                        checked = state.settings?.notificationsEnabled ?: true,
                        onCheckedChange = vm::setNotificationsEnabled
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (notificationsGranted) "通知权限已授予" else "需要通知权限",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
                            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("去授权")
                            }
                        } else {
                            TextButton(onClick = { vm.resetNotificationCursor() }) { Text("重置游标") }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "编辑器") {
                    SettingsSwitchRow(
                        title = "图片使用 Stellar image 标签",
                        subtitle = "关闭时插入 Markdown 图片语法",
                        checked = state.settings?.useStellarImageTag ?: false,
                        onCheckedChange = vm::setUseStellarImageTag
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    TextButton(onClick = { showClearDrafts = true }) { Text("清除本地草稿") }
                }
            }
            item {
                SectionCard(title = "关于") {
                    Text("字节工作台 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "GitHub 仓库管理 · Stellar 标签 · 图床 · Cloudflare 说说/友链",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { uriHandler.openUri("https://blog.lvbyte.top") }) { Text("博客") }
                        TextButton(onClick = { uriHandler.openUri("https://github.com/Bytelv") }) { Text("GitHub") }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录？") },
            text = { Text("本机的 GitHub Token、Cloudflare 会话和仓库选择都会清除，草稿不会删除。") },
            confirmButton = {
                Button(onClick = {
                    showLogout = false
                    vm.logout(onDone = onLogout)
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("取消") } }
        )
    }
    if (showClearDrafts) {
        AlertDialog(
            onDismissRequest = { showClearDrafts = false },
            title = { Text("清除本地草稿？") },
            text = { Text("只会删除这台手机上保存的自动草稿，不影响 GitHub 上的文件。") },
            confirmButton = {
                Button(onClick = {
                    showClearDrafts = false
                    vm.clearDrafts()
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showClearDrafts = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
