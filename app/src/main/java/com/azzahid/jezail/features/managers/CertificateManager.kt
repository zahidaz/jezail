package com.azzahid.jezail.features.managers

import android.os.Build
import com.topjohnwu.superuser.Shell
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object CertificateManager {
    private const val SYSTEM_CERTS_DIR = "/system/etc/security/cacerts"
    private const val USER_CERTS_DIR = "/data/misc/user/0/cacerts-added"

    fun computeSubjectHashOld(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(cert.subjectX500Principal.encoded)
        val hash = (digest[0].toLong() and 0xFF) or
                ((digest[1].toLong() and 0xFF) shl 8) or
                ((digest[2].toLong() and 0xFF) shl 16) or
                ((digest[3].toLong() and 0xFF) shl 24)
        return "%08x".format(hash)
    }

    fun parseCertificate(bytes: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }

    fun encodeToPem(cert: X509Certificate): String {
        val encoded = java.util.Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(cert.encoded)
        return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----\n"
    }

    private val PEM_REGEX = Regex(
        "-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----",
        RegexOption.DOT_MATCHES_ALL
    )

    private fun parseCertFile(path: String): Map<String, Any?>? {
        val result = Shell.cmd("cat '$path'").exec()
        if (!result.isSuccess) return null
        val content = result.out.joinToString("\n")
        return try {
            val bytes = PEM_REGEX.find(content)?.let { match ->
                val b64 = match.groupValues[1].replace("\\s".toRegex(), "")
                java.util.Base64.getDecoder().decode(b64)
            } ?: content.toByteArray()
            val cert = parseCertificate(bytes)
            mapOf(
                "file" to path.substringAfterLast("/"),
                "hash" to path.substringAfterLast("/").removeSuffix(".0"),
                "subject" to cert.subjectDN.toString(),
                "issuer" to cert.issuerDN.toString(),
                "notBefore" to cert.notBefore.toString(),
                "notAfter" to cert.notAfter.toString(),
                "serialNumber" to cert.serialNumber.toString()
            )
        } catch (e: Exception) {
            mapOf(
                "file" to path.substringAfterLast("/"),
                "hash" to path.substringAfterLast("/").removeSuffix(".0"),
                "error" to "Failed to parse: ${e.message}"
            )
        }
    }

    fun listSystemCerts(): List<Map<String, Any?>> {
        val result = Shell.cmd("ls $SYSTEM_CERTS_DIR").exec()
        if (!result.isSuccess) return emptyList()
        return result.out.filter { it.endsWith(".0") }.mapNotNull { file ->
            parseCertFile("$SYSTEM_CERTS_DIR/$file")
        }
    }

    fun listUserCerts(): List<Map<String, Any?>> {
        val result = Shell.cmd("ls $USER_CERTS_DIR").exec()
        if (!result.isSuccess) return emptyList()
        return result.out.filter { it.endsWith(".0") }.mapNotNull { file ->
            parseCertFile("$USER_CERTS_DIR/$file")
        }
    }

    fun installUserCert(bytes: ByteArray): Map<String, String> {
        val cert = parseCertificate(bytes)
        val hash = computeSubjectHashOld(cert)
        val pem = encodeToPem(cert)
        val targetPath = "$USER_CERTS_DIR/$hash.0"

        Shell.cmd("mkdir -p $USER_CERTS_DIR").exec()
        val writeResult = Shell.cmd("echo '${pem.replace("'", "'\\''")}' > '$targetPath'").exec()
        check(writeResult.isSuccess) {
            "Failed to write certificate: ${writeResult.err.joinToString("\n")}"
        }
        Shell.cmd("chmod 644 '$targetPath'").exec()

        return mapOf("hash" to hash, "path" to targetPath, "subject" to cert.subjectDN.toString())
    }

    fun installSystemCert(bytes: ByteArray): Map<String, String> {
        val cert = parseCertificate(bytes)
        val hash = computeSubjectHashOld(cert)
        val pem = encodeToPem(cert)
        val targetPath = "$SYSTEM_CERTS_DIR/$hash.0"

        if (Build.VERSION.SDK_INT >= 34) {
            val tmpDir = "/data/local/tmp/cacerts_${System.currentTimeMillis()}"
            Shell.cmd("mkdir -p '$tmpDir'").exec()
            Shell.cmd("cp $SYSTEM_CERTS_DIR/* '$tmpDir/'").exec()
            Shell.cmd("echo '${pem.replace("'", "'\\''")}' > '$tmpDir/$hash.0'").exec()
            Shell.cmd("chmod 644 '$tmpDir/$hash.0'").exec()

            val mountResult = Shell.cmd("mount --bind '$tmpDir' $SYSTEM_CERTS_DIR").exec()
            check(mountResult.isSuccess) {
                Shell.cmd("rm -rf '$tmpDir'").exec()
                "Failed to bind mount certificates: ${mountResult.err.joinToString("\n")}"
            }
        } else {
            Shell.cmd("mount -o rw,remount /system").exec()
            val writeResult = Shell.cmd("echo '${pem.replace("'", "'\\''")}' > '$targetPath'").exec()
            check(writeResult.isSuccess) {
                Shell.cmd("mount -o ro,remount /system").exec()
                "Failed to write system certificate: ${writeResult.err.joinToString("\n")}"
            }
            Shell.cmd("chmod 644 '$targetPath'").exec()
            Shell.cmd("mount -o ro,remount /system").exec()
        }

        return mapOf("hash" to hash, "path" to targetPath, "subject" to cert.subjectDN.toString())
    }

    fun removeUserCert(hash: String): Boolean {
        require(hash.matches(Regex("[a-fA-F0-9]+"))) { "Invalid certificate hash" }
        val result = Shell.cmd("rm '$USER_CERTS_DIR/$hash.0'").exec()
        return result.isSuccess
    }

    fun removeSystemCert(hash: String): Boolean {
        require(hash.matches(Regex("[a-fA-F0-9]+"))) { "Invalid certificate hash" }

        if (Build.VERSION.SDK_INT >= 34) {
            val tmpDir = "/data/local/tmp/cacerts_${System.currentTimeMillis()}"
            Shell.cmd("mkdir -p '$tmpDir'").exec()
            Shell.cmd("cp $SYSTEM_CERTS_DIR/* '$tmpDir/'").exec()
            Shell.cmd("rm '$tmpDir/$hash.0'").exec()
            return Shell.cmd("mount --bind '$tmpDir' $SYSTEM_CERTS_DIR").exec().isSuccess
        } else {
            Shell.cmd("mount -o rw,remount /system").exec()
            val result = Shell.cmd("rm '$SYSTEM_CERTS_DIR/$hash.0'").exec()
            Shell.cmd("mount -o ro,remount /system").exec()
            return result.isSuccess
        }
    }
}
