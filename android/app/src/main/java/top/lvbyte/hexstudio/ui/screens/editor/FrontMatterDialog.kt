package top.lvbyte.hexstudio.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.lvbyte.hexstudio.util.TimeUtils

@Composable
fun FrontMatterDialog(
    initial: FrontMatterData,
    onSave: (FrontMatterData) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var updated by rememberSaveable { mutableStateOf(initial.updated) }
    var abbrlink by rememberSaveable { mutableStateOf(initial.abbrlink) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var cover by rememberSaveable { mutableStateOf(initial.cover) }
    var tags by rememberSaveable { mutableStateOf(initial.tags) }
    var categories by rememberSaveable { mutableStateOf(initial.categories) }
    var activeMenu by rememberSaveable { mutableStateOf(initial.activeMenu) }
    var error by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文章属性") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("title 标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("date 发布时间") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val now = TimeUtils.frontMatterNow()
                        date = now
                        if (updated.isBlank()) updated = now
                    }) { Text("填入当前时间") }
                }
                OutlinedTextField(
                    value = updated,
                    onValueChange = { updated = it },
                    label = { Text("updated 更新时间") },
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
                    value = activeMenu,
                    onValueChange = { activeMenu = it },
                    label = { Text("active_menu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "提示：Stellar v2 的 menu_id / poster 等旧字段已失效；未知字段会原样保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error.isNotBlank()) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) {
                    error = "title 不能为空"
                    return@Button
                }
                onSave(
                    FrontMatterData(
                        title = title,
                        date = date,
                        updated = updated,
                        abbrlink = abbrlink,
                        description = description,
                        cover = cover,
                        tags = tags,
                        categories = categories,
                        activeMenu = activeMenu.ifBlank { "post" }
                    )
                )
            }) { Text("保存属性") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
