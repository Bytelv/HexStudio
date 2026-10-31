package top.lvbyte.hexstudio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import top.lvbyte.hexstudio.BuildConfig
import top.lvbyte.hexstudio.ui.UpdateUiState

@Composable
fun UpdateHost(
    state: UpdateUiState,
    onDismissResult: () -> Unit,
    onDismissUpdate: () -> Unit,
    onRetry: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    state.info?.let { info ->
        AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text("发现新版本 ${info.versionName}") },
            text = {
                Column {
                    Text(
                        "当前版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (info.changelog.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            info.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { uriHandler.openUri(info.downloadUrl) }
                    onDismissUpdate()
                }) { Text("下载更新") }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdate) { Text("稍后") }
            }
        )
    }

    if (state.upToDate) {
        AlertDialog(
            onDismissRequest = onDismissResult,
            title = { Text("已是最新版本") },
            text = { Text("HexStudio v${BuildConfig.VERSION_NAME} 已是最新。") },
            confirmButton = {
                TextButton(onClick = onDismissResult) { Text("知道了") }
            }
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissResult,
            title = { Text("检查更新失败") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = onRetry) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = onDismissResult) { Text("关闭") }
            }
        )
    }
}
