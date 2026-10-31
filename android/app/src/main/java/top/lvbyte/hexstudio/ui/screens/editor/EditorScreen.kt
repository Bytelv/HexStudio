package top.lvbyte.hexstudio.ui.screens.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.ui.appViewModelFactory
import top.lvbyte.hexstudio.ui.components.ErrorCard
import top.lvbyte.hexstudio.ui.components.StatusPill
import top.lvbyte.hexstudio.ui.components.TagInsertSheet
import top.lvbyte.hexstudio.util.StellarTags
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    container: AppContainer,
    path: String,
    onBack: () -> Unit
) {
    val vm: EditorViewModel = viewModel(
        key = "editor:$path",
        factory = appViewModelFactory { EditorViewModel(container, path) }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var initialized by remember { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showFrontMatter by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var issues by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSave by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var commitMessage by rememberSaveable { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) vm.uploadImages(uris)
    }

    fun insertTemplate(template: String) {
        val cursorMarker = template.indexOf(StellarTags.CURSOR)
        val clean = template.replace(StellarTags.CURSOR, "")
        val selection = editorValue.selection
        val start = min(selection.start, selection.end)
        val end = max(selection.start, selection.end)
        val next = editorValue.text.replaceRange(start, end, clean)
        val cursor = if (cursorMarker >= 0) start + cursorMarker else start + clean.length
        editorValue = TextFieldValue(next, TextRange(cursor.coerceIn(0, next.length)))
        vm.setBody(next)
    }

    LaunchedEffect(state.loading, state.sha) {
        if (!state.loading && !initialized) {
            editorValue = TextFieldValue(state.body)
            initialized = true
        }
    }

    LaunchedEffect(editorValue.text, state.dirty) {
        if (initialized && state.dirty) {
            delay(1500)
            vm.saveDraft()
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is EditorEvent.Insert -> insertTemplate(event.text)
                is EditorEvent.Replace -> {
                    editorValue = TextFieldValue(event.text)
                    initialized = true
                    vm.setBody(event.text)
                }
                is EditorEvent.Toast -> snackbar.showSnackbar(event.text)
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }

    BackHandler {
        if (state.dirty) showDiscard = true else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (state.dirty) showDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(state.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            kindLabel(state.kind) + if (state.branch.isNotBlank()) " · ${state.branch}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        issues = vm.validate()
                        showValidation = true
                    }) {
                        Icon(Icons.Outlined.Checklist, contentDescription = "检查标签")
                    }
                    if (!state.isConfig) {
                        IconButton(onClick = { showPreview = true }) {
                            Icon(Icons.Outlined.Visibility, contentDescription = "预览")
                        }
                    }
                    IconButton(
                        enabled = !state.saving,
                        onClick = {
                            commitMessage = vm.defaultCommitMessage()
                            showSave = true
                        }
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.padding(10.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Save, contentDescription = "保存")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize()) {
                    top.lvbyte.hexstudio.ui.components.AppLoading("读取文件…")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                        .imePadding()
                ) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(kindLabel(state.kind))
                        if (state.dirty) StatusPill("未提交", MaterialTheme.colorScheme.tertiary)
                        Text(
                            state.path,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${editorValue.text.length} 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!state.error.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        ErrorCard(state.error ?: "", onRetry = { vm.load() })
                    }
                    if (!state.draftFound.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "发现本地草稿",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = vm::restoreDraft) { Text("恢复") }
                                TextButton(onClick = vm::discardDraft) { Text("忽略") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.medium
                            ),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                if (editorValue.text.isEmpty()) {
                                    Text(
                                        "开始写正文…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    )
                                }
                                BasicTextField(
                                    value = editorValue,
                                    onValueChange = { value ->
                                        if (initialized) {
                                            editorValue = value
                                            vm.setBody(value.text)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    visualTransformation = MarkdownHighlightTransformation(
                                        primary = MaterialTheme.colorScheme.primary,
                                        secondary = MaterialTheme.colorScheme.secondary,
                                        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                                        muted = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        autoCorrectEnabled = false
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (state.sha.isNullOrBlank()) "新文件" else "已同步 ${state.sha?.take(7)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (state.dirty) "有未提交修改" else "已保存",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.dirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (state.uploading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            state.uploadLabel + if (state.uploadCount > 0) "（${state.uploadDone}/${state.uploadCount}）" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (state.uploadLabel.isNotBlank()) {
                        Text(
                            state.uploadLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!state.isConfig) {
                            TextButton(onClick = { showFrontMatter = true }) {
                                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.height(18.dp))
                                Text(" 属性")
                            }
                            TextButton(onClick = { showTags = true }) {
                                Icon(Icons.Outlined.Sell, contentDescription = null, modifier = Modifier.height(18.dp))
                                Text(" 标签")
                            }
                            TextButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !state.uploading
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.height(18.dp))
                                Text(" 图片")
                            }
                        }
                        TextButton(onClick = {
                            issues = vm.validate()
                            showValidation = true
                        }) {
                            Icon(Icons.Outlined.Checklist, contentDescription = null, modifier = Modifier.height(18.dp))
                            Text(" 检查")
                        }
                    }
                }
            }
        }
    }

    if (showTags) {
        TagInsertSheet(
            onSelect = { insertTemplate(it.template) },
            onDismiss = { showTags = false }
        )
    }
    if (showFrontMatter) {
        FrontMatterDialog(
            initial = FrontMatter.dataFromFields(state.frontMatter),
            onSave = {
                vm.applyFrontMatter(it)
                showFrontMatter = false
            },
            onDismiss = { showFrontMatter = false }
        )
    }
    if (showPreview) {
        PreviewDialog(
            markdown = state.body,
            title = state.title,
            onDismiss = { showPreview = false }
        )
    }
    if (showValidation) {
        AlertDialog(
            onDismissRequest = { showValidation = false },
            title = { Text("标签与属性检查") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (issues.isEmpty()) {
                        Text("没有发现问题。")
                    } else {
                        issues.forEach { Text("• $it") }
                    }
                    Text(
                        "\n提示：这里只检查标签闭合和少数 front-matter 字段，最终以 Hexo 构建结果为准。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showValidation = false }) { Text("知道了") } }
        )
    }
    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("提交到 GitHub") },
            text = {
                Column {
                    Text(
                        "会直接提交到 ${state.repoFullName} 的 ${state.branch} 分支，Cloudflare Pages 随后自动构建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("commit message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSave = false
                    vm.save(commitMessage)
                }) { Text("提交") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("取消") } }
        )
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃未提交的修改？") },
            text = { Text("本地草稿会保留，但这次离开后需要从草稿恢复。") },
            confirmButton = {
                Button(onClick = {
                    showDiscard = false
                    onBack()
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("继续编辑") }
            }
        )
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "post" -> "文章"
    "page" -> "页面"
    "config" -> "配置"
    else -> kind
}
