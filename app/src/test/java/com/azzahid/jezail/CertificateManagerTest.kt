package com.azzahid.jezail

import com.azzahid.jezail.features.managers.CertificateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class CertificateManagerTest {

    private val testPem1 = """
-----BEGIN CERTIFICATE-----
MIIC1DCCAbygAwIBAgIJAIC9YvgAU4UIMA0GCSqGSIb3DQEBDAUAMBgxFjAUBgNV
BAMTDVRlc3QgQ2VydCBPbmUwHhcNMjYwMjI2MDAzNDA0WhcNMjcwMjI2MDAzNDA0
WjAYMRYwFAYDVQQDEw1UZXN0IENlcnQgT25lMIIBIjANBgkqhkiG9w0BAQEFAAOC
AQ8AMIIBCgKCAQEAurA4owaHcm7r0MyeYa4dlBDe+k9sL2LSchg3nsjQJdT+ilPg
1RgJr2tsQP3oUVKVz5LEPRUbkVDiBQnkJbAGnvVEHOUZTmPnu62dENhbm5SbXL+U
fvV2BxoLwpIfdJJGicVsnuEoIDUNk2zUs37V0xj7NBbs7jIudapkCIyQiq3ur7EZ
UINwOTBZIw5rWemfhdE2SbTry8S1nZE+SFRpFRjQLuifGwvypGocmfCh5llHO/Se
21Ia55gGZEzS/krrt3JTBXx2SxiANEormY44kdxMR/FmOeGIsZRTv2GfojbLG2a0
6/keAiEKp+vGUWS5qYUwIkB4NYk+AumOAS3BSQIDAQABoyEwHzAdBgNVHQ4EFgQU
OhPlgIrI5pSGERzvo4sJfsbVn+UwDQYJKoZIhvcNAQEMBQADggEBAEP2Hvg0adFy
qN5l4p/gGu2eP33EpiI2LeZ0V5EMgbmLAO+2t2GqHnFLbSct/IJKr/eB2DC50NVm
NY5j0v0tCnoiZrlSvRIesleQM6g29MDf6q+1Y64CDCOpnF9tk69GsFoY7FYxG1Fe
dOjAdMXnUnd7no3QIa4fBTQ4qQadpYU1ICZ6O263Gz8XVgMhncnGamx1NN8WHIqZ
VwJnr0mUQS48in0KdK6HNlC9DGLnMI0nmT/tGbBkxaBvIxq5aj4ggcOeRrYAfRZA
+DgPy0T911uE1UIzRIFzRJYUC6CNkUKL/6jeEtlWmgNwGcResS8USJvDEGRO/Rmz
uXZ4ld2hKGE=
-----END CERTIFICATE-----
    """.trimIndent()

    private val testPem2 = """
-----BEGIN CERTIFICATE-----
MIIC0zCCAbugAwIBAgIIFxBR4yd6kZAwDQYJKoZIhvcNAQEMBQAwGDEWMBQGA1UE
AxMNVGVzdCBDZXJ0IFR3bzAeFw0yNjAyMjYwMDM0MTJaFw0yNzAyMjYwMDM0MTJa
MBgxFjAUBgNVBAMTDVRlc3QgQ2VydCBUd28wggEiMA0GCSqGSIb3DQEBAQUAA4IB
DwAwggEKAoIBAQDRz3G52MN0/9I/XSIJNV/SMn7jJUHCbtLy7VactaiYCqRz3x1c
dGWbSpmKtPzTMSB05PTsK0NjX+eUmNExzCTN553ZxT0/oxgZpJSY8J53lRtGLrds
8OEPTOL++u1lDqCI7sNNwIOSt1oUxbu16lTnq87pPdp4TczIVtrrzhlaOG2b/+jw
HAdLeQPb5gSwgsdI/vid2WQmUGbbXj9J5kUb1wUSRD50r26kaeG6neS1vPQg8Tu9
15qOzlZuF32Q4xWRrk8fgcEk98CrlcXqZ4jsh0dCJDllZEFb8FlVnD621Lis8MU3
1/GCAUpFwiRi86AJs80FPRCT6kYUTay9Xb1RAgMBAAGjITAfMB0GA1UdDgQWBBRs
sCSQTWjZG/VuqPTDxOmHApRXjDANBgkqhkiG9w0BAQwFAAOCAQEAsaDSij92sGc6
+Fwc938A/mDsviFOgGqDdhDtHRuKJuCTj08LGRrUJyejQl86u9ak6KW0v+h76PPb
tRj6/T8lwahEnJyyPxq7Lij3ZO+L68QpZPyuYYypWnNcx90Z4mWir5dz2i8I/PjJ
cT5v6IgF5M1hqFXmrMdLQpqH4JLzlZg5SacJJXnkRHXBy3LjzeTeubZwg9rAoxcg
zs77/FhOqSeYW3qMRiZDktDBzj3bl2i8nHfxFT7J+i8359p/2LEFs5SQE5rlPE92
ETWCYMWmcmjH9h2PGgnzu+A1w0VfBGf2UbtzVrYlnM71sbyp/UPoisd7f5sTpHcT
R5RlbkTLTw==
-----END CERTIFICATE-----
    """.trimIndent()

    private fun loadCert(pem: String): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(pem.byteInputStream()) as X509Certificate
    }

    @Test
    fun `computeSubjectHashOld returns 8 char hex string`() {
        val cert = loadCert(testPem1)
        val hash = CertificateManager.computeSubjectHashOld(cert)
        assertEquals(8, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{8}")))
    }

    @Test
    fun `computeSubjectHashOld is deterministic`() {
        val cert = loadCert(testPem1)
        val hash1 = CertificateManager.computeSubjectHashOld(cert)
        val hash2 = CertificateManager.computeSubjectHashOld(cert)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `computeSubjectHashOld differs for different subjects`() {
        val cert1 = loadCert(testPem1)
        val cert2 = loadCert(testPem2)
        val hash1 = CertificateManager.computeSubjectHashOld(cert1)
        val hash2 = CertificateManager.computeSubjectHashOld(cert2)
        assertTrue("Hashes should differ for different subjects", hash1 != hash2)
    }

    @Test
    fun `parseCertificate parses PEM bytes`() {
        val cert = CertificateManager.parseCertificate(testPem1.toByteArray())
        assertNotNull(cert)
        assertTrue(cert.subjectDN.toString().contains("Test Cert One"))
    }

    @Test
    fun `parseCertificate parses DER bytes`() {
        val pemCert = loadCert(testPem1)
        val derBytes = pemCert.encoded
        val cert = CertificateManager.parseCertificate(derBytes)
        assertEquals(pemCert.subjectDN.toString(), cert.subjectDN.toString())
    }

    @Test
    fun `encodeToPem produces valid PEM format`() {
        val cert = loadCert(testPem1)
        val pem = CertificateManager.encodeToPem(cert)
        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----"))
        assertTrue(pem.trimEnd().endsWith("-----END CERTIFICATE-----"))
    }

    @Test
    fun `encodeToPem roundtrips through parseCertificate`() {
        val original = loadCert(testPem1)
        val pem = CertificateManager.encodeToPem(original)
        val parsed = CertificateManager.parseCertificate(pem.toByteArray())
        assertEquals(original.serialNumber, parsed.serialNumber)
        assertEquals(original.subjectDN.toString(), parsed.subjectDN.toString())
    }

    @Test
    fun `computeSubjectHashOld matches after PEM roundtrip`() {
        val original = loadCert(testPem1)
        val pem = CertificateManager.encodeToPem(original)
        val parsed = CertificateManager.parseCertificate(pem.toByteArray())
        assertEquals(
            CertificateManager.computeSubjectHashOld(original),
            CertificateManager.computeSubjectHashOld(parsed)
        )
    }
}
