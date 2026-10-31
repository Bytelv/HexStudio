package top.lvbyte.studio.ui.screens.editor

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewDialog(
    markdown: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var pageReady by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("预览 · $title", maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = true
                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = false
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    pageReady = true
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (url.startsWith("http://") || url.startsWith("https://")) {
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl("file:///android_asset/preview/index.html")
                            webView = this
                        }
                    }
                )
                if (!pageReady) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    LaunchedEffect(pageReady, markdown, dark) {
        if (pageReady) {
            webView?.evaluateJavascript(
                "window.renderPreview(${JSONObject.quote(markdown)}, $dark)",
                null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}
