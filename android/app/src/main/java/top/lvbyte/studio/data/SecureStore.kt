package top.lvbyte.studio.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Token 与密码只放本机加密存储，绝不写入仓库或日志。
 */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "lvbyte_studio_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, using private prefs", e)
            context.getSharedPreferences("lvbyte_studio_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    var githubToken: String?
        get() = prefs.getString(KEY_GITHUB_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_GITHUB_TOKEN, value).apply()

    var sessionToken: String?
        get() = prefs.getString(KEY_SESSION_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_SESSION_TOKEN, value).apply()

    var imagePassword: String?
        get() = prefs.getString(KEY_IMAGE_PASSWORD, null)
        set(value) = prefs.edit().putString(KEY_IMAGE_PASSWORD, value).apply()

    val hasGithubToken: Boolean get() = !githubToken.isNullOrBlank()
    val hasSession: Boolean get() = !sessionToken.isNullOrBlank()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_SESSION_TOKEN).apply()
    }

    companion object {
        private const val TAG = "SecureStore"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_IMAGE_PASSWORD = "image_password"
    }
}
