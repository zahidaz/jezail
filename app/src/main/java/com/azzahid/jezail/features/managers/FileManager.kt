package com.azzahid.jezail.features.managers

import android.content.Context
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import com.azzahid.jezail.JezailApp
import com.azzahid.jezail.features.withRootFS
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

private suspend fun getMimeType(path: String) = withRootFS { fs ->
    val extension = MimeTypeMap.getFileExtensionFromUrl(path).ifEmpty {
        File(path).extension
    }.lowercase()

    extension.takeIf { it.isNotEmpty() }
        ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        ?: runCatching {
            fs.getFile(path).takeIf { it.exists() && it.isFile }
                ?.newInputStream()?.use { input ->
                    ByteArray(512).let { buffer ->
                        input.read(buffer).takeIf { it > 0 }?.let { bytesRead ->
                            URLConnection.guessContentTypeFromStream(
                                ByteArrayInputStream(buffer, 0, bytesRead)
                            )
                        }
                    }
                }
        }.getOrNull()
}

private fun copyFileContent(source: ExtendedFile, dest: ExtendedFile, overwrite: Boolean) {
    val target = if (dest.exists() && dest.isDirectory) dest.getChildFile(source.name) else dest

    if (target.exists() && !overwrite) {
        error("Destination exists: ${target.absolutePath}")
    }

    ensureParentExists(target)
    source.newInputStream().use { input ->
        target.newOutputStream().use { output ->
            input.copyTo(output, 65536)
        }
    }
    target.setLastModified(source.lastModified())
}

private suspend fun copyDirectory(
    sourceDir: ExtendedFile,
    destDir: ExtendedFile,
    overwrite: Boolean
) {
    if (destDir.exists() && !overwrite) {
        error("Destination directory exists: ${destDir.absolutePath}")
    }

    if (!destDir.exists()) {
        require(destDir.mkdirs()) { "Failed to create directory: ${destDir.absolutePath}" }
    }

    sourceDir.listFiles()?.forEach { child ->
        val destChild = destDir.getChildFile(child.name)
        when {
            child.isFile -> copyFileContent(child, destChild, overwrite)
            child.isDirectory -> copyDirectory(child, destChild, overwrite)
            child.isSymlink -> copySymlink(child, destChild, overwrite)
        }
    } ?: error("Cannot list directory: ${sourceDir.absolutePath}")
}

private fun copySymlink(sourceLink: ExtendedFile, destLink: ExtendedFile, overwrite: Boolean) {
    if (destLink.exists() && !overwrite) {
        error("Destination exists: ${destLink.absolutePath}")
    }

    if (destLink.exists()) destLink.delete()
    ensureParentExists(destLink)

    require(destLink.createNewSymlink(sourceLink.canonicalPath)) {
        "Failed to create symlink: ${destLink.absolutePath}"
    }
}

private fun ensureParentExists(file: ExtendedFile) {
    file.parentFile?.let { parent ->
        if (!parent.exists()) {
            require(parent.mkdirs()) { "Cannot create parent directory: ${parent.absolutePath}" }
        }
        require(parent.canWrite()) { "No write permission: ${parent.absolutePath}" }
    }
}

private fun isSubPath(childPath: String, parentPath: String): Boolean {
    val child = File(childPath).canonicalPath
    val parent = File(parentPath).canonicalPath
    return child.startsWith("$parent${File.separator}")
}

private fun validateWritePermissions(oldParent: ExtendedFile?, newParent: ExtendedFile?) {
    oldParent?.let { require(it.canWrite()) { "No write permission to source directory: ${it.absolutePath}" } }
    newParent?.let {
        require(it.exists()) { "Destination directory does not exist: ${it.absolutePath}" }
        require(it.canWrite()) { "No write permission to destination directory: ${it.absolutePath}" }
    }
}

private fun validateParentDirectory(parent: ExtendedFile?) {
    parent?.let {
        require(it.exists()) { "Parent directory does not exist: ${it.absolutePath}" }
        require(it.canWrite()) { "No write permission to parent directory: ${it.absolutePath}" }
    }
}

private fun isValidPermissions(permissions: String) =
    permissions.matches(Regex("^[0-7]{3,4}$")) ||
            permissions.matches(Regex("^[ugoa]*[+-=][rwxXstugo]*$"))

private suspend fun executeFileOperation(
    path: String,
    value: String,
    command: String,
    operation: String
) = withRootFS { fs ->
    require(path.isNotBlank() && value.isNotBlank()) { "Path and $operation value cannot be empty" }

    fs.getFile(path).apply {
        require(exists()) { "File does not exist: $path" }
    }

    executeShellCommand("$command '$value' '$path'", operation)
}

private suspend fun executeShellCommand(command: String, operation: String) =
    withContext(Dispatchers.IO) {
        Shell.cmd(command).exec().let { result ->
            require(result.isSuccess) { "Failed to $operation: ${result.err.joinToString()}" }
        }
    }

private suspend fun getPermissions(path: String) = withContext(Dispatchers.IO) {
    Shell.cmd("stat -c '%A %a' '$path'").exec().let { result ->
        if (result.isSuccess && result.out.isNotEmpty()) {
            result.out[0].split(" ").let { parts ->
                parts.getOrElse(0) { "unknown" } to parts.getOrElse(1) { "000" }
            }
        } else "unknown" to "000"
    }
}

private suspend fun getOwnership(path: String) = withContext(Dispatchers.IO) {
    Shell.cmd("stat -c '%U %G' '$path'").exec().let { result ->
        if (result.isSuccess && result.out.isNotEmpty()) {
            result.out[0].split(" ").let { parts ->
                parts.getOrElse(0) { "unknown" } to parts.getOrElse(1) { "unknown" }
            }
        } else "unknown" to "unknown"
    }
}

fun zipDirectory(
    sourceDir: File,
    zipFileName: String = sourceDir.name
): File? = runCatching {
    require(sourceDir.exists() && sourceDir.isDirectory) {
        "Source must be an existing directory"
    }

    val zipFile = File(sourceDir.parent, "$zipFileName.zip")

    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
        sourceDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(sourceDir).path
                zos.putNextEntry(ZipEntry(relativePath))
                file.inputStream().buffered().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
    }

    zipFile
}.getOrNull()

fun ExtendedFile.moveTo(destination: File): Boolean = runCatching {
    destination.parentFile?.mkdirs()
    if (renameTo(destination)) return@runCatching true
    if (isDirectory) {
        destination.mkdirs()
        walkTopDown().forEach { src ->
            val dest = File(destination, src.relativeTo(this).path)
            if (src.isDirectory) dest.mkdirs()
            else src.copyTo(dest, true)
        }
        deleteRecursively()
    } else {
        copyTo(destination, true)
        delete()
    }
    true
}.getOrElse { false }