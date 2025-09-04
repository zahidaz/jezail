package com.azzahid.jezail.core.utils

import android.util.Log
import android.webkit.MimeTypeMap
import com.azzahid.jezail.core.services.withRootFS
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLConnection
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


suspend fun getMimeType(path: String) = withRootFS { fs ->
    val extension = MimeTypeMap.getFileExtensionFromUrl(path).ifEmpty {
        File(path).extension
    }.lowercase()

    extension.takeIf { it.isNotEmpty() }
        ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: runCatching {
        fs.getFile(path).takeIf { it.exists() && it.isFile }?.newInputStream()?.use { input ->
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

fun copyFileContent(source: ExtendedFile, dest: ExtendedFile, overwrite: Boolean) {
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

fun copyDirectory(
    sourceDir: ExtendedFile, destDir: ExtendedFile, overwrite: Boolean
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

fun copySymlink(sourceLink: ExtendedFile, destLink: ExtendedFile, overwrite: Boolean) {
    if (destLink.exists() && !overwrite) {
        error("Destination exists: ${destLink.absolutePath}")
    }

    if (destLink.exists()) destLink.delete()
    ensureParentExists(destLink)

    require(destLink.createNewSymlink(sourceLink.canonicalPath)) {
        "Failed to create symlink: ${destLink.absolutePath}"
    }
}

fun ensureParentExists(file: ExtendedFile) {
    file.parentFile?.let { parent ->
        if (!parent.exists()) {
            require(parent.mkdirs()) { "Cannot create parent directory: ${parent.absolutePath}" }
        }
        require(parent.canWrite()) { "No write permission: ${parent.absolutePath}" }
    }
}

fun validateWritePermissions(oldParent: ExtendedFile?, newParent: ExtendedFile?) {
    oldParent?.let { require(it.canWrite()) { "No write permission to source directory: ${it.absolutePath}" } }
    newParent?.let {
        require(it.exists()) { "Destination directory does not exist: ${it.absolutePath}" }
        require(it.canWrite()) { "No write permission to destination directory: ${it.absolutePath}" }
    }
}

fun validateParentDirectory(parent: ExtendedFile?) {
    parent?.let {
        require(it.exists()) { "Parent directory does not exist: ${it.absolutePath}" }
        require(it.canWrite()) { "No write permission to parent directory: ${it.absolutePath}" }
    }
}

fun isValidPermissions(permissions: String) =
    permissions.matches(Regex("^[0-7]{3,4}$")) || permissions.matches(Regex("^[ugoa]*[+-=][rwxXstugo]*$"))

suspend fun executeFileOperation(
    path: String, value: String, command: String, operation: String
) = withRootFS { fs ->
    require(path.isNotBlank() && value.isNotBlank()) { "Path and $operation value cannot be empty" }

    fs.getFile(path).apply {
        require(exists()) { "File does not exist: $path" }
    }

    executeShellCommand("$command '$value' '$path'", operation)
}

suspend fun executeShellCommand(command: String, operation: String) = withContext(Dispatchers.IO) {
    Shell.cmd(command).exec().let { result ->
        require(result.isSuccess) { "Failed to $operation: ${result.err.joinToString()}" }
    }
}


suspend fun getPermissions(path: String) = withContext(Dispatchers.IO) {
    Shell.cmd("stat -c '%A %a' '$path'").exec().let { result ->
        if (result.isSuccess && result.out.isNotEmpty()) {
            result.out[0].split(" ").let { parts ->
                parts.getOrElse(0) { "unknown" } to parts.getOrElse(1) { "000" }
            }
        } else "unknown" to "000"
    }
}

suspend fun getOwnership(path: String) = withContext(Dispatchers.IO) {
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
): File {
    require(sourceDir.exists() && sourceDir.isDirectory) {
        "Source must be an existing directory"
    }
    val name = zipFileName.removeSuffix(".zip") + ".zip"
    val target = File(sourceDir.parentFile, name)
    return target.also {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(sourceDir).path
                Log.d("zipDirectory", "Zipping file: $relativePath")
                zos.putNextEntry(ZipEntry(relativePath))
                file.inputStream().buffered().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}


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

fun extractXZFile(inputFile: File, outputFile: File) {
    require(inputFile.exists())
    if (outputFile.exists()) outputFile.delete()
    outputFile.parentFile?.mkdirs()
    BufferedInputStream(FileInputStream(inputFile)).use { bis ->
        XZInputStream(bis).use { xzIn ->
            FileOutputStream(outputFile).use { out -> xzIn.copyTo(out) }
        }
    }
}