package com.azzahid.jezail.core.api.routes

import com.azzahid.jezail.core.data.models.Failure
import com.azzahid.jezail.core.data.models.Success
import com.azzahid.jezail.features.managers.CertificateManager
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.ByteArrayOutputStream

fun Route.certificateRoutes() {
    route("/certs", {
        description = "Certificate management endpoints"
    }) {
        get("/system", {
            description = "List all system CA certificates"
        }) {
            call.respond(Success(CertificateManager.listSystemCerts()))
        }

        get("/user", {
            description = "List all user-installed CA certificates"
        }) {
            call.respond(Success(CertificateManager.listUserCerts()))
        }

        post("/install", {
            description = "Install a CA certificate"
            request {
                queryParameter<String>("type") {
                    description = "Certificate store type: 'system' or 'user'"
                    required = false
                }
                body<ByteArray> {
                    description = "Multipart form data containing the certificate file (PEM or DER)"
                }
            }
        }) {
            val type = call.request.queryParameters["type"] ?: "user"
            require(type in listOf("system", "user")) { "type must be 'system' or 'user'" }

            val multipart = call.receiveMultipart()
            var certBytes: ByteArray? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val baos = ByteArrayOutputStream()
                    part.provider().copyTo(baos)
                    certBytes = baos.toByteArray()
                    part.dispose()
                }
            }

            requireNotNull(certBytes) { "No certificate file provided" }

            val result = if (type == "system") {
                CertificateManager.installSystemCert(certBytes!!)
            } else {
                CertificateManager.installUserCert(certBytes!!)
            }

            call.respond(Success(result))
        }

        delete("/{hash}", {
            description = "Remove a CA certificate by hash"
            request {
                pathParameter<String>("hash") {
                    description = "Certificate subject hash (hex)"
                }
                queryParameter<String>("type") {
                    description = "Certificate store type: 'system' or 'user'"
                    required = false
                }
            }
        }) {
            val hash = call.parameters["hash"]
                ?: throw IllegalArgumentException("Certificate hash is required")
            val type = call.request.queryParameters["type"] ?: "user"
            require(type in listOf("system", "user")) { "type must be 'system' or 'user'" }

            val success = if (type == "system") {
                CertificateManager.removeSystemCert(hash)
            } else {
                CertificateManager.removeUserCert(hash)
            }

            if (success) {
                call.respond(Success("Certificate '$hash' removed from $type store"))
            } else {
                call.respond(InternalServerError, Failure("Failed to remove certificate"))
            }
        }
    }
}
