package top.lvbyte.studio

import android.app.Application
import top.lvbyte.studio.core.AppContainer

class StudioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        top.lvbyte.studio.work.NotificationScheduler.ensureChannel(this)
        top.lvbyte.studio.work.NotificationScheduler.schedule(this, true)
    }
}
