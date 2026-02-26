package com.azzahid.jezail

import com.azzahid.jezail.core.api.routes.intentRoutes
import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.utils.AnySerializer
import com.topjohnwu.superuser.Shell
import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
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

class IntentRoutesTest {

    private val mockShellJob = mockk<Shell.Job>(relaxed = true)
    private val mockResult = mockk<Shell.Result>(relaxed = true)

    @Before
    fun setup() {
        mockkObject(JezailApp.Companion)
        every { JezailApp.appContext } returns mockk(relaxed = true)
        mockkStatic(Shell::class)
        every { Shell.cmd(any<String>()) } returns mockShellJob
        every { mockShellJob.exec() } returns mockResult
    }

    @After
    fun teardown() {
        unmockkObject(JezailApp.Companion)
        unmockkStatic(Shell::class)
    }

    private fun Application.testModule() {
        install(OpenApi)
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, Failure(error = cause.message ?: "Invalid request"))
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
                intentRoutes()
            }
        }
    }

    @Test
    fun `POST start-activity with action succeeds`() = testApplication {
        application { testModule() }
        every { mockResult.isSuccess } returns true
        every { mockResult.out } returns listOf("Starting: Intent { act=android.intent.action.VIEW }")

        val response = client.post("/api/intent/start-activity") {
            contentType(ContentType.Application.Json)
            setBody("""{"action":"android.intent.action.VIEW"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Activity started", body["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST start-activity with full params succeeds`() = testApplication {
        application { testModule() }
        every { mockResult.isSuccess } returns true
        every { mockResult.out } returns listOf("OK")

        val response = client.post("/api/intent/start-activity") {
            contentType(ContentType.Application.Json)
            setBody("""{"action":"android.intent.action.MAIN","package":"com.example","class":".MainActivity","flags":["FLAG_ACTIVITY_NEW_TASK"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        verify { Shell.cmd(match<String> { it.startsWith("am start") && it.contains("-n") }) }
    }

    @Test
    fun `POST start-activity failure returns 500`() = testApplication {
        application { testModule() }
        every { mockResult.isSuccess } returns false
        every { mockResult.err } returns listOf("Error: Activity not found")

        val response = client.post("/api/intent/start-activity") {
            contentType(ContentType.Application.Json)
            setBody("""{"action":"android.intent.action.VIEW"}""")
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `POST broadcast succeeds`() = testApplication {
        application { testModule() }
        every { mockResult.isSuccess } returns true
        every { mockResult.out } returns listOf("Broadcast completed")

        val response = client.post("/api/intent/broadcast") {
            contentType(ContentType.Application.Json)
            setBody("""{"action":"android.intent.action.BATTERY_LOW"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Broadcast sent", body["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST broadcast with extras builds correct command`() = testApplication {
        application { testModule() }
        every { mockResult.isSuccess } returns true
        every { mockResult.out } returns listOf("OK")

        client.post("/api/intent/broadcast") {
            contentType(ContentType.Application.Json)
            setBody("""{"action":"custom.ACTION","extras":{"key1":"val1"}}""")
        }
        verify { Shell.cmd(match<String> { it.contains("am broadcast") && it.contains("--es") }) }
    }

    @Test
    fun `POST start-service succeeds`() = testApplication {
        application { testModule() }
        every { mockResult.isSuccess } returns true
        every { mockResult.out } returns listOf("Service started")

        val response = client.post("/api/intent/start-service") {
            contentType(ContentType.Application.Json)
            setBody("""{"action":"com.example.START","package":"com.example","class":".MyService"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Service started", body["data"]?.jsonPrimitive?.content)
    }
}
