package com.azzahid.jezail

import com.azzahid.jezail.core.api.routes.deviceRoutes
import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.utils.AnySerializer
import com.azzahid.jezail.features.managers.DeviceManager
import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.client.request.delete
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
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

class DeviceNewRoutesTest {

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
                deviceRoutes()
            }
        }
    }

    @Test
    fun `GET env returns environment variables`() = testApplication {
        application { testModule() }
        every { DeviceManager.getEnvironmentVariables(null) } returns mapOf(
            "PATH" to "/usr/bin",
            "HOME" to "/root"
        )

        val response = client.get("/api/device/env")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("/usr/bin", data?.get("PATH")?.jsonPrimitive?.content)
    }

    @Test
    fun `GET env with filter passes filter param`() = testApplication {
        application { testModule() }
        every { DeviceManager.getEnvironmentVariables("PATH") } returns mapOf(
            "PATH" to "/usr/bin"
        )

        val response = client.get("/api/device/env?filter=PATH")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.getEnvironmentVariables("PATH") }
    }

    @Test
    fun `GET env with init=true returns init environment`() = testApplication {
        application { testModule() }
        every { DeviceManager.getInitEnvironment() } returns mapOf("INIT_VAR" to "value")

        val response = client.get("/api/device/env?init=true")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.getInitEnvironment() }
    }

    @Test
    fun `GET env variable by name`() = testApplication {
        application { testModule() }
        every { DeviceManager.getEnvironmentVariable("PATH") } returns "/usr/bin"

        val response = client.get("/api/device/env/PATH")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertEquals("PATH", data?.get("name")?.jsonPrimitive?.content)
        assertEquals("/usr/bin", data?.get("value")?.jsonPrimitive?.content)
    }

    @Test
    fun `GET proxy returns proxy config`() = testApplication {
        application { testModule() }
        every { DeviceManager.getProxy() } returns mapOf(
            "enabled" to true,
            "host" to "127.0.0.1",
            "port" to 8888,
            "exclusionList" to null
        )

        val response = client.get("/api/device/proxy")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("127.0.0.1", data?.get("host")?.jsonPrimitive?.content)
    }

    @Test
    fun `POST proxy sets proxy`() = testApplication {
        application { testModule() }
        every { DeviceManager.setProxy("127.0.0.1", 8888, null) } returns Unit
        every { DeviceManager.getProxy() } returns mapOf(
            "enabled" to true,
            "host" to "127.0.0.1",
            "port" to 8888,
            "exclusionList" to null
        )

        val response = client.post("/api/device/proxy?host=127.0.0.1&port=8888")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.setProxy("127.0.0.1", 8888, null) }
    }

    @Test
    fun `POST proxy without host returns 400`() = testApplication {
        application { testModule() }

        val response = client.post("/api/device/proxy?port=8888")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE proxy clears proxy`() = testApplication {
        application { testModule() }
        every { DeviceManager.clearProxy() } returns Unit

        val response = client.delete("/api/device/proxy")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.clearProxy() }
    }

    @Test
    fun `GET dns returns dns config`() = testApplication {
        application { testModule() }
        every { DeviceManager.getDnsConfig() } returns mapOf(
            "dns1" to "8.8.8.8",
            "dns2" to "8.8.4.4",
            "privateDnsMode" to null,
            "privateDnsSpecifier" to null
        )

        val response = client.get("/api/device/dns")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("8.8.8.8", data?.get("dns1")?.jsonPrimitive?.content)
    }

    @Test
    fun `POST dns sets dns servers`() = testApplication {
        application { testModule() }
        every { DeviceManager.setDns("8.8.8.8", "8.8.4.4") } returns Unit
        every { DeviceManager.getDnsConfig() } returns mapOf(
            "dns1" to "8.8.8.8",
            "dns2" to "8.8.4.4",
            "privateDnsMode" to null,
            "privateDnsSpecifier" to null
        )

        val response = client.post("/api/device/dns?dns1=8.8.8.8&dns2=8.8.4.4")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.setDns("8.8.8.8", "8.8.4.4") }
    }

    @Test
    fun `DELETE dns clears dns`() = testApplication {
        application { testModule() }
        every { DeviceManager.clearDns() } returns Unit

        val response = client.delete("/api/device/dns")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.clearDns() }
    }

    @Test
    fun `POST private dns sets hostname`() = testApplication {
        application { testModule() }
        every { DeviceManager.setPrivateDns("dns.example.com") } returns Unit
        every { DeviceManager.getDnsConfig() } returns mapOf(
            "dns1" to null,
            "dns2" to null,
            "privateDnsMode" to "hostname",
            "privateDnsSpecifier" to "dns.example.com"
        )

        val response = client.post("/api/device/dns/private?host=dns.example.com")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.setPrivateDns("dns.example.com") }
    }

    @Test
    fun `DELETE private dns clears`() = testApplication {
        application { testModule() }
        every { DeviceManager.clearPrivateDns() } returns Unit

        val response = client.delete("/api/device/dns/private")
        assertEquals(HttpStatusCode.OK, response.status)
        verify { DeviceManager.clearPrivateDns() }
    }
}
