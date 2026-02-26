package com.azzahid.jezail

import com.azzahid.jezail.core.api.routes.deviceRoutes
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.utils.AnySerializer
import com.azzahid.jezail.features.managers.DeviceManager
import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
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

class DeviceRoutesTest {

    @Before
    fun setup() {
        mockkObject(DeviceManager)
        mockkObject(JezailApp.Companion)
        every { JezailApp.appContext } returns io.mockk.mockk(relaxed = true)
    }

    @After
    fun teardown() {
        unmockkObject(DeviceManager)
        unmockkObject(JezailApp.Companion)
    }

    private fun Application.testModule() {
        install(WebSockets)
        install(OpenApi)

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
                deviceRoutes()
            }
        }
    }

    @Test
    fun `GET device battery returns 200 with battery info`() = testApplication {
        application { testModule() }
        val mockBattery = mapOf<String, Any?>(
            "level" to 85,
            "status" to 2,
            "plugged" to true
        )
        every { DeviceManager.getBatteryInfo(any()) } returns mockBattery

        val response = client.get("/api/device/battery")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals(85, data?.get("level")?.jsonPrimitive?.int)
    }

    @Test
    fun `GET device logs with invalid lines param uses default 100`() = testApplication {
        application { testModule() }
        every { DeviceManager.getMainLogs(any(), any()) } returns listOf("log line 1")

        val response = client.get("/api/device/logs?lines=abc")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.getMainLogs(100, null) }
    }

    @Test
    fun `POST device keys home returns 200 with success`() = testApplication {
        application { testModule() }
        every { DeviceManager.pressHomeKey() } returns true

        val response = client.post("/api/device/keys/home")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["data"])
    }
}
