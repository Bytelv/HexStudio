package top.lvbyte.hexstudio.core

import android.content.Context
import top.lvbyte.hexstudio.data.DraftStore
import top.lvbyte.hexstudio.data.RepoCache
import top.lvbyte.hexstudio.data.SecureStore
import top.lvbyte.hexstudio.data.SettingsStore
import top.lvbyte.hexstudio.data.UpdateChecker
import top.lvbyte.hexstudio.data.github.DeviceFlowClient
import top.lvbyte.hexstudio.data.github.GitHubClient
import top.lvbyte.hexstudio.data.image.ImageBedClient
import top.lvbyte.hexstudio.data.studio.StudioClient

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsStore(appContext)
    val secure = SecureStore(appContext)
    val github = GitHubClient(secure)
    val deviceFlow = DeviceFlowClient()
    val studio = StudioClient(settings, secure)
    val imageBed = ImageBedClient(appContext, settings, secure)
    val drafts = DraftStore(appContext)
    val repoCache = RepoCache(appContext)
    val updates = UpdateChecker(appContext)
}
