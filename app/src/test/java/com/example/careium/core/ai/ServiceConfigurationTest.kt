package com.example.careium.core.ai

import org.junit.Assert.*
import org.junit.Test

class ServiceConfigurationTest {
    @Test fun oneKeyConfiguresDefaultAndRedactsDiagnostics() {
        val config = ServiceConfiguration.direct(" bearer a! \n")
        assertEquals("a!", config.credential)
        assertEquals(ServiceConfiguration.DEEPSEEK_URL, config.endpoint)
        assertEquals(ServiceConfiguration.DEEPSEEK_MODEL, config.visionModel)
        assertTrue(config.online)
        assertFalse(config.toString().contains("a!"))
    }
    @Test fun keysAreNotRestrictedByLengthPrefixOrCharacterWhitelist() {
        for (key in listOf("x", "a.b+/=:@!", "x".repeat(4096), "any token format")) {
            assertEquals(key, ServiceConfiguration.direct(key).credential)
        }
        try { ServiceConfiguration.direct("  "); fail("Empty credential") } catch (_: IllegalArgumentException) { }
    }
    @Test fun arbitraryModelsAndProviderPathsArePreserved() {
        val examples = mapOf(
            "api.example.cn" to "https://api.example.cn/v1/chat/completions",
            "https://api.example.cn/v1/" to "https://api.example.cn/v1/chat/completions",
            "https://example.cn/api/v3" to "https://example.cn/api/v3/chat/completions",
            "https://example.cn/custom/v2/chat/completions/" to "https://example.cn/custom/v2/chat/completions",
            ServiceConfiguration.DEEPSEEK_URL to ServiceConfiguration.DEEPSEEK_URL)
        examples.forEach { (input, expected) ->
            val config = ServiceConfiguration.direct("key", input, "org/model-v9", "ep-any")
            assertEquals(expected, config.endpoint)
            assertEquals("org/model-v9", config.textModel)
            assertEquals("ep-any", config.visionModel)
        }
        assertEquals("", ServiceConfiguration.direct("key", visionModel = "").visionModel)
    }
    @Test fun gatewayAcceptsDomainOrPastedFullRoute() {
        for (address in listOf("example.cn", "https://example.cn/", "https://example.cn/v1/calories/", "example.cn/v1/meal-plan")) {
            assertEquals("https://example.cn", ServiceConfiguration.gateway(address, "Bearer code").endpoint)
        }
    }
    @Test fun unsafeEndpointsAreRejectedWithoutRestrictingProviders() {
        for (url in listOf("", "http://example.cn", "https://user:pass@example.cn", "https://example.cn?token=abc", "https://example.cn#key", "file:///tmp")) {
            try { ServiceConfiguration.direct("key", url); fail("Invalid URL accepted: $url") } catch (_: IllegalArgumentException) { }
        }
        assertFalse(ServiceConfiguration(ServiceMode.OFFLINE).online)
        assertFalse(ServiceConfiguration(ServiceMode.COMPATIBLE).online)
    }
}
