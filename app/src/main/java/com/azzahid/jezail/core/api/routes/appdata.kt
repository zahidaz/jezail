package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.topjohnwu.superuser.Shell
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

private fun sanitize(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"

private val packagePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$")
private val dbNamePattern = Regex("^[a-zA-Z0-9_.-]+$")
private val selectPattern = Regex("^\\s*SELECT\\b", RegexOption.IGNORE_CASE)

private fun validatePackage(pkg: String): String {
    require(packagePattern.matches(pkg)) { "Invalid package name: $pkg" }
    return pkg
}

private fun validateDbName(name: String): String {
    require(dbNamePattern.matches(name)) { "Invalid database name: $name" }
    require(!name.contains("..")) { "Invalid database name: $name" }
    return name
}

private suspend fun shellExec(command: String): Shell.Result =
    withContext(Dispatchers.IO) { Shell.cmd(command).exec() }

private fun parseSharedPrefsXml(xml: String): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    val entryPattern = Regex("""<(string|int|long|float|boolean|set)\s+name="([^"]+)"[^>]*(?:>([^<]*)</\1>|value="([^"]*)"[^/]*/?>)""")
    for (match in entryPattern.findAll(xml)) {
        val type = match.groupValues[1]
        val name = match.groupValues[2]
        val textContent = match.groupValues[3]
        val attrValue = match.groupValues[4]
        result[name] = when (type) {
            "string" -> textContent
            "int" -> attrValue.toIntOrNull()
            "long" -> attrValue.toLongOrNull()
            "float" -> attrValue.toFloatOrNull()
            "boolean" -> attrValue.toBooleanStrictOrNull()
            else -> textContent.ifEmpty { attrValue }
        }
    }
    return result
}

private fun parseSqliteJson(output: String): JsonElement {
    val trimmed = output.trim()
    if (trimmed.isEmpty()) return JsonArray(emptyList())
    return runCatching { Json.parseToJsonElement(trimmed) }.getOrElse { JsonArray(emptyList()) }
}

