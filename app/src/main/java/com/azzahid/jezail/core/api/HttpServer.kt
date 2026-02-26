package com.azzahid.jezail.core.api

import android.os.Build
import android.util.Log
import com.azzahid.jezail.core.api.routes.adbRoutes
import com.azzahid.jezail.core.api.routes.certificateRoutes
import com.azzahid.jezail.core.api.routes.deviceRoutes
import com.azzahid.jezail.core.api.routes.filesRoutes
import com.azzahid.jezail.core.api.routes.fridaRoutes
import com.azzahid.jezail.core.api.routes.packageRoutes
import com.azzahid.jezail.core.data.models.AssetsResourceProvider
import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.data.models.respondAssetNoCache
import com.azzahid.jezail.core.utils.AnySerializer
import com.azzahid.jezail.core.utils.RoutingNodeSerializer
import com.azzahid.jezail.features.managers.AuthManager
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.cio.CIOApplicationEngine.Configuration
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.slf4j.event.Level
import kotlin.reflect.KClass


typealias CIOEmbeddedServer = EmbeddedServer<CIOApplicationEngine, Configuration>

private const val TAG = "HttpServer"

fun buildServer(port: Int): CIOEmbeddedServer {
    return embeddedServer(CIO, port = port) {
        configureServer()
        configureRouting()
    }
}

private val bypassPaths = listOf("/pair", "/api/status", "/api/json", "/api/swagger")

private val AuthPlugin = createApplicationPlugin(name = "AuthPlugin") {
    onCall { call ->
        if (!AuthManager.isEnabled()) return@onCall
        val path = call.request.path()
        if (bypassPaths.any { path.startsWith(it) }) return@onCall

        val authHeader = call.request.headers[HttpHeaders.Authorization]
        val bearerToken = authHeader?.removePrefix("Bearer ")?.trim()
        if (bearerToken != null && AuthManager.validateToken(bearerToken)) return@onCall

        val cookieToken = call.request.cookies["jezail_token"]
        if (cookieToken != null && AuthManager.validateToken(cookieToken)) return@onCall

        if (path.startsWith("/api/")) {
            call.respond(HttpStatusCode.Unauthorized, Failure(error = "Unauthorized"))
        } else {
            call.respondRedirect("/pair")
        }
    }
}

fun Application.configureRouting() {
    val webFiles = AssetsResourceProvider("web")
    val refridaFiles = AssetsResourceProvider("refrida")
    val pairFiles = AssetsResourceProvider("pair")

    install(AuthPlugin)

    routing {
        get("/pair") {
            if (!AuthManager.isEnabled()) {
                call.respondRedirect("/")
                return@get
            }
            val resource = pairFiles.getResource("index.html")
            if (resource != null) call.respondAssetNoCache(resource)
            else call.respond(NotFound, Failure(error = "Pairing page not found"))
        }

        post("/pair") {
            val body = runCatching {
                Json.parseToJsonElement(call.receiveText()).jsonObject
            }.getOrNull()
            val pin = body?.get("pin")?.jsonPrimitive?.content
            if (pin == null) {
                call.respond(BadRequest, Failure(error = "Missing pin"))
                return@post
            }
            val token = AuthManager.pair(pin)
            if (token != null) {
                call.response.cookies.append(Cookie("jezail_token", token, path = "/", httpOnly = true))
                call.respond(Success(data = mapOf("token" to token)))
            } else {
                call.respond(Forbidden, Failure(error = "Invalid PIN"))
            }
        }

        route("/api") {
            get("/status", {
                description = "Checks the health/status of the API"
            }) {
                call.respond(
                    Success(
                        data = mapOf(
                            "status" to "ok",
                        )
                    )
                )
            }

            route("json") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openApi()
                } else {
                    get {
                        call.respondText(
                            "OpenAPI documentation requires Android 12 (API 31) or higher",
                            status = HttpStatusCode.NotImplemented
                        )
                    }
                }
            }

            route("/swagger") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    swaggerUI("/api/json")
                } else {
                    get {
                        call.respondText(
                            "Swagger UI requires Android 12 (API 31) or higher",
                            status = HttpStatusCode.NotImplemented
                        )
                    }
                }
            }

            adbRoutes()
            fridaRoutes()
            deviceRoutes()
            packageRoutes()
            filesRoutes()
            certificateRoutes()
        }

        get("/refrida") {
            call.respondRedirect("/refrida/index.html", permanent = false)
        }

        get("/refrida/{file...}") {
            val path = call.parameters.getAll("file")?.joinToString("/")
                ?: return@get call.respond(NotFound)
            val resource = refridaFiles.getResource(path)
            if (resource != null) call.respondAssetNoCache(resource)
            else call.respond(NotFound)
        }

        get {
            call.respondRedirect("/index.html", permanent = false)
        }

        get("/{file...}") {
            val path = call.parameters.getAll("file")?.joinToString("/")
                ?: return@get call.respond(NotFound)
            val resource = webFiles.getResource(path)
            if (resource != null) call.respondAssetNoCache(resource)
            else call.respond(NotFound)
        }
    }
}


