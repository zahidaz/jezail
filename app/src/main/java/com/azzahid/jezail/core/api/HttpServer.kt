package com.azzahid.jezail.core.api

import android.util.Log
import com.azzahid.jezail.core.api.routes.adbRoutes
import com.azzahid.jezail.core.api.routes.deviceRoutes
import com.azzahid.jezail.core.api.routes.filesRoutes
import com.azzahid.jezail.core.api.routes.fridaRoutes
import com.azzahid.jezail.core.api.routes.packageRoutes
import com.azzahid.jezail.core.data.models.AssetsResourceProvider
import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.data.models.respondAsset
import com.azzahid.jezail.core.utils.AnySerializer
import com.azzahid.jezail.core.utils.RoutingNodeSerializer
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
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
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
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

fun Application.configureRouting() {
    val webFiles = AssetsResourceProvider("web")

    routing {

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
                openApi()
            }

            route("/swagger") {
                swaggerUI("/api/json") {
                    // Add configuration for this Swagger UI "instance" here.
                }
            }

            adbRoutes()
            fridaRoutes()
            deviceRoutes()
            packageRoutes()
            filesRoutes()
        }

        get {
            call.respondRedirect("/index.html", permanent = false)
        }

        get("/{file...}") {
            val path = call.parameters.getAll("file")?.joinToString("/")
            if (path == null) call.respond(NotFound)
            val resource = webFiles.getResource(path!!)
            if (resource != null) call.respondAsset(resource)
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

    install(OpenApi)


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


