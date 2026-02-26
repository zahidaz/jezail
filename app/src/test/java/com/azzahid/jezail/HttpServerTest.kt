package com.azzahid.jezail

import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.utils.AnySerializer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class HttpServerTest {

    private fun Application.testModule() {
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    Failure(error = cause.message ?: "Invalid request")
                )
            }
            exception<IllegalStateException> { call, cause ->
                call.respond(
                    HttpStatusCode.Conflict,
                    Failure(error = cause.message ?: "Request could not be completed")
                )
            }
            status(HttpStatusCode.NotFound) { call, status ->
                call.respond(status, Failure(error = "404"))
            }
        }

        install(ContentNegotiation) {
            val module = SerializersModule {
                @Suppress("UNCHECKED_CAST")
                contextual(Success::class as KClass<*>) {
                    Success.serializer(AnySerializer)
                }
            }
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                serializersModule = module
            })
        }

        routing {
            route("/api") {
                get("/status") {
                    call.respond(Success(data = mapOf("status" to "ok")))
                }
                get("/throw-illegal-argument") {
                    throw IllegalArgumentException("bad argument")
                }
                get("/throw-illegal-state") {
                    throw IllegalStateException("bad state")
                }
            }
        }
    }

    @Test
    fun `GET api status returns 200 with ok`() = testApplication {
        application { testModule() }
        val response = client.get("/api/status")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("ok", data?.get("status")?.jsonPrimitive?.content)
    }

    @Test
    fun `GET nonexistent returns 404`() = testApplication {
        application { testModule() }
        val response = client.get("/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `response Content-Type is JSON`() = testApplication {
        application { testModule() }
        val response = client.get("/api/status")
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)
    }

    @Test
    fun `IllegalArgumentException returns 400`() = testApplication {
        application { testModule() }
        val response = client.get("/api/throw-illegal-argument")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("bad argument", body["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `IllegalStateException returns 409`() = testApplication {
        application { testModule() }
        val response = client.get("/api/throw-illegal-state")
        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["error"])
    }
}
