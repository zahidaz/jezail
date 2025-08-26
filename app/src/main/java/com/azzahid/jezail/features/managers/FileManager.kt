package com.azzahid.jezail.features.managers

import android.content.Context
import android.text.format.Formatter
import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.core.services.withRootFS
import com.azzahid.jezail.core.utils.executeFileOperation
import com.azzahid.jezail.core.utils.executeShellCommand
import com.azzahid.jezail.core.utils.getMimeType
import com.azzahid.jezail.core.utils.getOwnership
import com.azzahid.jezail.core.utils.getPermissions
import com.azzahid.jezail.core.utils.isValidPermissions
import com.azzahid.jezail.core.utils.validateParentDirectory
import com.azzahid.jezail.core.utils.validateWritePermissions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileManager {

    suspend fun listDirectory(path: String) = withRootFS { fs ->
        require(path.isNotBlank()) { "Path cannot be empty" }

        val directory = fs.getFile(path).apply {
            require(exists()) { "Directory does not exist: $path" }
            require(isDirectory) { "Path is not a directory: $path" }
            require(canRead()) { "Directory is not readable: $path" }
        }

        directory.listFiles()?.map { getFileInfo(it.absolutePath) }
            ?.sortedWith(compareBy({ !(it["isDirectory"] as Boolean) }, { it["name"] as String }))
            ?: error("Failed to list directory contents")
    }

    suspend fun getFileInfo(path: String) = withRootFS { fs ->
        require(path.isNotBlank()) { "Path cannot be empty" }

        val file = fs.getFile(path)

        val (permissions, octal) = getPermissions(path)
        val (owner, group) = getOwnership(path)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        buildMap {
            put("path", path)
            put("absolutePath", file.absolutePath)
            put("name", file.name)
            put("parent", file.parent)
            put("isDirectory", file.isDirectory)
            put("isFile", file.isFile)
            put("isHidden", file.isHidden)
            put("size", file.takeIf { it.isFile }?.length())
            put("sizeFormatted", file.formattedSize(JezailApp.appContext))
            put("lastModified", file.lastModified())
            put("lastModifiedFormatted", dateFormat.format(Date(file.lastModified())))
            put("readable", file.canRead())
            put("writable", file.canWrite())
            put("executable", file.canExecute())
            put("isSymbolicLink", file.isSymlink)
            put("permissions", permissions)
            put("permissionsOctal", octal)
            put("owner", owner)
            put("group", group)
            put("mimeType", file.takeIf { it.isFile }?.let { getMimeType(path) })
            put("childCount", file.takeIf { it.isDirectory }?.listFiles()?.size)
        }
    }

    suspend fun renameFile(oldPath: String, newPath: String) = withRootFS { fs ->
        require(oldPath.isNotBlank() && newPath.isNotBlank()) { "Paths cannot be empty" }
        require(oldPath != newPath) { "Paths must be different" }

        val oldFile = fs.getFile(oldPath).apply {
            require(exists()) { "File does not exist: $oldPath" }
        }

        val newFile = fs.getFile(newPath).apply {
            require(!exists()) { "Destination already exists: $newPath" }
        }

        validateWritePermissions(oldFile.parentFile, newFile.parentFile)
        require(oldFile.renameTo(newFile)) { "Failed to rename file" }
    }

    suspend fun removeFile(path: String) = withRootFS { fs ->
        require(path.isNotBlank()) { "Path cannot be empty" }
        require(
            path !in setOf(
                "/",
                "/system",
                "/data"
            )
        ) { "Cannot delete critical system path: $path" }

        val file = fs.getFile(path).apply {
            require(exists()) { "File does not exist: $path" }
        }

        file.parentFile?.let { parent ->
            require(parent.canWrite()) { "No write permission to parent directory: ${parent.absolutePath}" }
        }

        val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
        require(success) { "Failed to delete file" }
    }

    fun File.formattedSize(context: Context): String? =
        takeIf { isFile }?.let { Formatter.formatFileSize(context, length()) }

    suspend fun changeOwnership(path: String, owner: String) =
        executeFileOperation(path, owner, "chown", "change ownership")

    suspend fun changeGroup(path: String, group: String) =
        executeFileOperation(path, group, "chgrp", "change group")

    suspend fun changePermissions(path: String, permissions: String) = withRootFS { fs ->
        require(path.isNotBlank() && permissions.isNotBlank()) { "Path and permissions cannot be empty" }
        require(isValidPermissions(permissions)) { "Invalid permissions format: $permissions" }

        fs.getFile(path).apply {
            require(exists()) { "File does not exist: $path" }
        }

        executeShellCommand("chmod '$permissions' '$path'", "change permissions")
    }

    suspend fun createDirectory(path: String) = withRootFS { fs ->
        require(path.isNotBlank()) { "Path cannot be empty" }

        val directory = fs.getFile(path).apply {
            require(!exists()) { "Directory already exists: $path" }
        }

        validateParentDirectory(directory.parentFile)
        require(directory.mkdirs()) { "Failed to create directory" }
    }

    suspend fun readFile(path: String) = withRootFS { fs ->
        require(path.isNotBlank()) { "Path cannot be empty" }

        val file = fs.getFile(path).apply {
            require(exists()) { "File does not exist: $path" }
            require(isFile) { "Path is not a file: $path" }
            require(canRead()) { "File is not readable: $path" }
            require(length() <= 10 * 1024 * 1024) { "File too large (>10MB): $path" }
        }

        file.newInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
    }

    suspend fun writeFile(path: String, content: String) = withRootFS { fs ->
        require(path.isNotBlank()) { "Path cannot be empty" }

        val file = fs.getFile(path)
        validateParentDirectory(file.parentFile)

        if (file.exists()) {
            require(file.canWrite()) { "File is not writable: $path" }
        }

        file.newOutputStream().use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

}
