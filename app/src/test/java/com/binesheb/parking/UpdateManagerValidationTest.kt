package com.binesheb.parking

import java.net.URL
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

    @Test
    fun acceptsOnlyTheRepositoryReleaseUrlAsTheInitialApkUrl() {
        assertTrue(isValidInitialApkUrl(URL("https://github.com/binesheb/parking-management/releases/latest/download/parking-management.apk")))
        assertFalse(isValidInitialApkUrl(URL("http://github.com/binesheb/parking-management/releases/latest/download/parking-management.apk")))
        assertFalse(isValidInitialApkUrl(URL("https://github.com/another/repo/releases/latest/download/app.apk")))
        assertFalse(isValidInitialApkUrl(URL("https://github.com.evil.test/binesheb/parking-management/releases/app.apk")))
        assertFalse(isValidInitialApkUrl(URL("https://github.com:8443/binesheb/parking-management/releases/app.apk")))
    }

    @Test
    fun acceptsOnlyTrustedHttpsGitHubAssetRedirectHosts() {
        assertTrue(isAllowedApkRedirectUrl(URL("https://release-assets.githubusercontent.com/github-production-release-asset/file")))
        assertTrue(isAllowedApkRedirectUrl(URL("https://objects.githubusercontent.com/github-production-release-asset/file")))
    }

    @Test
    fun rejectsUntrustedApkRedirectTargets() {
        assertFalse(isAllowedApkRedirectUrl(URL("http://release-assets.githubusercontent.com/file")))
        assertFalse(isAllowedApkRedirectUrl(URL("https://github.com/binesheb/parking-management/releases/file")))
        assertFalse(isAllowedApkRedirectUrl(URL("https://release-assets.githubusercontent.com.evil.test/file")))
        assertFalse(isAllowedApkRedirectUrl(URL("https://release-assets.githubusercontent.com@evil.test/file")))
        assertFalse(isAllowedApkRedirectUrl(URL("https://release-assets.githubusercontent.com:8443/file")))
    }
}
