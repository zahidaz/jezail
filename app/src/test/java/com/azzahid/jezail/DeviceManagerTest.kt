package com.azzahid.jezail

import com.azzahid.jezail.features.managers.DeviceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceManagerTest {

    @Test
    fun `sanitizeShellArg returns empty for empty input`() {
        assertEquals("", DeviceManager.sanitizeShellArg(""))
    }

    @Test
    fun `sanitizeShellArg returns normal string unchanged`() {
        assertEquals("hello world", DeviceManager.sanitizeShellArg("hello world"))
    }

    @Test
    fun `sanitizeShellArg escapes single quotes`() {
        assertEquals("it'\\''s a test", DeviceManager.sanitizeShellArg("it's a test"))
    }

    @Test
    fun `sanitizeShellArg passes shell metacharacters through`() {
        val input = "foo; bar && baz | qux"
        assertEquals(input, DeviceManager.sanitizeShellArg(input))
    }

    @Test
    fun `GETPROP_REGEX matches standard key-value pair`() {
        val match = DeviceManager.GETPROP_REGEX.find("[ro.build.display.id]: [ABC123]")
        assertNotNull(match)
        assertEquals("ro.build.display.id", match!!.groupValues[1])
        assertEquals("ABC123", match.groupValues[2])
    }

    @Test
    fun `GETPROP_REGEX matches empty value`() {
        val match = DeviceManager.GETPROP_REGEX.find("[ro.empty.prop]: []")
        assertNotNull(match)
        assertEquals("ro.empty.prop", match!!.groupValues[1])
        assertEquals("", match.groupValues[2])
    }

    @Test
    fun `GETPROP_REGEX matches dotted keys`() {
        val match = DeviceManager.GETPROP_REGEX.find("[persist.sys.timezone]: [America/New_York]")
        assertNotNull(match)
        assertEquals("persist.sys.timezone", match!!.groupValues[1])
        assertEquals("America/New_York", match.groupValues[2])
    }

    @Test
    fun `GETPROP_REGEX does not match non-standard lines`() {
        assertNull(DeviceManager.GETPROP_REGEX.find("not a prop line"))
        assertNull(DeviceManager.GETPROP_REGEX.find("key: value"))
        assertNull(DeviceManager.GETPROP_REGEX.find("[only-key]"))
    }

    @Test
    fun `WHITESPACE_REGEX splits on multiple spaces`() {
        val parts = "a   b   c".split(DeviceManager.WHITESPACE_REGEX)
        assertEquals(listOf("a", "b", "c"), parts)
    }

    @Test
    fun `WHITESPACE_REGEX splits on tabs`() {
        val parts = "a\tb\tc".split(DeviceManager.WHITESPACE_REGEX)
        assertEquals(listOf("a", "b", "c"), parts)
    }

    @Test
    fun `WHITESPACE_REGEX splits on mixed whitespace`() {
        val parts = "a \t b\t\t c".split(DeviceManager.WHITESPACE_REGEX)
        assertEquals(listOf("a", "b", "c"), parts)
    }
}
