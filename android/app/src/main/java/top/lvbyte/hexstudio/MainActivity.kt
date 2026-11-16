package top.lvbyte.hexstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.lvbyte.hexstudio.ui.StudioRoot
import top.lvbyte.hexstudio.ui.theme.StudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StudioApp).container
        // 通知栏点进来的目标页（NotificationWorker.putExtra("open", ...)）
        val initialRoute = intent?.getStringExtra("open").orEmpty()
        setContent {
            StudioTheme {
                StudioRoot(container, initialRoute = initialRoute)
            }
        }
    }
}
