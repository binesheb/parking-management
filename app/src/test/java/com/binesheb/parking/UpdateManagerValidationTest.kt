package com.binesheb.parking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerValidationTest {
    @Test
    fun acceptsAWellFormedSha256() {
        assertTrue(isValidApkSha256("a".repeat(64)))
        assertTrue(isValidApkSha256("ABCDEF0123456789".repeat(4)))
    }

    @Test
    fun rejectsTheAllZeroPlaceholder() {
        assertFalse(isValidApkSha256("0".repeat(64)))
    }

    @Test
    fun rejectsMalformedDigests() {
        assertFalse(isValidApkSha256(""))
        assertFalse(isValidApkSha256("0".repeat(63)))
        assertFalse(isValidApkSha256("g".repeat(64)))
    }
}
