package top.lvbyte.hexstudio.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.lvbyte.hexstudio.data.ApiException
import top.lvbyte.hexstudio.data.SecureStore
import top.lvbyte.hexstudio.data.SettingsStore
import top.lvbyte.hexstudio.data.execute
import top.lvbyte.hexstudio.data.sharedHttpClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ImageBedClient(
    private val context: Context,
    private val settings: SettingsStore,
    private val secure: SecureStore
) {
    data class PreparedImage(
        val bytes: ByteArray,
        val mime: String,
        val filename: String,
        val warning: String? = null
    )

    data class UploadedImage(
        val url: String,
        val filename: String,
        val warning: String? = null
    )

    suspend fun prepare(uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ApiException(400, "无法读取这张图片")
        if (bytes.isEmpty()) throw ApiException(400, "图片是空的")
        if (bytes.size > 30 * 1024 * 1024) throw ApiException(413, "图片超过 30MB，请先压缩")

        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.takeIf { it.isNotBlank() }
        val mime = resolver.getType(uri)?.lowercase()
            ?: guessMime(displayName)
            ?: "image/jpeg"

        if (mime == "image/gif") {
            if (bytes.size > 8 * 1024 * 1024) throw ApiException(413, "GIF 超过 8MB，图床可能拒绝")
            return@withContext PreparedImage(
                bytes = bytes,
                mime = mime,
                filename = displayName ?: "image_${System.currentTimeMillis()}.gif"
            )
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ApiException(400, "这个文件不是可识别的图片")
        }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 2560 || bounds.outHeight / (sample * 2) >= 2560) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw ApiException(400, "图片解码失败")

        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        var bitmap = applyOrientation(decoded, orientation)
        var scaled = false
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        if (maxDimension > 2560) {
            val ratio = 2560f / maxDimension
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
            scaled = true
        }

        val isPng = mime == "image/png"
        val rotatedOrScaled = scaled || orientation != ExifInterface.ORIENTATION_NORMAL
        if (!rotatedOrScaled && bytes.size <= 1_500_000 && (mime == "image/jpeg" || isPng)) {
            return@withContext PreparedImage(
                bytes = bytes,
                mime = mime,
                filename = displayName ?: "image_${System.currentTimeMillis()}.${if (isPng) "png" else "jpg"}",
                warning = if (isPng) "图床会把 PNG 透明区域压成白底" else null
            )
        }

        val outputBitmap = if (isPng) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { target ->
                val canvas = Canvas(target)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }
        }

        var quality = 88
        var outBytes: ByteArray
        while (true) {
            val output = ByteArrayOutputStream()
            val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            outputBitmap.compress(format, if (isPng) 100 else quality, output)
            outBytes = output.toByteArray()
            if (outBytes.size <= 8 * 1024 * 1024 || isPng || quality <= 60) break
            quality -= 10
        }
        if (outBytes.size > 12 * 1024 * 1024) {
            throw ApiException(413, "压缩后仍然太大，请换一张图片")
        }

        val extension = if (isPng) "png" else "jpg"
        val baseName = (displayName ?: "image_${System.currentTimeMillis()}")
            .substringBeforeLast('.', displayName ?: "image")
            .ifBlank { "image" }
        PreparedImage(
            bytes = outBytes,
            mime = if (isPng) "image/png" else "image/jpeg",
            filename = "${baseName}.${extension}",
            warning = if (isPng) "图床会把 PNG 透明区域压成白底" else null
        )
    }

    suspend fun upload(uri: Uri): UploadedImage {
        val prepared = prepare(uri)
        return uploadPrepared(prepared)
    }

    suspend fun uploadPrepared(prepared: PreparedImage): UploadedImage = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val endpoint = snapshot.imageEndpoint.trim()
        if (endpoint.isBlank()) throw ApiException(400, "还没有配置图床地址")
        val password = secure.imagePassword.orEmpty()
        val user = snapshot.imageUser
        if ((user.isBlank()) != (password.isBlank())) {
            throw ApiException(401, "图床用户名和密码需要同时填写")
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                prepared.filename,
                prepared.bytes.toRequestBody(prepared.mime.toMediaTypeOrNull())
            )
            .build()
        val builder = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .post(body)
        if (user.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(user, password))
        }
        val res = sharedHttpClient.execute(builder.build())
        if (res.code == 401) {
            throw ApiException(401, "图床认证失败：请检查用户名和密码（不要在末尾带空格）")
        }
        if (res.code == 413) throw ApiException(413, "图床拒绝了这张图片：文件太大")
        if (!res.successful) {
            throw ApiException(res.code, "图床上传失败（HTTP ${res.code}）")
        }
        val src = extractSrc(res.body)
            ?: throw ApiException(500, "图床返回里没有图片地址")
        UploadedImage(
            url = absoluteUrl(endpoint, src),
            filename = prepared.filename,
            warning = prepared.warning
        )
    }

    suspend fun testUpload(): UploadedImage {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(91, 108, 255))
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return uploadPrepared(
            PreparedImage(
                bytes = output.toByteArray(),
                mime = "image/png",
                filename = "lvbyte-studio-test.png",
                warning = "测试图片"
            )
        )
    }

    private fun extractSrc(body: String): String? {
        return try {
            val element = Json.parseToJsonElement(body)
            val obj = when {
                element is kotlinx.serialization.json.JsonArray -> element.jsonArray.firstOrNull()?.jsonObject
                else -> element.jsonObject
            }
            obj?.get("src")?.jsonPrimitive?.content
                ?: obj?.get("url")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    private fun absoluteUrl(endpoint: String, src: String): String {
        if (src.startsWith("http://") || src.startsWith("https://")) return src
        val base = endpoint.toHttpUrlOrNull() ?: return src
        val portPart = if ((base.scheme == "https" && base.port == 443) || (base.scheme == "http" && base.port == 80)) {
            ""
        } else {
            ":${base.port}"
        }
        return if (src.startsWith("/")) {
            "${base.scheme}://${base.host}$portPart$src"
        } else {
            "${base.scheme}://${base.host}$portPart/${src}"
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun guessMime(name: String?): String? {
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return null
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> null
        }
    }
}
