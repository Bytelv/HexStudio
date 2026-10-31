package top.lvbyte.hexstudio.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import top.lvbyte.hexstudio.BuildConfig
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String = ""
)

class UpdateChecker(context: Context) {
    private val prefs = context.getSharedPreferences("hexstudio_updates", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val manifestUrls = listOf(
        BuildConfig.UPDATE_MANIFEST_URL,
        "https://cdn.jsdelivr.net/gh/Bytelv/HexStudio@main/update.json"
    ).filter { it.isNotBlank() }.distinct()

    suspend fun fetch(): UpdateInfo = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (base in manifestUrls) {
            try {
                val url = base + (if (base.contains('?')) "&" else "?") + "t=" + System.currentTimeMillis()
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "HexStudio-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException(response.code, "HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val info = StudioJson.json.decodeFromString<UpdateInfo>(body)
                    if (info.versionCode <= 0 || info.downloadUrl.isBlank()) {
                        throw ApiException(500, "更新信息不完整")
                    }
                    return@withContext info
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: ApiException(500, "检查更新失败")
    }

    fun shouldAutoCheck(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > AUTO_CHECK_INTERVAL_MS
    }

    fun markChecked() {
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun isNewer(info: UpdateInfo): Boolean = info.versionCode > BuildConfig.VERSION_CODE

    private companion object {
        const val KEY_LAST_CHECK = "last_check"
        const val AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000
    }
}
