package top.lvbyte.studio.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import top.lvbyte.studio.core.AppContainer
import top.lvbyte.studio.ui.screens.content.ContentScreen
import top.lvbyte.studio.ui.screens.editor.EditorScreen
import top.lvbyte.studio.ui.screens.friends.FriendsScreen
import top.lvbyte.studio.ui.screens.home.HomeScreen
import top.lvbyte.studio.ui.screens.repo.RepoPickerScreen
import top.lvbyte.studio.ui.screens.settings.SettingsScreen
import top.lvbyte.studio.ui.screens.talks.TalksScreen

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home", "首页", Icons.Outlined.Home),
    NavItem("content", "内容", Icons.AutoMirrored.Outlined.Article),
    NavItem("talks", "说说", Icons.Outlined.ChatBubbleOutline),
    NavItem("friends", "友链", Icons.Outlined.Link),
    NavItem("settings", "设置", Icons.Outlined.Settings)
)

@Composable
fun MainNavHost(
    container: AppContainer,
    onLogout: () -> Unit,
    onRepoChanged: () -> Unit
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topRoute = currentRoute?.substringBefore('?')
    val showBottomBar = topRoute in navItems.map { it.route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = topRoute == item.route,
                            onClick = {
                                val target = if (item.route == "content") "content?new=false" else item.route
                                navController.navigate(target) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    container = container,
                    onOpenContent = { startNew ->
                        navController.navigate(if (startNew) "content?new=true" else "content?new=false")
                    },
                    onOpenTalks = { navController.navigate("talks") },
                    onOpenFriends = { navController.navigate("friends") },
                    onSwitchRepo = { navController.navigate("repoSwitch") }
                )
            }
            composable(
                route = "content?new={new}",
                arguments = listOf(
                    navArgument("new") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                val startNew = entry.arguments?.getBoolean("new") ?: false
                ContentScreen(
                    container = container,
                    startNew = startNew,
                    onOpenEditor = { path ->
                        navController.navigate("editor/${Uri.encode(path)}")
                    }
                )
            }
            composable(
                route = "editor/{path}",
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                val path = Uri.decode(entry.arguments?.getString("path") ?: "")
                EditorScreen(
                    container = container,
                    path = path,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("talks") { TalksScreen(container) }
            composable("friends") { FriendsScreen(container) }
            composable("settings") {
                SettingsScreen(
                    container = container,
                    onSwitchRepo = { navController.navigate("repoSwitch") },
                    onLogout = onLogout
                )
            }
            composable("repoSwitch") {
                RepoPickerScreen(
                    container = container,
                    isSwitch = true,
                    onDone = {
                        onRepoChanged()
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout
                )
            }
        }
    }
}
