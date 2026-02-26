package com.azzahid.jezail

import com.azzahid.jezail.core.utils.isValidPermissions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUtilsTest {

    @Test
    fun `valid octal 755`() {
        assertTrue(isValidPermissions("755"))
    }

    @Test
    fun `valid octal 0644`() {
        assertTrue(isValidPermissions("0644"))
    }

    @Test
    fun `valid symbolic u+x`() {
        assertTrue(isValidPermissions("u+x"))
    }

    @Test
    fun `valid symbolic go-w`() {
        assertTrue(isValidPermissions("go-w"))
    }

    @Test
    fun `valid symbolic a=rwx`() {
        assertTrue(isValidPermissions("a=rwx"))
    }

    @Test
    fun `invalid 999`() {
        assertFalse(isValidPermissions("999"))
    }

    @Test
    fun `invalid abc`() {
        assertFalse(isValidPermissions("abc"))
    }

    @Test
    fun `invalid empty`() {
        assertFalse(isValidPermissions(""))
    }

    @Test
    fun `invalid 78`() {
        assertFalse(isValidPermissions("78"))
    }

    @Test
    fun `invalid 12345`() {
        assertFalse(isValidPermissions("12345"))
    }
}
