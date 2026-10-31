package top.lvbyte.studio.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import top.lvbyte.studio.BuildConfig
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lvbyte_studio_settings")

data class AppSettings(
    val githubLogin: String = "",
    val repoOwner: String = "",
    val repoName: String = "",
    val repoBranch: String = "main",
    val apiBase: String = BuildConfig.DEFAULT_API_BASE,
    val githubClientId: String = BuildConfig.GITHUB_CLIENT_ID,
    val imageEndpoint: String = "https://image.lvbyte.top/upload",
    val imageUser: String = "",
    val notificationsEnabled: Boolean = true,
    val useStellarImageTag: Boolean = false,
    val lastNotificationId: Long = 0L
) {
    val repoFullName: String
        get() = if (repoOwner.isBlank() || repoName.isBlank()) "" else "$repoOwner/$repoName"

    val hasRepo: Boolean get() = repoFullName.isNotBlank()
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val githubLogin = stringPreferencesKey("github_login")
        val repoOwner = stringPreferencesKey("repo_owner")
        val repoName = stringPreferencesKey("repo_name")
        val repoBranch = stringPreferencesKey("repo_branch")
        val apiBase = stringPreferencesKey("api_base")
        val githubClientId = stringPreferencesKey("github_client_id")
        val imageEndpoint = stringPreferencesKey("image_endpoint")
        val imageUser = stringPreferencesKey("image_user")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val useStellarImageTag = booleanPreferencesKey("use_stellar_image_tag")
        val lastNotificationId = longPreferencesKey("last_notification_id")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                githubLogin = prefs[Keys.githubLogin] ?: "",
                repoOwner = prefs[Keys.repoOwner] ?: "",
                repoName = prefs[Keys.repoName] ?: "",
                repoBranch = prefs[Keys.repoBranch] ?: "main",
                apiBase = (prefs[Keys.apiBase] ?: BuildConfig.DEFAULT_API_BASE).trimEnd('/'),
                githubClientId = prefs[Keys.githubClientId] ?: BuildConfig.GITHUB_CLIENT_ID,
                imageEndpoint = prefs[Keys.imageEndpoint] ?: "https://image.lvbyte.top/upload",
                imageUser = prefs[Keys.imageUser] ?: "",
                notificationsEnabled = prefs[Keys.notificationsEnabled] ?: true,
                useStellarImageTag = prefs[Keys.useStellarImageTag] ?: false,
                lastNotificationId = prefs[Keys.lastNotificationId] ?: 0L
            )
        }

    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun setGithubLogin(login: String) = edit { it[Keys.githubLogin] = login }

    suspend fun setRepo(owner: String, name: String, branch: String) = edit {
        it[Keys.repoOwner] = owner
        it[Keys.repoName] = name
        it[Keys.repoBranch] = branch
    }

    suspend fun clearRepo() = edit {
        it.remove(Keys.repoOwner)
        it.remove(Keys.repoName)
        it.remove(Keys.repoBranch)
    }

    suspend fun setApiBase(value: String) = edit {
        it[Keys.apiBase] = value.trim().trimEnd('/').ifBlank { BuildConfig.DEFAULT_API_BASE }
    }

    suspend fun setGithubClientId(value: String) = edit {
        it[Keys.githubClientId] = value.trim()
    }

    suspend fun setImageBed(endpoint: String, user: String) = edit {
        it[Keys.imageEndpoint] = endpoint.trim().ifBlank { "https://image.lvbyte.top/upload" }
        it[Keys.imageUser] = user.trim()
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit {
        it[Keys.notificationsEnabled] = enabled
    }

    suspend fun setUseStellarImageTag(enabled: Boolean) = edit {
        it[Keys.useStellarImageTag] = enabled
    }

    suspend fun setLastNotificationId(id: Long) = edit {
        if (id > (it[Keys.lastNotificationId] ?: 0L)) it[Keys.lastNotificationId] = id
    }

    suspend fun resetNotificationCursor() = edit { it[Keys.lastNotificationId] = 0L }

    suspend fun clearAccount() = edit {
        it.remove(Keys.githubLogin)
        it.remove(Keys.repoOwner)
        it.remove(Keys.repoName)
        it.remove(Keys.repoBranch)
    }

    suspend fun clearAll() = edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
