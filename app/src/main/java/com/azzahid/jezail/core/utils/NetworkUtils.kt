package com.azzahid.jezail.core.utils

import com.google.gson.Gson
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.path
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

fun RoutingNode.toFlattenedList(): List<RoutingNode> {
    val result = mutableListOf<RoutingNode>()
    val stack = ArrayDeque(children)

    while (stack.isNotEmpty()) {
        val node = stack.removeFirst()
        result.add(node)
        stack.addAll(0, node.children)
    }

    return result
}


object RoutingNodeSerializer : KSerializer<RoutingNode> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoutingNode") {
        element<String>("path")
    }

    override fun serialize(encoder: Encoder, value: RoutingNode) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.path.ifEmpty { "/" })
        }
    }

    override fun deserialize(decoder: Decoder): RoutingNode {
        throw NotImplementedError("Deserialization is not implemented for RoutingNode")
    }
}

object AnySerializer : KSerializer<Any> {
    private val gson = Gson()
    private val jsonElementSerializer = JsonElement.serializer()
    override val descriptor = jsonElementSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonString = gson.toJson(value)
        val jsonElement = Json.parseToJsonElement(jsonString)
        jsonElementSerializer.serialize(encoder, jsonElement)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any> {
        throw NotImplementedError("Deserialization is not implemented for MapStringAny")
    }
}


fun getLocalIpAddress(): String = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
        ?.hostAddress ?: "127.0.0.1"
} catch (_: Exception) {
    "127.0.0.1"
}

fun downloadFile(url: String, to: File) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 30000
        readTimeout = 60000
    }
    if (connection.responseCode != 200) throw IOException("Download failed: ${connection.responseCode}")
    connection.inputStream.use { input ->
        FileOutputStream(
            to, false
        ).use { input.copyTo(it) }
    }
    connection.disconnect()
}
