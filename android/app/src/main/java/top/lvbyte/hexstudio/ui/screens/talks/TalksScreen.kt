package top.lvbyte.hexstudio.ui.screens.talks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.lvbyte.hexstudio.core.AppContainer
import top.lvbyte.hexstudio.data.model.Talk
import top.lvbyte.hexstudio.ui.appViewModelFactory
import top.lvbyte.hexstudio.ui.components.AppLoading
import top.lvbyte.hexstudio.ui.components.EmptyState
import top.lvbyte.hexstudio.ui.components.ErrorCard
import top.lvbyte.hexstudio.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalksScreen(container: AppContainer) {
    val vm: TalksViewModel = viewModel(factory = appViewModelFactory { TalksViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCompose by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Talk?>(null) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Talk?>(null) }
    var inlineActive by rememberSaveable { mutableStateOf(false) }
    val inlineText = rememberSaveable { mutableStateOf("") }
    var composeSeed by rememberSaveable { mutableStateOf("") }
    var inlineAllowExpand by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!state.loading) vm.load()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("全部说说", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.loading && state.talks.isEmpty()) "正在同步…" else "${state.talks.size} 条记录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = "导入 tgtalk")
                    }
                    IconButton(onClick = vm::load) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.talks.isEmpty() -> AppLoading("加载说说…")
                !state.error.isNullOrBlank() && state.talks.isEmpty() -> ErrorCard(
                    state.error ?: "",
                    modifier = Modifier.padding(16.dp),
                    onRetry = vm::load
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp
                    )
                ) {
                    item(key = "composer") {
                        NenoInlineComposer(
                            active = inlineActive,
                            textState = inlineText,
                            allowAutoExpand = inlineAllowExpand,
                            onActivate = {
                                inlineActive = true
                                inlineAllowExpand = true
                                scope.launch {
                                    delay(80)
                                    listState.animateScrollToItem(0)
                                }
                            },
                            onExpand = {
                                composeSeed = inlineText.value
                                inlineActive = false
                                editing = null
                                showCompose = true
                            },
                            onPublish = { text -> vm.publish(text, emptyList(), null) },
                            onPublished = {
                                inlineText.value = ""
                                inlineActive = false
                                inlineAllowExpand = true
                            }
                        )
                    }
                    if (state.talks.isEmpty()) {
                        item(key = "empty") { NenoEmptyCard() }
                    } else {
                        items(state.talks, key = { it.id }) { talk ->
                            NenoTalkItem(
                                talk = talk,
                                onEdit = {
                                    composeSeed = ""
                                    editing = talk
                                    showCompose = true
                                },
                                onDelete = { toDelete = talk },
                                onImageClick = { previewImage = it }
                            )
                        }
                    }
                    if (!state.error.isNullOrBlank()) {
                        item(key = "list-error") {
                            ErrorCard(state.error ?: "", onRetry = vm::load)
                        }
                    }
                }
            }
        }
    }

    if (showCompose) {
        TalkComposeDialog(
            vm = vm,
            editing = editing,
            initialContent = editing?.content ?: composeSeed,
            initialImages = editing?.images ?: emptyList(),
            onDismiss = { published, finalText ->
                showCompose = false
                if (published) {
                    inlineText.value = ""
                    composeSeed = ""
                    inlineActive = false
                    inlineAllowExpand = true
                } else if (editing == null && finalText.isNotBlank()) {
                    inlineText.value = finalText
                    composeSeed = ""
                    inlineActive = true
                    inlineAllowExpand = false
                }
                editing = null
            }
        )
    }

    if (showImport) {
        ImportTalksDialog(
            vm = vm,
            onDismiss = { showImport = false }
        )
    }

    previewImage?.let { url ->
        Dialog(
            onDismissRequest = { previewImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { previewImage = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
                IconButton(
                    onClick = { previewImage = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
        }
    }

    toDelete?.let { talk ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除这条说说？") },
            text = { Text(talk.content.take(80)) },
            confirmButton = {
                Button(onClick = {
                    val id = talk.id
                    toDelete = null
                    scope.launch {
                        val error = vm.delete(id)
                        snackbar.showSnackbar(error ?: "已删除")
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } }
        )
    }
}

private val NenoGreen = Color(0xFF2F9E6B)
private val NenoGreenDark = Color(0xFF5FD3A1)
private val NenoHashtag = Regex("""(^|[\s(\[（])#([\p{L}\p{N}_-]{1,30})""")

@Composable
private fun nenoAccent(): Color = if (isSystemInDarkTheme()) NenoGreenDark else NenoGreen

@Composable
private fun nenoOnAccent(): Color = if (isSystemInDarkTheme()) Color(0xFF06281A) else Color.White

@Composable
private fun nenoHighlight(content: String): AnnotatedString {
    val accent = nenoAccent()
    return remember(content, accent) {
        buildAnnotatedString {
            append(content)
            NenoHashtag.findAll(content).forEach { match ->
                val start = match.range.first + match.groupValues[1].length
                addStyle(
                    SpanStyle(color = accent, fontWeight = FontWeight.Medium),
                    start,
                    match.range.last + 1
                )
            }
        }
    }
}

@Composable
private fun NenoInlineComposer(
    active: Boolean,
    textState: MutableState<String>,
    allowAutoExpand: Boolean,
    onActivate: () -> Unit,
    onExpand: () -> Unit,
    onPublish: suspend (String) -> String?,
    onPublished: () -> Unit
) {
    val text = textState.value
    val accent = nenoAccent()
    val onAccent = nenoOnAccent()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lineCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(active) {
        if (active) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
        } else {
            error = null
        }
    }

    LaunchedEffect(active, allowAutoExpand, text, lineCount) {
        if (!active || !allowAutoExpand || text.isBlank()) return@LaunchedEffect
        if (lineCount > 7 || text.length > 320) {
            delay(180)
            onExpand()
        }
    }

    fun submit() {
        val value = text.trim()
        if (value.isEmpty() || busy) return
        scope.launch {
            busy = true
            error = null
            val err = onPublish(value)
            busy = false
            if (err == null) {
                textState.value = ""
                onPublished()
            } else {
                error = err
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(180)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            if (!active) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onActivate)
                ) {
                    Text(
                        if (text.isBlank()) "现在的想法是…" else text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (text.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Tag,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(10.dp), color = accent) {
                            Text(
                                "写说说",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = onAccent
                            )
                        }
                    }
                }
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= 5000) textState.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = 186.dp)
                        .verticalScroll(scrollState)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        lineHeight = 27.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(accent),
                    onTextLayout = { lineCount = it.lineCount },
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    "现在的想法是…",
                                    fontSize = 17.sp,
                                    lineHeight = 27.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            textState.value =
                                if (text.isBlank() || text.endsWith(" ")) "$text#" else "$text #"
                            runCatching { focusRequester.requestFocus() }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Tag,
                            contentDescription = "插入标签",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        enabled = !busy,
                        onClick = onExpand,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = "添加图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        enabled = !busy,
                        onClick = onExpand,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Outlined.OpenInFull,
                            contentDescription = "展开编辑器",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${text.length}/5000",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = text.isNotBlank() && !busy,
                        onClick = ::submit,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = onAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = onAccent
                            )
                        } else {
                            Text("发布", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun NenoEmptyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("还没有说说", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "点上面的输入框，记下此刻。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NenoTalkItem(
    talk: Talk,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            talk.created.ifBlank { TimeUtils.dateTime(talk.createdAt) },
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        nenoHighlight(talk.content),
                        modifier = Modifier.weight(1f).padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "更多",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
                if (talk.images.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    TalkImages(talk.images, onImageClick)
                }
                val extraTags = talk.tags.filter { "#$it" !in talk.content }
                if (extraTags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        extraTags.take(6).forEach { tag ->
                            Text(
                                "#$tag",
                                style = MaterialTheme.typography.labelMedium,
                                color = nenoAccent()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TalkImages(images: List<String>, onClick: (String) -> Unit) {
    if (images.size == 1) {
        AsyncImage(
            model = images.first(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onClick(images.first()) }
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        images.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClick(url) }
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TalkComposeDialog(
    vm: TalksViewModel,
    editing: Talk?,
    initialContent: String,
    initialImages: List<String>,
    onDismiss: (published: Boolean, finalContent: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var content by rememberSaveable(editing?.id, initialContent) { mutableStateOf(initialContent) }
    var images by remember(editing?.id, initialImages) { mutableStateOf(initialImages) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val timestamp = remember(editing?.id) { editing?.createdAt ?: System.currentTimeMillis() }
    val dateLabel = remember(timestamp) {
        TimeUtils.dayLabel(timestamp) + " " + TimeUtils.weekdayLabel(timestamp)
    }
    val accent = nenoAccent()
    val onAccent = nenoOnAccent()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            try {
                val urls = vm.uploadImages(uris) { _, _, label -> progress = label }
                images = (images + urls).take(9)
            } catch (e: Exception) {
                error = e.message ?: "图片上传失败"
            } finally {
                busy = false
                progress = ""
            }
        }
    }

    fun submit() {
        if (content.isBlank()) {
            error = "说说不能为空"
            return
        }
        scope.launch {
            busy = true
            error = null
            val err = vm.publish(content.trim(), images, editing?.id)
            busy = false
            if (err == null) onDismiss(true, content) else error = err
        }
    }

    LaunchedEffect(Unit) {
        entered = true
        delay(200)
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss(false, content) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.985f, animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(enabled = !busy, onClick = { onDismiss(false, content) }) { Text("取消") }
                    Text(
                        dateLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        enabled = content.isNotBlank() && !busy,
                        onClick = ::submit,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = onAccent
                        )
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = onAccent
                            )
                        } else {
                            Text(if (editing == null) "发布" else "保存")
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .imePadding()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.padding(16.dp)) {
                    BasicTextField(
                        value = content,
                        onValueChange = { value ->
                            if (value.length <= 5000) content = value
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(accent),
                        decorationBox = { inner ->
                            Box {
                                if (content.isEmpty()) {
                                    Text(
                                        "现在的想法是…",
                                        fontSize = 17.sp,
                                        lineHeight = 28.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    if (content.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${content.length}/5000",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }

                    val tags = vm.extractTags(content)
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            tags.take(8).forEach { tag ->
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        images.take(9).forEachIndexed { index, url ->
                            Box(modifier = Modifier.size(92.dp)) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                IconButton(
                                    onClick = { images = images.filterIndexed { i, _ -> i != index } },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "移除",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }
                        if (images.size < 9) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .clickable(enabled = !busy) {
                                        picker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "添加图片",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "图片",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (progress.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(progress, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = !busy && images.size < 9,
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = "添加图片")
                    }
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            content = if (content.isBlank() || content.endsWith(" ")) "$content#" else "$content #"
                            focusRequester.requestFocus()
                        }
                    ) {
                        Icon(Icons.Outlined.Tag, contentDescription = "插入标签", tint = accent)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (editing == null) "新说说" else "编辑中",
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
private fun ImportTalksDialog(
    vm: TalksViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("https://tgtalk.4123010.xyz/?tag=SFCN") }
    var rehost by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<ImportOutcome?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("导入 tgtalk 说说") },
        text = {
            Column {
                Text(
                    "从 tgtalk 的 JSON 接口读取历史说说，按 source_id 去重写入 Cloudflare D1。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("JSON 地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = rehost, onCheckedChange = { rehost = it }, enabled = !busy)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("把图片转存到图床", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "逐张上传，受 Telegram 速率限制，会比较慢",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (progress.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(progress, style = MaterialTheme.typography.labelSmall)
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (outcome != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "导入完成：${outcome?.imported} 条，跳过 ${outcome?.skipped} 条。",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (outcome != null) {
                Button(onClick = onDismiss) { Text("完成") }
            } else {
                Button(
                    enabled = !busy && url.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        progress = ""
                        scope.launch {
                            val (result, err) = vm.importFromTgtalk(url.trim(), rehost) { progress = it }
                            busy = false
                            outcome = result
                            error = err
                        }
                    }
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("开始导入")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("关闭") }
        }
    )
}

