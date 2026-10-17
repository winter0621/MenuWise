package com.example.careium.core.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AiServiceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai_calorie_service", Context.MODE_PRIVATE)
    private val alias = "menuwise_provider_key"
    fun current(): ServiceConfiguration {
        migrateDefaultEndpoint()
        val mode = prefs.getString("mode", null) ?: if (prefs.getString("endpoint", "").isNullOrBlank()) "OFFLINE" else "GATEWAY"
        return when (mode) {
            "COMPATIBLE" -> ServiceConfiguration(ServiceMode.COMPATIBLE, readKey(),
                endpoint = prefs.getString("provider_endpoint", "").orEmpty(),
                visionModel = prefs.getString("vision_model", "").orEmpty(),
                textModel = prefs.getString("text_model", "").orEmpty())
            // Preserve the original destination when migrating existing encrypted credentials.
            "DASHSCOPE" -> ServiceConfiguration(ServiceMode.COMPATIBLE, readKey(),
                endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                visionModel = prefs.getString("vision_model", "qwen-vl-max") ?: "qwen-vl-max", textModel = "qwen-plus")
            "GATEWAY" -> ServiceConfiguration(ServiceMode.GATEWAY, prefs.getString("access_code", "").orEmpty(), prefs.getString("endpoint", "").orEmpty())
            else -> ServiceConfiguration(ServiceMode.OFFLINE)
        }
    }
    fun save(config: ServiceConfiguration) {
        val editor = prefs.edit().putString("mode", config.mode.name).putInt("configuration_version", 2)
        when (config.mode) {
            ServiceMode.COMPATIBLE -> {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
                val encrypted = cipher.doFinal(config.credential.toByteArray(Charsets.UTF_8))
                editor.putString("provider_key", encode(cipher.iv) + ":" + encode(encrypted))
                    .putString("vision_model", config.visionModel)
                    .putString("text_model", config.textModel).putString("provider_endpoint", config.endpoint)
                    .remove("endpoint").remove("access_code")
            }
            ServiceMode.GATEWAY -> editor.putString("endpoint", config.endpoint).putString("access_code", config.credential).remove("provider_key")
            ServiceMode.OFFLINE -> editor.remove("provider_key").remove("endpoint").remove("access_code")
        }
        check(editor.commit()) { "设置保存失败，请重试" }
    }
    // Correct the previous one-key default for this DeepSeek configuration.
    // Keep encrypted bytes intact; explicitly saved/custom destinations remain unchanged.
    private fun migrateDefaultEndpoint() {
        if (prefs.getInt("configuration_version", 0) >= 2) return
        val editor = prefs.edit().putInt("configuration_version", 2)
        if (prefs.getString("mode", "") == "COMPATIBLE"
            && prefs.getString("provider_endpoint", "") == ServiceConfiguration.DOUBAO_URL
            && prefs.getString("text_model", "") == ServiceConfiguration.DOUBAO_MODEL
            && prefs.getString("vision_model", "") == ServiceConfiguration.DOUBAO_MODEL) {
            editor.putString("provider_endpoint", ServiceConfiguration.DEEPSEEK_URL)
                .putString("text_model", ServiceConfiguration.DEEPSEEK_MODEL)
                .putString("vision_model", ServiceConfiguration.DEEPSEEK_MODEL)
        }
        check(editor.commit()) { "API 配置更新失败，请重新打开设置" }
    }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "请重新填写 API Key" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun readKey(): String = try {
        val parts = prefs.getString("provider_key", "").orEmpty().split(':')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) { "" }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
