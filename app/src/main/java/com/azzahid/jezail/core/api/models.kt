package com.azzahid.jezail.core.api

import android.content.Context
import com.azzahid.jezail.MyApplication
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.fromFileExtension
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
sealed class ApiResponse<out T> {
    abstract val success: Boolean
    val timestamp: String = System.currentTimeMillis().toString()
}

@Serializable
@SerialName("success")
data class Success<T>(
    val data: T,
    override val success: Boolean = true,
) : ApiResponse<T>()

@Serializable
@SerialName("failure")
data class Failure(
    val error: String,
    override val success: Boolean = false,
) : ApiResponse<Nothing>()

class AssetsResourceProvider(
    private val basePath: String = "",
    private val context: Context = MyApplication.appContext,
) {
    fun getResource(path: String): AssetResource? = runCatching {
        val fullPath = if (basePath.isEmpty()) path else "$basePath/$path"
        context.assets.open(fullPath).use { AssetResource(fullPath, it.readBytes()) }
    }.getOrNull()

}

data class AssetResource(val path: String, val bytes: ByteArray) {
    fun getContentType(): ContentType =
        ContentType.fromFileExtension(File(path).extension).firstOrNull()
            ?: ContentType.Application.OctetStream

    override fun equals(other: Any?) =
        this === other || (other is AssetResource &&
                path == other.path && bytes.contentEquals(other.bytes))

    override fun hashCode() = 31 * path.hashCode() + bytes.contentHashCode()
}

suspend fun ApplicationCall.respondAssetNoCache(resource: AssetResource) {
    response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate")
    response.header(HttpHeaders.Pragma, "no-cache")
    respondBytes(resource.bytes, resource.getContentType())
}

suspend fun ApplicationCall.respondAsset(resource: AssetResource) {
    response.header(HttpHeaders.CacheControl, "public, max-age=3600")
    response.header(HttpHeaders.ETag, "\"${resource.path.hashCode()}\"")
    respondBytes(resource.bytes, resource.getContentType())
}
