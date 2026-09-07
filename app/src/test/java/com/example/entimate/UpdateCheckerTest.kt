package com.example.entimate

import com.example.entimate.data.update.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `isNewer compares semantic versions`() {
        assertTrue(UpdateChecker.isNewer("1.4.0", "1.3.0"))
        assertTrue(UpdateChecker.isNewer("1.3.1", "1.3.0"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"))
    }

    @Test
    fun `isNewer is false for equal or older versions`() {
        assertFalse(UpdateChecker.isNewer("1.3.0", "1.3.0"))
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.3.0"))
        assertFalse(UpdateChecker.isNewer("1.3.0-beta", "1.3.0"))
    }

    @Test
    fun `isNewer handles empty or invalid input`() {
        assertFalse(UpdateChecker.isNewer("", "1.3.0"))
        assertFalse(UpdateChecker.isNewer("1.3.0", ""))
        assertFalse(UpdateChecker.isNewer("abc", "1.3.0"))
    }
}