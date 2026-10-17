package com.vibemusic

import com.vibemusic.downloads.YouTubeFailure
import org.junit.Assert.*
import org.junit.Test

class YouTubeFailureTest {
    @Test fun nativeNetworkErrorsAreRetryableEvenWhenTheyAreNotIoExceptions() {
        listOf("ERROR: timed out", "HTTP Error 503: Service Unavailable", "Connection reset by peer", "Temporary failure in name resolution").forEach {
            assertTrue(it, YouTubeFailure.from(IllegalStateException(it)).retryable)
        }
        assertTrue(YouTubeFailure.from(RuntimeException("Conversion failed", RuntimeException("Network is unreachable"))).retryable)
    }
    @Test fun restrictionsRateLimitsAndDiskErrorsDoNotCauseAutomaticRetryLoops() {
        listOf("Private video", "Sign in to confirm your age", "HTTP Error 403: Forbidden", "HTTP Error 429: Too Many Requests", "No space left on device", "Unknown failure").forEach {
            assertFalse(it, YouTubeFailure.from(IllegalStateException(it)).retryable)
        }
        assertTrue(YouTubeFailure.from(IllegalStateException("No space left on device")).message.contains("almacenamiento"))
    }
    @Test fun messagesDoNotExposeNativePathsUrlsOrCredentials() {
        val raw = "ERROR: failed at /data/user/0/private https://example.test/?token=secret"
        val result = YouTubeFailure.from(IllegalStateException(raw))
        assertFalse(result.message.contains("secret"));assertFalse(result.message.contains("/data/"))
        assertFalse(result.retryable)
    }
}
