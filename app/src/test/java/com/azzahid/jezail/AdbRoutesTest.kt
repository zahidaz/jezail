package com.azzahid.jezail

import com.azzahid.jezail.core.api.routes.adbRoutes
import com.azzahid.jezail.core.data.Preferences
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.utils.AnySerializer
import com.azzahid.jezail.features.managers.AdbManager
import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import com.azzahid.jezail.core.data.models.Failure
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

class AdbRoutesTest {

    @Before
    fun setup() {
        mockkObject(AdbManager)
        mockkObject(Preferences)
        mockkObject(JezailApp.Companion)
        every { JezailApp.appContext } returns io.mockk.mockk(relaxed = true)
    }

    @After
    fun teardown() {
        unmockkObject(AdbManager)
        unmockkObject(Preferences)
        unmockkObject(JezailApp.Companion)
    }

    private fun Application.testModule() {
        install(OpenApi)
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    Failure(error = cause.message ?: "Invalid request")
                )
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
                adbRoutes()
            }
        }
    }

    @Test
    fun `GET adb port returns configured port`() = testApplication {
        application { testModule() }
        every { AdbManager.getPort() } returns mapOf("port" to 5555)

        val response = client.get("/api/adb/port")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals(5555, data?.get("port")?.jsonPrimitive?.int)
    }

    @Test
    fun `POST adb port sets port`() = testApplication {
        application { testModule() }
        every { AdbManager.setPort(5556) } returns Unit
        every { AdbManager.getPort() } returns mapOf("port" to 5556)

        val response = client.post("/api/adb/port?port=5556")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { AdbManager.setPort(5556) }
    }

    @Test
    fun `POST adb port without port param returns 400`() = testApplication {
        application { testModule() }

        val response = client.post("/api/adb/port")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET adb status returns running state and ports`() = testApplication {
        application { testModule() }
        every { AdbManager.getStatus() } returns mapOf(
            "isRunning" to true,
            "activePort" to 5555,
            "preferredPort" to 5555
        )

        val response = client.get("/api/adb/status")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
    }
}
