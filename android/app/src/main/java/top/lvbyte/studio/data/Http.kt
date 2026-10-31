package top.lvbyte.studio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object StudioJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
}

class ApiException(val status: Int, message: String) : Exception(message)

class SessionExpiredException : Exception("登录状态已失效，请重新登录")

val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .callTimeout(90, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

data class HttpResult(
    val code: Int,
    val body: String,
    val successful: Boolean,
    val headers: Map<String, String> = emptyMap()
)

data class BinaryResult(
    val code: Int,
    val bytes: ByteArray,
    val contentType: String,
    val successful: Boolean
)

suspend fun OkHttpClient.executeBinary(request: Request): BinaryResult = withContext(Dispatchers.IO) {
    newCall(request).execute().use { response ->
        BinaryResult(
            code = response.code,
            bytes = response.body?.bytes() ?: ByteArray(0),
            contentType = response.header("content-type").orEmpty(),
            successful = response.isSuccessful
        )
    }
}

suspend fun OkHttpClient.execute(request: Request): HttpResult = withContext(Dispatchers.IO) {
    newCall(request).execute().use { response ->
        HttpResult(
            code = response.code,
            body = response.body?.string().orEmpty(),
            successful = response.isSuccessful,
            headers = response.headers.names().associateWith { response.header(it).orEmpty() }
        )
    }
}

fun parseApiError(body: String): String? {
    if (body.isBlank()) return null
    return try {
        val obj = StudioJson.json.parseToJsonElement(body).jsonObject
        val message = obj["message"]?.jsonPrimitive?.contentOrNullSafe()
        val error = obj["error"]?.jsonPrimitive?.contentOrNullSafe()
        message ?: error
    } catch (_: Exception) {
        body.take(300).takeIf { it.isNotBlank() }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    try { content } catch (_: Exception) { null }

fun HttpResult.requireOk(): HttpResult {
    if (!successful) throw ApiException(code, parseApiError(body) ?: "HTTP $code")
    return this
}

fun Request.Builder.githubApi(token: String): Request.Builder = apply {
    header("Authorization", "Bearer $token")
    header("Accept", "application/vnd.github+json")
    header("X-GitHub-Api-Version", "2022-11-28")
    header("User-Agent", "LvbyteStudio-Android/1.0")
}

fun Request.Builder.session(token: String): Request.Builder = apply {
    header("Authorization", "Bearer $token")
    header("User-Agent", "LvbyteStudio-Android/1.0")
}

fun Request.Builder.jsonBody(body: String): Request.Builder = apply {
    post(body.toRequestBody(StudioJson.jsonMediaType))
}

fun Request.Builder.jsonPatch(body: String): Request.Builder = apply {
    patch(body.toRequestBody(StudioJson.jsonMediaType))
}

fun Request.Builder.jsonPost(body: String): Request.Builder = apply {
    post(body.toRequestBody(StudioJson.jsonMediaType))
}

fun Request.Builder.jsonPut(body: String): Request.Builder = apply {
    put(body.toRequestBody(StudioJson.jsonMediaType))
}

fun Request.Builder.noBodyPost(): Request.Builder = apply { post(ByteArray(0).toRequestBody(null)) }

fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

fun encodeGithubPath(path: String): String =
    path.split('/').filter { it.isNotEmpty() }.joinToString("/") { encodePathSegment(it) }

fun ResponseBody.text(): String = string()

inline fun <reified T> parseJson(body: String): T = StudioJson.json.decodeFromString(body)

fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNullSafe()
