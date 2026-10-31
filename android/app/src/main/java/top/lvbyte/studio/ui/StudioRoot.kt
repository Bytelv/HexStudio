package top.lvbyte.studio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.lvbyte.studio.core.AppContainer
import top.lvbyte.studio.ui.screens.login.LoginScreen
import top.lvbyte.studio.ui.screens.repo.RepoPickerScreen

@Composable
fun StudioRoot(container: AppContainer) {
    val appVm: AppViewModel = viewModel(factory = appViewModelFactory { AppViewModel(container) })
    val gate by appVm.gate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(gate) {
        if (gate == Gate.Ready) {
            top.lvbyte.studio.work.NotificationScheduler.pollNow(context)
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (gate) {
            Gate.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            Gate.NeedLogin -> LoginScreen(
                container = container,
                onAuthenticated = { appVm.refresh() }
            )
            Gate.NeedRepo -> RepoPickerScreen(
                container = container,
                isSwitch = false,
                onDone = { appVm.refresh() },
                onLogout = { appVm.logout() }
            )
            Gate.Ready -> MainNavHost(
                container = container,
                onLogout = { appVm.logout() },
                onRepoChanged = { appVm.refresh() }
            )
        }
    }
}
