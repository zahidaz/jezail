package com.azzahid.jezail

import com.azzahid.jezail.core.data.Preferences
import com.azzahid.jezail.features.managers.AuthManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthManagerTest {

    private var storedPin = ""
    private var storedToken = ""
    private var storedEnabled = false

    @Before
    fun setup() {
        storedPin = ""
        storedToken = ""
        storedEnabled = false

        mockkObject(Preferences)
        every { Preferences.authPin = any() } answers { storedPin = firstArg() }
        every { Preferences.authPin } answers { storedPin }
        every { Preferences.authToken = any() } answers { storedToken = firstArg() }
        every { Preferences.authToken } answers { storedToken }
        every { Preferences.authEnabled = any() } answers { storedEnabled = firstArg() }
        every { Preferences.authEnabled } answers { storedEnabled }
    }

    @After
    fun teardown() {
        unmockkObject(Preferences)
    }

    @Test
    fun `generatePin returns 6 digit string`() {
        val pin = AuthManager.generatePin()
        assertEquals(6, pin.length)
        assertTrue(pin.all { it.isDigit() })
    }

    @Test
    fun `generateToken returns non-empty string`() {
        val token = AuthManager.generateToken()
        assertTrue(token.isNotEmpty())
    }

    @Test
    fun `pair with correct pin returns token`() {
        AuthManager.generatePin()
        val pin = storedPin
        val token = AuthManager.pair(pin)
        assertNotNull(token)
        assertTrue(token!!.isNotEmpty())
    }

    @Test
    fun `pair with wrong pin returns null`() {
        AuthManager.generatePin()
        assertNull(AuthManager.pair("000000"))
    }

    @Test
    fun `validateToken with correct token returns true`() {
        AuthManager.generateToken()
        assertTrue(AuthManager.validateToken(storedToken))
    }

    @Test
    fun `validateToken with wrong token returns false`() {
        AuthManager.generateToken()
        assertFalse(AuthManager.validateToken("wrong-token"))
    }

    @Test
    fun `validateToken with empty token returns false`() {
        assertFalse(AuthManager.validateToken(""))
    }

    @Test
    fun `setEnabled true generates pin and token`() {
        AuthManager.setEnabled(true)
        assertTrue(storedEnabled)
        assertTrue(storedPin.isNotEmpty())
        assertTrue(storedToken.isNotEmpty())
    }

    @Test
    fun `regeneratePin clears old token`() {
        AuthManager.generateToken()
        assertTrue(storedToken.isNotEmpty())
        AuthManager.regeneratePin()
        assertTrue(storedPin.isNotEmpty())
        assertEquals("", storedToken)
    }
}
