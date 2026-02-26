package com.azzahid.jezail

import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.utils.AnySerializer
import com.azzahid.jezail.features.managers.AuthManager
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

class AuthRoutesTest {

    @Before
    fun setup() {
        mockkObject(AuthManager)
    }

    @After
    fun teardown() {
        unmockkObject(AuthManager)
    }

    private val bypassPaths = listOf("/pair", "/api/status")

    private fun Application.authTestModule() {
        install(StatusPages) {
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

        val testAuthPlugin = createApplicationPlugin(name = "TestAuthPlugin") {
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
        install(testAuthPlugin)

        routing {
            post("/pair") {
                val body = runCatching {
                    Json.parseToJsonElement(call.receiveText()).jsonObject
                }.getOrNull()
                val pin = body?.get("pin")?.jsonPrimitive?.content
                if (pin == null) {
                    call.respond(HttpStatusCode.BadRequest, Failure(error = "Missing pin"))
                    return@post
                }
                val result = AuthManager.pair(pin)
                if (result != null) {
                    call.respond(Success(data = mapOf("token" to result)))
                } else {
                    call.respond(HttpStatusCode.Forbidden, Failure(error = "Invalid PIN"))
                }
            }

            route("/api") {
                get("/status") {
                    call.respond(Success(data = mapOf("status" to "ok")))
                }
                get("/device/env") {
                    call.respond(Success(data = mapOf("env" to "test")))
                }
            }
        }
    }

    @Test
    fun `POST pair with correct pin returns token`() = testApplication {
        every { AuthManager.isEnabled() } returns true
        every { AuthManager.pair("123456") } returns "test-token-abc"
        application { authTestModule() }

        val response = client.post("/pair") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"pin":"123456"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("test-token-abc", data?.get("token")?.jsonPrimitive?.content)
    }

    @Test
    fun `POST pair with wrong pin returns 403`() = testApplication {
        every { AuthManager.isEnabled() } returns true
        every { AuthManager.pair("000000") } returns null
        application { authTestModule() }

        val response = client.post("/pair") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `protected route without token returns 401 when auth enabled`() = testApplication {
        every { AuthManager.isEnabled() } returns true
        every { AuthManager.validateToken(any()) } returns false
        application { authTestModule() }

        val response = client.get("/api/device/env")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `protected route with valid token returns 200`() = testApplication {
        every { AuthManager.isEnabled() } returns true
        every { AuthManager.validateToken("valid-token") } returns true
        application { authTestModule() }

        val response = client.get("/api/device/env") {
            header(HttpHeaders.Authorization, "Bearer valid-token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `status route works without token`() = testApplication {
        every { AuthManager.isEnabled() } returns true
        application { authTestModule() }

        val response = client.get("/api/status")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `pair route works without token`() = testApplication {
        every { AuthManager.isEnabled() } returns true
        every { AuthManager.pair("123456") } returns "token"
        application { authTestModule() }

        val response = client.post("/pair") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"pin":"123456"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `protected route with valid cookie returns 200`() = testApplication {
        every { AuthManager.isEnabled() } returns true
        every { AuthManager.validateToken("cookie-token") } returns true
        application { authTestModule() }

        val response = client.get("/api/device/env") {
            cookie("jezail_token", "cookie-token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
