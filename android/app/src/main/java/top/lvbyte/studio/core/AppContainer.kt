package top.lvbyte.studio.core

import android.content.Context
import top.lvbyte.studio.data.DraftStore
import top.lvbyte.studio.data.SecureStore
import top.lvbyte.studio.data.SettingsStore
import top.lvbyte.studio.data.github.DeviceFlowClient
import top.lvbyte.studio.data.github.GitHubClient
import top.lvbyte.studio.data.image.ImageBedClient
import top.lvbyte.studio.data.studio.StudioClient

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsStore(appContext)
    val secure = SecureStore(appContext)
    val github = GitHubClient(secure)
    val deviceFlow = DeviceFlowClient()
    val studio = StudioClient(settings, secure)
    val imageBed = ImageBedClient(appContext, settings, secure)
    val drafts = DraftStore(appContext)
}
