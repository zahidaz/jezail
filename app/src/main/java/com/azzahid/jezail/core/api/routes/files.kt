package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.core.services.withRootFSFile
import com.azzahid.jezail.core.utils.zipDirectory
import com.azzahid.jezail.features.managers.FileManager
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

fun Route.filesRoutes() {
    route("/files", {
        description = "File system management endpoints"
    }) {
        val fileMutexes = ConcurrentHashMap<String, Mutex>()

        fun getMutexFor(path: String): Mutex {
            val normalizedPath = path.trim().replace("\\", "/") // Normalize for consistency
            return fileMutexes.computeIfAbsent(normalizedPath) { Mutex() }
        }

        get("/info", {
            description = "Get information about a file or directory"
            request {
                queryParameter<String>("path") {
                    description = "Path to the file or directory"
                    required = true
                }
            }
        }) {
            val path = call.request.queryParameters.getOrFail("path")
            call.respond(Success(FileManager.getFileInfo(path)))
        }

        get("/list", {
            description = "List contents of a directory"
            request {
                queryParameter<String>("path") {
                    description = "Path to the directory to list"
                    required = false
                }
            }
        }) {
            val path = call.request.queryParameters["path"] ?: "/"
            call.respond(Success(FileManager.listDirectory(path)))
        }

        get("/read", {
            description = "Read the contents of a text file"
            request {
                queryParameter<String>("path") {
                    description = "Path to the text file to read"
                    required = true
                }
            }
        }) {
            val path = call.request.queryParameters.getOrFail("path")
            val content = FileManager.readFile(path)
            call.respond(Success(mapOf("content" to content)))
        }

        post("/upload", {
            description = "Upload a file to the device"
            request {
                queryParameter<String>("path") {
                    description = "Destination path where the file should be saved"
                    required = true
                }
                body<ByteArray> {
                    description = "Multipart form data containing the file to upload"
                }
            }
        }) {
            val destPath = call.request.queryParameters.getOrFail("path")
            require(destPath.isNotBlank()) { "Destination path is required" }

            val mutex = getMutexFor(destPath)
            mutex.withLock {
                val multipart = call.receiveMultipart(formFieldLimit = 999L * 1024 * 1024)
                withRootFSFile(destPath) { df ->
                    require(!df.exists()) { "File exists: ${df.absolutePath}" }
                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            df.newOutputStream().use { output ->
                                part.provider().copyTo(output)
                            }
                        }
                        part.dispose()
                    }
                    call.respond(Success("File uploaded"))
                }
            }
        }

        get("/download", {
            description = "Download files or directories (zip) from the device"
            request {
                queryParameter<List<String>>("paths") {
                    description = "List of paths to files or directories to download"
                    required = true
                }
            }
        }) {
            val srcPaths = call.request.queryParameters.getAll("paths") ?: emptyList()
            require(srcPaths.isNotEmpty()) { "At least one path is required" }

            val sortedPaths = srcPaths.sorted()
            val mutexes = sortedPaths.map { getMutexFor(it) }

            mutexes.fold(Unit) { _, mutex -> mutex.lock() }
            val uuid = UUID.randomUUID().toString()
            val downloadDir =
                File(JezailApp.appContext.getExternalFilesDir(null), "download/$uuid")
            downloadDir.mkdirs()

            try {

                srcPaths.forEach { srcPath ->
                    withRootFSFile(srcPath) { src ->
                        val target = File(downloadDir, src.name)
                        if (src.isDirectory) src.copyRecursively(
                            target, overwrite = true
                        ) else src.copyTo(target, overwrite = true)
                    }
                }

                val name = if (srcPaths.size > 1) uuid else {
                    File(srcPaths.first()).name.removePrefix(".")
                }

                val d = if (srcPaths.size == 1 && withRootFSFile(srcPaths.first()) { it.isFile }) {
                    File(downloadDir, name)
                } else {
                    zipDirectory(downloadDir, name)
                }


                call.response.header(
                    "Content-Disposition", "attachment; filename='${d.name}'"
                )
                val contentType = ContentType.Application.OctetStream
                call.respondBytes(d.readBytes(), contentType)
                d.deleteRecursively()
            } finally {
                mutexes.reversed().forEach { it.unlock() }
                downloadDir.deleteRecursively()
            }
        }

        post("/write", {
            description = "Write content to a text file"
            request {
                queryParameter<String>("path") {
                    description = "Path where the file should be written"
                    required = true
                }
                body<String> {
                    description = "Text content to write to the file"
                }
            }
        }) {
            val path = call.request.queryParameters.getOrFail("path")
            val mutex = getMutexFor(path)
            mutex.withLock {
                val content = call.receiveText()
                FileManager.writeFile(path, content)
                call.respond(Success("File written"))
            }
        }

        post("/rename", {
            description = "Rename or move a file or directory"
            request {
                queryParameter<String>("oldPath") {
                    description = "Current path of the file or directory"
                    required = true
                }
                queryParameter<String>("newPath") {
                    description = "New path for the file or directory"
                    required = true
                }
            }
        }) {
            val oldPath = call.request.queryParameters.getOrFail("oldPath")
            val newPath = call.request.queryParameters.getOrFail("newPath")

            val sortedPaths = listOf(oldPath, newPath).sorted()
            val mutexes = sortedPaths.map { getMutexFor(it) }

            mutexes.fold(Unit) { _, mutex -> mutex.withLock { } }

            try {
                FileManager.renameFile(oldPath, newPath)
                call.respond(Success("File renamed"))
            } finally {
                mutexes.reversed().forEach { it.unlock() }
            }
        }

        post("/mkdir", {
            description = "Create a new directory"
            request {
                queryParameter<String>("path") {
                    description = "Path where the directory should be created"
                    required = true
                }
            }
        }) {
            val path = call.request.queryParameters.getOrFail("path")
            val mutex = getMutexFor(path)
            mutex.withLock {
                FileManager.createDirectory(path)
                call.respond(Success("Directory created"))
            }
        }

        post("/chmod", {
            description = "Change file or directory permissions"
            request {
                queryParameter<String>("path") {
                    description = "Path to the file or directory"
                    required = true
                }
                queryParameter<String>("permissions") {
                    description = "Permissions in octal format (e.g., 755, 644)"
                    required = true
                }
            }
        }) {
            val path = call.request.queryParameters.getOrFail("path")
            val mutex = getMutexFor(path)
            mutex.withLock {
                val perm = call.request.queryParameters["permissions"].orEmpty()
                FileManager.changePermissions(path, perm)
                call.respond(Success("Permissions changed"))
            }
        }

        post("/chown", {
            description = "Change file or directory ownership"
            request {
                queryParameter<String>("path") {
                    description = "Path to the file or directory"
                    required = true
                }
                queryParameter<String>("owner") {
                    description = "New owner for the file or directory (root, shell, system ..)"
                    required = true
                }
            }
        }) {
            val path = call.request.queryParameters.getOrFail("path")
            val mutex = getMutexFor(path)
            mutex.withLock {
                val owner = call.request.queryParameters.getOrFail("owner")
                FileManager.changeOwnership(path, owner)
                call.respond(Success("Ownership changed"))
            }
        }

        post("/chgrp", {
            description = "Change file or directory group"
            request {
                queryParameter<String>("path") {
                    description = "Path to the file or directory"
                    required = true
                }
                queryParameter<String>("group") {
                    description = "New group for the file or directory"
                    required = true
                }
            }
        }) {
            val path = call.request.queryParameters.getOrFail("path")
            val mutex = getMutexFor(path)
            mutex.withLock {
                val group = call.request.queryParameters.getOrFail("group")
                FileManager.changeGroup(path, group)
                call.respond(Success("Group changed"))
            }
        }

        delete({
            description = "Delete a file or directory"
            request {
                queryParameter<String>("path") {
                    description = "Path to the file or directory to delete"
                    required = true
                }
            }
        }) {
            val path = call.request.queryParameters.getOrFail("path")
            val mutex = getMutexFor(path)
            mutex.withLock {
                FileManager.removeFile(path)
                call.respond(Success("File removed"))
            }
        }
    }
}
