package top.lvbyte.hexstudio.ui.screens.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.ui.appViewModelFactory
import top.lvbyte.hexstudio.ui.components.ErrorCard

@Composable
fun LoginScreen(
    container: AppContainer,
    onAuthenticated: () -> Unit
) {
    val vm: LoginViewModel = viewModel(factory = appViewModelFactory { LoginViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var patDialog by rememberSaveable { mutableStateOf(false) }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var tokenText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.authenticated) {
        if (state.authenticated) onAuthenticated()
    }

    DisposableEffect(Unit) {
        onDispose { vm.cancelDeviceFlow() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 54.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(Modifier.height(18.dp))
            Text(
                "HexStudio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Hexo · Stellar · 说说 · 友链",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(26.dp))
            Text(
                "把博客装进口袋",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "登录 GitHub 后选择 Hexo 仓库，就能在手机上写文章、传图片、发说说、审批友链。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    if (state.device != null) {
                        Text("在 GitHub 输入下面的代码", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "打开 GitHub 的设备授权页，登录后输入代码即可。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    state.device?.userCode.orEmpty(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 30.sp,
                                    letterSpacing = 4.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.size(10.dp))
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("GitHub code", state.device?.userCode.orEmpty())
                                    )
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "复制")
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val url = state.device?.verificationUri ?: "https://github.com/login/device"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" 打开 GitHub")
                            }
                            OutlinedButton(onClick = vm::cancelDeviceFlow) {
                                Text("取消")
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.polling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(10.dp))
                            }
                            Text(
                                state.status.ifBlank { "等待授权…" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("使用 GitHub 登录", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "通过 GitHub Device Flow 授权，App 不会接触你的账号密码。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = vm::startDeviceFlow,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(if (state.busy) "正在准备…" else "使用 GitHub 登录")
                        }
                        if (state.clientId.isBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "还没有配置 OAuth Client ID：可以先在下方「高级设置」填写，或使用 Token 登录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { patDialog = true },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("使用 GitHub Token 登录（备用）")
                        }
                    }

                    if (!state.error.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        ErrorCard(state.error ?: "")
                    }
                    if (!state.status.isNullOrBlank() && state.device == null && !state.busy) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            state.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = { vm.setAdvancedOpen(!state.advancedOpen) }) {
                Text(if (state.advancedOpen) "收起高级设置" else "高级设置")
            }
            AnimatedVisibility(visible = state.advancedOpen) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("OAuth App / API", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = state.apiBase,
                            onValueChange = { newValue ->
                                vm.saveAdvanced(newValue, state.clientId)
                            },
                            label = { Text("Cloudflare API 地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.clientId,
                            onValueChange = { newValue ->
                                vm.saveAdvanced(state.apiBase, newValue)
                            },
                            label = { Text("GitHub OAuth Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "在 GitHub → Settings → Developer settings → OAuth Apps 新建应用，勾选 Enable Device Flow，然后把 Client ID 填到这里。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "打开 GitHub 创建 OAuth App",
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/applications/new"))
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!state.configMessage.isNullOrBlank()) {
                            Text(
                                state.configMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Token 只会加密保存在本机；图床密码同理。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (patDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { patDialog = false },
            title = { Text("使用 GitHub Token") },
            text = {
                Column {
                    Text(
                        "建议创建一个 fine-grained token，至少勾选 Contents (Read and write) 和 Metadata (Read)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        label = { Text("Token") },
                        singleLine = true,
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    if (tokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (tokenVisible) "隐藏" else "显示"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.loginWithPersonalToken(tokenText)
                    patDialog = false
                }) { Text("登录") }
            },
            dismissButton = {
                TextButton(onClick = { patDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "L",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black
        )
    }
}
