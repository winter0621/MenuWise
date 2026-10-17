package com.example.careium.core.ai

import java.net.URI

enum class ServiceMode { OFFLINE, COMPATIBLE, GATEWAY }

data class ServiceConfiguration(val mode: ServiceMode, val credential: String = "", val endpoint: String = "",
    val visionModel: String = DEEPSEEK_MODEL, val textModel: String = DEEPSEEK_MODEL) {
    val online: Boolean get() = mode != ServiceMode.OFFLINE && credential.isNotBlank()
    override fun toString() = "ServiceConfiguration(mode=$mode, credential=<redacted>)"
    companion object {
        const val DOUBAO_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
        const val DOUBAO_MODEL = "doubao-seed-1-6-250615"
        const val DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"
        const val DEEPSEEK_MODEL = "deepseek-flash"

        fun direct(rawKey: String, endpoint: String = DEEPSEEK_URL,
                   textModel: String = DEEPSEEK_MODEL, visionModel: String = DEEPSEEK_MODEL): ServiceConfiguration {
            val key = rawKey.trim().replaceFirst(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "").trim()
            require(key.isNotBlank()) { "请填写 API Key" }
            require(textModel.isNotBlank()) { "请填写文字模型名称" }
            return ServiceConfiguration(ServiceMode.COMPATIBLE, key, completionEndpoint(endpoint), visionModel.trim(), textModel.trim())
        }

        /** Accept a base URL or full Chat Completions URL, preserving provider-specific prefixes. */
        fun completionEndpoint(address: String): String {
            var value = address.trim().trimEnd('/')
            require(value.isNotBlank()) { "请填写 API 地址" }
            if (!value.contains("://")) value = "https://$value"
            val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("API 地址格式不正确") }
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
                && uri.rawQuery == null && uri.rawFragment == null) { "API 地址必须为 HTTPS，且不能包含账号、查询参数或锚点" }
            if (uri.path.isNullOrBlank() || uri.path == "/") value += "/v1"
            if (!value.endsWith("/chat/completions")) value += "/chat/completions"
            return value
        }

        fun gateway(address: String, rawCode: String): ServiceConfiguration {
            var endpoint = address.trim().trimEnd('/')
            if (!endpoint.contains("://")) endpoint = "https://$endpoint"
            endpoint = endpoint.removeSuffix("/v1/calories").removeSuffix("/v1/meal-plan")
            CalorieClient.checkedEndpoint(endpoint)
            val code = rawCode.trim().removePrefix("Bearer ").trim()
            require(code.isNotBlank()) { "请填写服务访问码" }
            return ServiceConfiguration(ServiceMode.GATEWAY, code, endpoint)
        }
    }
}
