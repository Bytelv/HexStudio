package top.lvbyte.hexstudio

import android.app.Application
import top.lvbyte.hexstudio.core.AppContainer

class StudioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        top.lvbyte.hexstudio.work.NotificationScheduler.ensureChannel(this)
        top.lvbyte.hexstudio.work.NotificationScheduler.schedule(this, true)
    }
}