fun Application.configureServer() {

    install(CORS) {
        anyHost() // Only for testing purposes.
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        exposeHeader(HttpHeaders.ContentDisposition)
        allowCredentials = true
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        install(OpenApi)
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(BadRequest, Failure(error = cause.message ?: "Invalid request"))
            Log.w(
                TAG,
                "Bad Request (400) - IllegalArgumentException: ${call.request.httpMethod.value} ${call.request.path()} - ${cause.message}",
                cause
            )
        }

        exception<IllegalStateException> { call, cause ->
            call.respond(
                Conflict,
                Failure(error = cause.message ?: "Request could not be completed")
            )
            Log.e(
                TAG,
                "Conflict (409) - IllegalStateException: ${call.request.httpMethod.value} ${call.request.path()} - ${cause.message}",
                cause
            )
        }

        exception<IllegalAccessException> { call, cause ->
            call.respond(BadRequest, Failure(error = cause.message ?: "Access denied"))
            Log.w(
                TAG,
                "Bad Request (400) - IllegalAccessException: ${call.request.httpMethod.value} ${call.request.path()} - ${cause.message}",
                cause
            )
        }

        exception<SecurityException> { call, cause ->
            call.respond(BadRequest, Failure(error = cause.message ?: "Security error"))
            Log.w(
                TAG,
                "Bad Request (400) - SecurityException: ${call.request.httpMethod.value} ${call.request.path()} - ${cause.message}",
                cause
            )
        }

        exception<Throwable> { call, cause ->
            call.respond(
                InternalServerError,
                Failure(
                    error = mapOf(
                        "type" to cause::class.simpleName,
                        "message" to cause.message,
                        "localizedMessage" to cause.localizedMessage,
                        "stackTrace" to cause.stackTrace.take(5).map { it.toString() },
                        "cause" to cause.cause?.let {
                            mapOf(
                                "type" to it::class.simpleName,
                                "message" to it.message
                            )
                        }
                    ).toString())
            )
            Log.e(
                TAG,
                "Internal Server Error (500): ${call.request.httpMethod.value} ${call.request.path()}",
                cause
            )
        }

        status(BadRequest) { call, status ->
            Log.w(TAG, "Bad Request (400): ${call.request.httpMethod.value} ${call.request.path()}")
            call.respond(status, Failure(error = "Bad Request"))
        }

        status(NotFound) { call, status ->
            Log.w(TAG, "Not Found (404): ${call.request.httpMethod.value} ${call.request.path()}")
            call.respond(status, Failure(error = "404"))
        }
    }

    install(ContentNegotiation) {

        val module = SerializersModule {

            contextual(Success::class as KClass<*>) {
                Success.serializer(AnySerializer)
            }

            contextual(io.ktor.server.routing.RoutingNode::class, RoutingNodeSerializer)
        }
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            serializersModule = module
        })
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            call.request.path().startsWith("/api")
        }
        format { call ->
            val userAgent = call.request.headers["User-Agent"] ?: "unknown"
            "Method: ${call.request.httpMethod.value}, Path: ${call.request.path()}, User-Agent: $userAgent"
        }
    }
}


