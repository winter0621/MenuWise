package com.example.careium.core.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VisionResultTest {
    @Test fun customApiAlsoGeneratesMealPlansUsingTextModel() {
        val config = ServiceConfiguration.direct("key", "https://example.cn/v2", "custom-text", "custom-vision")
        val response = JSONObject().put("notes", JSONArray()).put("meals", JSONArray().put(JSONObject()
            .put("id", "dinner").put("items", JSONArray().put(JSONObject().put("name", "米饭")
                .put("ingredients", JSONArray().put("大米")).put("grams", 600).put("calories_kcal", 1200)))))
        val request = JSONObject().put("preferences", "清淡").put("excluded_foods", "花生")
            .put("meals", JSONArray().put(JSONObject().put("id", "dinner").put("target_kcal", 1200)))
        val answer = ProviderClient { passed, payload ->
            assertEquals(config.endpoint, passed.endpoint)
            assertEquals("custom-text", payload.getString("model"))
            envelope(response)
        }.plan(config, request)
        assertEquals(1200.0, answer.getJSONArray("meals").getJSONObject(0).getDouble("total_kcal"), 0.001)
    }
    @Test fun customProviderRoutesTextAndImagesToTheirConfiguredModels() {
        val config = ServiceConfiguration.direct("k!", "https://ai.example.cn/compatible/v1", "text/custom", "vision/custom")
        var calls = 0
        val client = ProviderClient { passed, body ->
            assertEquals("https://ai.example.cn/compatible/v1/chat/completions", passed.endpoint)
            assertEquals("k!", passed.credential)
            assertEquals(if (calls++ == 0) "text/custom" else "vision/custom", body.getString("model"))
            if (calls == 1) assertEquals("米饭", body.getJSONArray("messages").getJSONObject(1).getString("content"))
            envelope(result())
        }
        client.query(config, "米饭", null)
        client.query(config, "米饭", "data:image/jpeg;base64,test")
        assertEquals(2, calls)
    }
    @Test fun missingVisionModelRejectsPhotoBeforeAnyRequest() {
        val config = ServiceConfiguration.direct("key", visionModel = "")
        val client = ProviderClient { _, _ -> throw AssertionError("Photo must not be silently dropped or sent") }
        try { client.query(config, "米饭", "data:image/jpeg;base64,test"); fail("Missing vision model") }
        catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("视觉模型")) }
    }
    @Test fun deepseekAcceptsConfiguredVisionAndTextModel() {
        val config = ServiceConfiguration.direct("key", ServiceConfiguration.DEEPSEEK_URL,
            ServiceConfiguration.DEEPSEEK_MODEL, ServiceConfiguration.DEEPSEEK_MODEL)
        ProviderClient { passed, body ->
            assertEquals(ServiceConfiguration.DEEPSEEK_URL, passed.endpoint)
            assertEquals("deepseek-flash", body.getString("model"))
            assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
            envelope(result())
        }.query(config, "米饭", "data:image/jpeg;base64,test")
    }
    private fun food() = JSONObject().put("name", "熟米饭").put("grams", 200).put("calories_kcal", 260)
        .put("protein_g", 5).put("carbs_g", 56).put("fat_g", 1).put("calories_min_kcal", 220).put("calories_max_kcal", 300)
    private fun result() = JSONObject().put("food_detected", true).put("items", JSONArray().put(food()).put(food()))
        .put("assumptions", JSONArray().put("按熟重估算"))
    private fun envelope(result: JSONObject) = JSONObject().put("choices", JSONArray().put(JSONObject()
        .put("finish_reason", "stop").put("message", JSONObject().put("content", result.toString()))))
    @Test fun visualRequestKeepsPhotoAndDescriptionAndUsesSelectedModel() {
        val config = ServiceConfiguration.direct("sk-" + "x".repeat(32))
        var payload: JSONObject? = null
        val client = ProviderClient { _, body -> payload = body; envelope(result()) }
        val response = client.query(config, "米饭200克，少油", "data:image/jpeg;base64,test")
        assertEquals(ServiceConfiguration.DEEPSEEK_MODEL, payload!!.getString("model"))
        val content = payload!!.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("米饭200克，少油", content.getJSONObject(0).getString("text"))
        assertEquals("high", content.getJSONObject(1).getJSONObject("image_url").getString("detail"))
        assertEquals(520.0, response.getJSONObject("totals").getDouble("calories_kcal"), 0.001)
        assertEquals(440.0, response.getJSONObject("totals").getDouble("calories_min_kcal"), 0.001)
    }
    @Test fun textOnlyUsesTextModelAndIncompleteGenerationIsRejected() {
        val config = ServiceConfiguration.direct("sk-" + "x".repeat(32))
        ProviderClient { _, body -> assertEquals(ServiceConfiguration.DEEPSEEK_MODEL, body.getString("model")); envelope(result()) }.query(config, "米饭", null)
        val client = ProviderClient { _, _ -> envelope(result()).apply { getJSONArray("choices").getJSONObject(0).put("finish_reason", "length") } }
        try { client.query(config, "米饭", null); fail("Truncation accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun nonFoodImpossibleDensitiesAndInvalidRangesAreRejected() {
        val cases = listOf(
            result().put("food_detected", false),
            result().put("food_detected", "true"),
            result().apply { getJSONArray("items").getJSONObject(0).put("calories_kcal", 5000) },
            result().apply { getJSONArray("items").getJSONObject(0).put("protein_g", 500) },
            result().apply { getJSONArray("items").getJSONObject(0).put("calories_min_kcal", 400) },
            result().apply { getJSONArray("items").getJSONObject(0).put("grams", 0) },
            result().apply { getJSONArray("items").getJSONObject(0).put("fat_g", "1") })
        cases.forEach { try { NutritionResponse.normalize(it); fail("Invalid model output") } catch (_: IllegalArgumentException) { } }
    }
    @Test fun oldGatewayResultWithoutRangesStillWorksAndTotalsAreRecomputed() {
        val original = result().apply {
            remove("food_detected")
            for (i in 0 until getJSONArray("items").length()) {
                getJSONArray("items").getJSONObject(i).remove("calories_min_kcal")
                getJSONArray("items").getJSONObject(i).remove("calories_max_kcal")
            }
            put("totals", JSONObject().put("calories_kcal", 1))
        }
        val checked = NutritionResponse.normalize(original)
        assertEquals(520.0, checked.getJSONObject("totals").getDouble("calories_kcal"), 0.001)
        assertFalse(checked.getJSONObject("totals").has("calories_min_kcal"))
    }
    @Test fun portionCorrectionRecomputesEveryNutrientAndRangeWithoutMutatingOriginal() {
        val original = NutritionResponse.normalize(result())
        val adjusted = CaloriePortions.adjust(original, listOf(100.0, 300.0))
        val first = adjusted.getJSONArray("items").getJSONObject(0)
        assertEquals(130.0, first.getDouble("calories_kcal"), 0.001)
        assertEquals(2.5, first.getDouble("protein_g"), 0.001)
        assertEquals(110.0, first.getDouble("calories_min_kcal"), 0.001)
        assertEquals(520.0, adjusted.getJSONObject("totals").getDouble("calories_kcal"), 0.001)
        assertEquals(200.0, original.getJSONArray("items").getJSONObject(0).getDouble("grams"), 0.001)
        assertTrue(adjusted.getBoolean("portion_adjusted"))
    }
    @Test fun correctionPreservesUnknownNutrientsAndRejectsInvalidWeights() {
        val original = result().apply { getJSONArray("items").getJSONObject(0).put("protein_g", JSONObject.NULL) }
        assertTrue(CaloriePortions.adjust(original, listOf(100.0, 100.0)).getJSONObject("totals").isNull("protein_g"))
        for (grams in listOf(listOf(0.0, 100.0), listOf(Double.NaN, 100.0), listOf(10001.0, 100.0), listOf(100.0))) {
            try { CaloriePortions.adjust(original, grams); fail("Invalid portion") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun directMealPlansRejectExcludedIngredientsAndRetainTargetChecks() {
        val food = JSONObject().put("name", "鸡蛋").put("ingredients", JSONArray().put("鸡蛋"))
            .put("grams", 100).put("calories_kcal", 500)
        val response = JSONObject().put("notes", JSONArray()).put("meals", JSONArray()
            .put(JSONObject().put("id", "lunch").put("items", JSONArray().put(food))))
        assertEquals(500.0, ProviderClient.validatePlan(response, mapOf("lunch" to 500), emptyList())
            .getJSONArray("meals").getJSONObject(0).getDouble("total_kcal"), 0.001)
        try { ProviderClient.validatePlan(response, mapOf("lunch" to 500), listOf("鸡蛋")); fail("Allergen accepted") } catch (_: IllegalArgumentException) { }
        try { ProviderClient.validatePlan(response, mapOf("lunch" to 900), emptyList()); fail("Wrong target accepted") } catch (_: IllegalArgumentException) { }
    }
}
