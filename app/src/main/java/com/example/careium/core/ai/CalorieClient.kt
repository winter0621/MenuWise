package com.example.careium.core.ai

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Routes to the selected personal provider or existing HTTPS gateway. */
class CalorieClient {
    fun query(config: ServiceConfiguration, text: String, imageData: String?): JSONObject = when (config.mode) {
        ServiceMode.COMPATIBLE -> ProviderClient().query(config, text, imageData)
        ServiceMode.GATEWAY -> query(config.endpoint, config.credential, text, imageData).put("photo_analysis", imageData != null)
        ServiceMode.OFFLINE -> error("请先配置在线服务")
    }
    fun plan(config: ServiceConfiguration, body: JSONObject): JSONObject = when (config.mode) {
        ServiceMode.COMPATIBLE -> ProviderClient().plan(config, body)
        ServiceMode.GATEWAY -> plan(config.endpoint, config.credential, body)
        ServiceMode.OFFLINE -> error("请先配置在线服务")
    }
    fun query(endpoint: String, accessCode: String, text: String, imageData: String?): JSONObject {
        val body = JSONObject().put("text", text)
        if (imageData != null) body.put("image_data", imageData)
        val result = post(endpoint, accessCode, "/v1/calories", body)
        require(result.optBoolean("estimated")) { "服务返回格式不正确，请检查后端版本" }
        return NutritionResponse.normalize(result).put("source", "gateway")
    }

    fun plan(endpoint: String, accessCode: String, body: JSONObject): JSONObject =
        post(endpoint, accessCode, "/v1/meal-plan", body)

    private fun post(endpoint: String, accessCode: String, route: String, body: JSONObject): JSONObject {
        val url = checkedEndpoint(endpoint, route)
        require(accessCode.isNotBlank()) { "请先在服务设置中填写访问码" }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $accessCode")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use {
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (true) {
                    val count = it.read(chunk)
                    if (count < 0) break
                    if (buffer.size() + count > 200_000) throw IllegalStateException("服务返回内容过大")
                    buffer.write(chunk, 0, count)
                }
                buffer.toString("UTF-8")
            }.orEmpty()
            val json = try { JSONObject(response) } catch (_: Exception) { null }
            if (status !in 200..299) {
                throw IllegalStateException(json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "查询失败（HTTP $status），请检查服务地址或稍后重试")
            }
            require(json != null) {
                "服务返回格式不正确，请检查后端版本"
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun checkedEndpoint(endpoint: String, route: String = "/v1/calories"): URL {
            require(route in listOf("/v1/calories", "/v1/meal-plan")) { "不支持的服务接口" }
            val parsed = try { URL(endpoint.trim()) } catch (_: Exception) {
                throw IllegalArgumentException("请填写完整的 HTTPS 服务地址")
            }
            require(parsed.protocol == "https" && parsed.host.isNotBlank()
                && parsed.userInfo == null && parsed.query == null && parsed.ref == null) {
                "服务地址必须使用 HTTPS，且不能包含账号、查询参数或锚点"
            }
            return URL(endpoint.trim().trimEnd('/') + route)
        }
    }
}