fun Route.appDataRoutes() {
    route("/appdata", {
        description = "Application data access endpoints for SharedPreferences and SQLite databases"
    }) {
        route("/{package}/shared-prefs") {
            get({
                description = "List SharedPreferences XML files for a package"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val result = shellExec("ls /data/data/${sanitize(pkg).trim('\'')}/shared_prefs/ 2>/dev/null")
                val files = result.out
                    .filter { it.isNotBlank() }
                    .map { it.trim() }
                call.respond(Success(files))
            }

            get("/{name}", {
                description = "Read and parse a SharedPreferences XML file"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                    pathParameter<String>("name") {
                        description = "SharedPreferences file name (e.g., prefs.xml)"
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val name = call.parameters.getOrFail("name")
                require(name.endsWith(".xml") && !name.contains("..") && !name.contains("/")) {
                    "Invalid preferences file name"
                }
                val path = "/data/data/${sanitize(pkg).trim('\'')}/shared_prefs/$name"
                val result = shellExec("cat ${sanitize(path)}")
                require(result.isSuccess) { "Failed to read preferences file: ${result.err.joinToString()}" }
                val xml = result.out.joinToString("\n")
                call.respond(Success(parseSharedPrefsXml(xml)))
            }

            put("/{name}/{key}", {
                description = "Update a value in a SharedPreferences XML file"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                    pathParameter<String>("name") {
                        description = "SharedPreferences file name (e.g., prefs.xml)"
                    }
                    pathParameter<String>("key") {
                        description = "Preference key to update"
                    }
                    queryParameter<String>("type") {
                        description = "Value type: string, int, long, float, or boolean (default: string)"
                        required = false
                    }
                    body<String> {
                        description = "New value as text"
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val name = call.parameters.getOrFail("name")
                require(name.endsWith(".xml") && !name.contains("..") && !name.contains("/")) {
                    "Invalid preferences file name"
                }
                val key = call.parameters.getOrFail("key")
                val value = call.receiveText()
                val type = call.request.queryParameters["type"] ?: "string"
                val path = "/data/data/${sanitize(pkg).trim('\'')}/shared_prefs/$name"

                val newEntry = when (type) {
                    "int" -> """<int name="$key" value="$value" />"""
                    "long" -> """<long name="$key" value="$value" />"""
                    "float" -> """<float name="$key" value="$value" />"""
                    "boolean" -> """<boolean name="$key" value="$value" />"""
                    else -> """<string name="$key">$value</string>"""
                }

                val readResult = shellExec("cat ${sanitize(path)}")
                require(readResult.isSuccess) { "Failed to read preferences file" }
                val xml = readResult.out.joinToString("\n")

                val keyPattern = """<(string|int|long|float|boolean)\s+name="$key"[^>]*(?:>.*?</\1>|/>)"""
                val updatedXml = if (Regex(keyPattern).containsMatchIn(xml)) {
                    xml.replace(Regex(keyPattern), newEntry)
                } else {
                    xml.replace("</map>", "    $newEntry\n</map>")
                }

                val writeResult = shellExec("echo ${sanitize(updatedXml)} > ${sanitize(path)}")
                require(writeResult.isSuccess) { "Failed to write preferences file" }
                call.respond(Success("Updated"))
            }

            delete("/{name}/{key}", {
                description = "Delete a key from a SharedPreferences XML file"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                    pathParameter<String>("name") {
                        description = "SharedPreferences file name (e.g., prefs.xml)"
                    }
                    pathParameter<String>("key") {
                        description = "Preference key to delete"
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val name = call.parameters.getOrFail("name")
                require(name.endsWith(".xml") && !name.contains("..") && !name.contains("/")) {
                    "Invalid preferences file name"
                }
                val key = call.parameters.getOrFail("key")
                val path = "/data/data/${sanitize(pkg).trim('\'')}/shared_prefs/$name"

                val keyPattern = """<(string|int|long|float|boolean)\s+name="$key"[^>]*(?:>.*?</\1>|/>)\n?"""
                val sedCmd = "sed -i -E '/${keyPattern.replace("/", "\\/")}/d' ${sanitize(path)}"
                val result = shellExec(sedCmd)
                require(result.isSuccess) { "Failed to delete key: ${result.err.joinToString()}" }
                call.respond(Success("Deleted"))
            }
        }

        route("/{package}/databases") {
            get({
                description = "List SQLite database files for a package"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val result = shellExec("ls /data/data/${sanitize(pkg).trim('\'')}/databases/ 2>/dev/null")
                val files = result.out
                    .filter { it.isNotBlank() && !it.endsWith("-journal") && !it.endsWith("-wal") && !it.endsWith("-shm") }
                    .map { it.trim() }
                call.respond(Success(files))
            }

            get("/{name}/tables", {
                description = "List all tables in a SQLite database"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                    pathParameter<String>("name") {
                        description = "Database file name (e.g., app.db)"
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val name = validateDbName(call.parameters.getOrFail("name"))
                val dbPath = "/data/data/${sanitize(pkg).trim('\'')}/databases/$name"
                val result = shellExec("sqlite3 ${sanitize(dbPath)} '.tables'")
                require(result.isSuccess) { "Failed to list tables: ${result.err.joinToString()}" }
                val tables = result.out
                    .flatMap { it.split(Regex("\\s+")) }
                    .filter { it.isNotBlank() }
                call.respond(Success(tables))
            }

            get("/{name}/schema/{table}", {
                description = "Get the schema of a specific table"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                    pathParameter<String>("name") {
                        description = "Database file name (e.g., app.db)"
                    }
                    pathParameter<String>("table") {
                        description = "Table name"
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val name = validateDbName(call.parameters.getOrFail("name"))
                val table = call.parameters.getOrFail("table")
                require(table.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) { "Invalid table name" }
                val dbPath = "/data/data/${sanitize(pkg).trim('\'')}/databases/$name"
                val result = shellExec("sqlite3 ${sanitize(dbPath)} '.schema $table'")
                require(result.isSuccess) { "Failed to get schema: ${result.err.joinToString()}" }
                call.respond(Success(result.out.joinToString("\n")))
            }

            get("/{name}/query", {
                description = "Execute a SELECT query on a SQLite database"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                    pathParameter<String>("name") {
                        description = "Database file name (e.g., app.db)"
                    }
                    queryParameter<String>("sql") {
                        description = "SQL SELECT query to execute"
                        required = true
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val name = validateDbName(call.parameters.getOrFail("name"))
                val sql = call.request.queryParameters["sql"]
                    ?: throw IllegalArgumentException("sql parameter is required")
                require(selectPattern.containsMatchIn(sql)) { "Only SELECT queries are allowed" }
                val dbPath = "/data/data/${sanitize(pkg).trim('\'')}/databases/$name"
                val result = shellExec("sqlite3 -json ${sanitize(dbPath)} ${sanitize(sql)}")
                require(result.isSuccess) { "Query failed: ${result.err.joinToString()}" }
                call.respond(Success(parseSqliteJson(result.out.joinToString("\n"))))
            }

            post("/{name}/execute", {
                description = "Execute a SQL statement (INSERT/UPDATE/DELETE) on a SQLite database"
                request {
                    pathParameter<String>("package") {
                        description = "Package name (e.g., com.example.app)"
                    }
                    pathParameter<String>("name") {
                        description = "Database file name (e.g., app.db)"
                    }
                    body<String> {
                        description = "SQL statement to execute"
                    }
                }
            }) {
                val pkg = validatePackage(call.parameters.getOrFail("package"))
                val name = validateDbName(call.parameters.getOrFail("name"))
                val sql = call.receiveText()
                require(sql.isNotBlank()) { "SQL statement cannot be empty" }
                val dbPath = "/data/data/${sanitize(pkg).trim('\'')}/databases/$name"
                val result = shellExec("sqlite3 ${sanitize(dbPath)} ${sanitize(sql)}")
                if (result.isSuccess) call.respond(Success("Executed"))
                else call.respond(InternalServerError, Failure(result.err.joinToString("\n").ifEmpty { "Execution failed" }))
            }
        }
    }
}
