package com.azzahid.jezail

import com.azzahid.jezail.core.api.routes.appDataRoutes
import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.utils.AnySerializer
import com.topjohnwu.superuser.Shell
import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
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

class AppDataRoutesTest {

    private val mockShellJob = mockk<Shell.Job>(relaxed = true)
    private val mockResult = mockk<Shell.Result>(relaxed = true)

    @Before
    fun setup() {
        mockkObject(JezailApp.Companion)
        every { JezailApp.appContext } returns mockk(relaxed = true)
        mockkStatic(Shell::class)
        every { Shell.cmd(any<String>()) } returns mockShellJob
        every { mockShellJob.exec() } returns mockResult
        every { mockResult.isSuccess } returns true
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
                appDataRoutes()
            }
        }
    }

    @Test
    fun `GET shared-prefs lists xml files`() = testApplication {
        application { testModule() }
        every { mockResult.out } returns listOf("prefs.xml", "settings.xml")

        val response = client.get("/api/appdata/com.example.app/shared-prefs")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonArray
        assertNotNull(data)
        assertEquals(2, data?.size)
    }

    @Test
    fun `GET shared-prefs with invalid package returns 400`() = testApplication {
        application { testModule() }

        val response = client.get("/api/appdata/invalid..pkg/shared-prefs")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET shared-prefs file parses xml correctly`() = testApplication {
        application { testModule() }
        every { mockResult.out } returns listOf(
            """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>""",
            """<map>""",
            """    <string name="username">testuser</string>""",
            """    <int name="count" value="42" />""",
            """    <boolean name="enabled" value="true" />""",
            """</map>"""
        )

        val response = client.get("/api/appdata/com.example.app/shared-prefs/prefs.xml")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("testuser", data?.get("username")?.jsonPrimitive?.content)
    }

    @Test
    fun `GET shared-prefs file rejects non-xml file`() = testApplication {
        application { testModule() }

        val response = client.get("/api/appdata/com.example.app/shared-prefs/passwd.txt")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET databases lists db files`() = testApplication {
        application { testModule() }
        every { mockResult.out } returns listOf("app.db", "cache.db", "app.db-journal", "app.db-wal")

        val response = client.get("/api/appdata/com.example.app/databases")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonArray
        assertNotNull(data)
        assertEquals(2, data?.size)
    }

    @Test
    fun `GET database tables returns table list`() = testApplication {
        application { testModule() }
        every { mockResult.out } returns listOf("users    sessions    tokens")

        val response = client.get("/api/appdata/com.example.app/databases/app.db/tables")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]?.jsonArray
        assertNotNull(data)
        assertEquals(3, data?.size)
    }

    @Test
    fun `GET database schema returns create statement`() = testApplication {
        application { testModule() }
        every { mockResult.out } returns listOf("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);")

        val response = client.get("/api/appdata/com.example.app/databases/app.db/schema/users")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["data"]?.jsonPrimitive?.content?.contains("CREATE TABLE") == true)
    }

    @Test
    fun `GET database schema rejects invalid table name`() = testApplication {
        application { testModule() }

        val response = client.get("/api/appdata/com.example.app/databases/app.db/schema/users;DROP TABLE")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET database query returns json results`() = testApplication {
        application { testModule() }
        every { mockResult.out } returns listOf("""[{"id":1,"name":"test"}]""")

        val response = client.get("/api/appdata/com.example.app/databases/app.db/query?sql=SELECT * FROM users")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET database query rejects non-SELECT`() = testApplication {
        application { testModule() }

        val response = client.get("/api/appdata/com.example.app/databases/app.db/query?sql=DELETE FROM users")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST database execute runs sql`() = testApplication {
        application { testModule() }

        val response = client.post("/api/appdata/com.example.app/databases/app.db/execute") {
            contentType(ContentType.Text.Plain)
            setBody("INSERT INTO users (name) VALUES ('test')")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Executed", body["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST database execute with empty sql returns 400`() = testApplication {
        application { testModule() }

        val response = client.post("/api/appdata/com.example.app/databases/app.db/execute") {
            contentType(ContentType.Text.Plain)
            setBody("")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET database query without sql param returns 400`() = testApplication {
        application { testModule() }

        val response = client.get("/api/appdata/com.example.app/databases/app.db/query")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET databases with invalid db name returns 400`() = testApplication {
        application { testModule() }

        val response = client.get("/api/appdata/com.example.app/databases/bad%20name!/tables")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
