package com.example.careium.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CalorieClientTest {
    @Test fun mealPlanRouteUsesSameHttpsValidation() {
        assertEquals("https://calories.example.cn/v1/meal-plan",
            CalorieClient.checkedEndpoint("https://calories.example.cn", "/v1/meal-plan").toString())
        try {
            CalorieClient.checkedEndpoint("https://calories.example.cn", "https://other.example")
            fail("Arbitrary route should be rejected")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun httpsEndpointGetsApiPath() {
        assertEquals("https://calories.example.cn/v1/calories",
            CalorieClient.checkedEndpoint(" https://calories.example.cn/ ").toString())
    }

    @Test fun gatewayPrefixIsPreserved() {
        assertEquals("https://calories.example.cn/menuwise/v1/calories",
            CalorieClient.checkedEndpoint("https://calories.example.cn/menuwise").toString())
    }

    @Test fun refusesCleartextAndNonNetworkUrls() {
        for (url in listOf("http://example.cn", "file:///etc/passwd", "", "example.cn")) {
            assertInvalid(url)
        }
    }

    @Test fun refusesEmbeddedCredentialsQueryAndFragment() {
        for (url in listOf("https://user:password@example.cn", "https://example.cn?key=secret", "https://example.cn#fragment")) {
            assertInvalid(url)
        }
    }

    private fun assertInvalid(url: String) {
        try {
            CalorieClient.checkedEndpoint(url)
            fail("Should reject $url")
        } catch (_: IllegalArgumentException) { }
    }
}
