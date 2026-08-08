package com.yanxing.agent.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlValidationTest {

    @Test
    fun `accepts http and https urls`() {
        assertTrue(isValidBaseUrl("https://api.example.com"))
        assertTrue(isValidBaseUrl("http://192.168.1.1:8080"))
        assertTrue(isValidBaseUrl("  https://api.example.com/v1  "))
    }

    @Test
    fun `rejects invalid urls`() {
        assertFalse(isValidBaseUrl(""))
        assertFalse(isValidBaseUrl("   "))
        assertFalse(isValidBaseUrl("ftp://example.com"))
        assertFalse(isValidBaseUrl("example.com"))
        assertFalse(isValidBaseUrl("api.example.com/v1"))
    }
}
