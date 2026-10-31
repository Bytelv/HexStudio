package top.lvbyte.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.lvbyte.studio.ui.StudioRoot
import top.lvbyte.studio.ui.theme.StudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StudioApp).container
        setContent {
            StudioTheme {
                StudioRoot(container)
            }
        }
    }
}
