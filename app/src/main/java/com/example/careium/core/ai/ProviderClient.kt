package com.example.careium.core.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.round

/** OpenAI-compatible Chat Completions client; redirects never forward credentials. */
class ProviderClient(private val transport: (ServiceConfiguration, JSONObject) -> JSONObject = ::send) {
    fun query(config: ServiceConfiguration, text: String, image: String?): JSONObject {
        require(image == null || config.visionModel.isNotBlank()) { "当前未配置视觉模型，请在高级设置中填写支持图片的模型" }
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", text.ifBlank { "请逐项估算照片中整份餐食的大卡、份量与营养，并说明用油假设。" }))
        if (image != null) content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image).put("detail", "high")))
        val answer = complete(config, if (image == null) config.textModel else config.visionModel, AiPrompts.SYSTEM_PROMPT, if (image == null) text else content)
        require(answer.opt("food_detected") is Boolean) { "食物识别结果不完整，请重试" }
        return NutritionResponse.normalize(answer).put("source", "compatible_api").put("photo_analysis", image != null)
    }
    fun plan(config: ServiceConfiguration, body: JSONObject): JSONObject {
        val excluded = body.optString("excluded_foods").split(',', '，', '、', ';', '；', '\n').map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val meals = body.getJSONArray("meals")
        require(meals.length() in 1..4 && excluded.size <= 20 && excluded.all { it.length <= 30 }) { "配餐参数不正确" }
        val clean = JSONObject().put("preferences", body.optString("preferences")).put("excluded_foods", JSONArray(excluded)).put("meals", meals)
        val expected = linkedMapOf<String, Int>()
        for (i in 0 until meals.length()) {
            val meal = meals.getJSONObject(i)
            val id = meal.getString("id")
            val target = meal.getInt("target_kcal")
            require(id in listOf("breakfast", "lunch", "dinner", "snack") && id !in expected && target in 1..8000) { "餐次目标无效" }
            expected[id] = target
        }
        require(expected.values.sum() in 1200..8000 && body.optString("preferences").length <= 500) { "每日配餐目标或偏好无效" }
        return validatePlan(complete(config, config.textModel, AiPrompts.PLAN_PROMPT, clean.toString()), expected, excluded)
    }
    private fun complete(config: ServiceConfiguration, model: String, prompt: String, content: Any): JSONObject {
        require(config.mode == ServiceMode.COMPATIBLE && config.credential.isNotBlank()) { "请先填写 API Key" }
        val payload = JSONObject().put("model", model).put("temperature", 0.1).put("max_tokens", 4500).put("stream", false)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", prompt))
                .put(JSONObject().put("role", "user").put("content", content)))
        if (java.net.URI(config.endpoint).host in listOf("ark.cn-beijing.volces.com", "api.deepseek.com"))
            payload.put("thinking", JSONObject().put("type", "disabled"))
        val choice = transport(config, payload).getJSONArray("choices").getJSONObject(0)
        require(choice.optString("finish_reason") == "stop") { "识别结果未完整返回，请缩小查询范围后重试" }
        var answer = choice.getJSONObject("message").getString("content").trim()
        if (answer.startsWith("```json") && answer.endsWith("```")) answer = answer.removePrefix("```json").removeSuffix("```").trim()
        return try { JSONObject(answer) } catch (_: Exception) { throw IllegalArgumentException("AI返回格式异常，请重试") }
    }
    companion object {
        fun validatePlan(value: JSONObject, expected: Map<String, Int>, excluded: List<String>): JSONObject {
            val meals = value.getJSONArray("meals")
            require(meals.length() == expected.size) { "AI返回的餐次不完整" }
            val notes = value.getJSONArray("notes")
            require(notes.length() <= 8 && (0 until notes.length()).all { notes.opt(it) is String && notes.getString(it).length <= 300 }) { "配餐说明无效" }
            val seen = mutableSetOf<String>()
            val clean = JSONArray()
            for (i in 0 until meals.length()) {
                val meal = meals.getJSONObject(i)
                val id = meal.getString("id")
                require(id in expected && seen.add(id)) { "餐次无效或重复" }
                val foods = meal.getJSONArray("items")
                require(foods.length() in 1..8) { "配餐食物数量异常" }
                val rows = JSONArray()
                for (j in 0 until foods.length()) {
                    val food = foods.getJSONObject(j)
                    val name = food.getString("name").trim()
                    require(name.length in 1..100) { "配餐名称无效" }
                    val ingredients = food.getJSONArray("ingredients")
                    require(ingredients.length() in 1..20) { "AI未列出食材，请重试" }
                    val parts = (0 until ingredients.length()).map { ingredients.getString(it).trim() }
                    require(parts.all { it.length in 1..60 }) { "食材格式无效" }
                    val searchable = (parts + name).joinToString(" ").lowercase()
                    require(excluded.none { searchable.contains(it) }) { "AI建议疑似包含忌口食材，请勿采用并重新生成" }
                    val grams = NutritionResponse.number(food, "grams", 10000.0)
                    val kcal = NutritionResponse.number(food, "calories_kcal", 20000.0)
                    require(grams > 0) { "食物份量无效" }
                    rows.put(JSONObject().put("name", name).put("grams", grams).put("calories_kcal", kcal).put("ingredients", JSONArray(parts)))
                }
                val total = round((0 until rows.length()).sumOf { rows.getJSONObject(it).getDouble("calories_kcal") } * 10) / 10
                val target = expected.getValue(id)
                require(total > 0 && abs(total - target) <= maxOf(50.0, target * 0.15)) { "AI配餐与热量目标相差较大，请重试" }
                clean.put(JSONObject().put("id", id).put("items", rows).put("total_kcal", total).put("target_kcal", target))
            }
            return JSONObject().put("meals", clean).put("notes", notes).put("estimated", true)
        }
        private fun send(config: ServiceConfiguration, payload: JSONObject): JSONObject {
            val connection = URL(ServiceConfiguration.completionEndpoint(config.endpoint)).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000; connection.readTimeout = 90000; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Authorization", "Bearer ${config.credential}")
                val bytes = payload.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val status = connection.responseCode
                require(status in 200..299) {
                    when (status) {
                        401 -> "API 身份验证失败（401），请检查 Key 是否属于 ${URL(config.endpoint).host}，并确认 Key 仍有效"
                        403 -> "API 访问被拒绝（403），请检查 ${URL(config.endpoint).host} 的模型权限或账户状态"
                        429 -> "模型调用限流或额度不足，请检查服务商余额后重试"
                        400, 404 -> "模型不可用，请在高级设置中检查 API 地址和模型名称后重试"
                        else -> "模型服务暂不可用（HTTP $status），请稍后重试"
                    }
                }
                val output = java.io.ByteArrayOutputStream()
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 200000) { "模型返回内容过大" }
                        output.write(buffer, 0, count)
                    }
                }
                return JSONObject(output.toString("UTF-8"))
            } finally { connection.disconnect() }
        }
    }
}
