package com.example.careium.core.ai

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.careium.core.models.DeepLearning
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionPipelineTest {
    @Test fun oldDefaultMigratesOnceWithoutChangingEncryptedKeyOrCustomSettings() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("migration_test_$name", mode)
        }
        val prefs = context.getSharedPreferences("ai_calorie_service", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = AiServiceSettings(context)
            val old = ServiceConfiguration.direct("fake-key", ServiceConfiguration.DOUBAO_URL,
                ServiceConfiguration.DOUBAO_MODEL, ServiceConfiguration.DOUBAO_MODEL)
            settings.save(old)
            val encrypted = prefs.getString("provider_key", "")
            prefs.edit().remove("configuration_version").commit()
            val migrated = settings.current()
            assertEquals(ServiceConfiguration.DEEPSEEK_URL, migrated.endpoint)
            assertEquals(ServiceConfiguration.DEEPSEEK_MODEL, migrated.textModel)
            assertEquals(ServiceConfiguration.DEEPSEEK_MODEL, migrated.visionModel)
            assertEquals("fake-key", migrated.credential)
            assertEquals(encrypted, prefs.getString("provider_key", ""))
            assertEquals(migrated, settings.current())
            settings.save(old)
            assertEquals(old, settings.current())
            val custom = ServiceConfiguration.direct("fake-custom", "https://custom.example.cn/v1", "custom", "vision")
            settings.save(custom)
            prefs.edit().remove("configuration_version").commit()
            assertEquals(custom, settings.current())
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun originalSizePhotoIsResizedToModelTensorWithoutOverflow() {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            for (size in listOf(224, 250)) {
                val tensor = DeepLearning().loadImageBuffer(bitmap, size, size)
                assertArrayEquals(intArrayOf(1, size, size, 3), tensor.shape)
                val values = tensor.floatArray
                assertEquals(1f, values[0], 0.0001f)
                assertEquals(0f, values[1], 0.0001f)
                assertEquals(1f, values[values.size - 3], 0.0001f)
            }
            assertFalse(bitmap.isRecycled)
        } finally { bitmap.recycle() }
    }
    @Test fun encodedPhotoIsJpegWithinGatewayLimit() {
        val bitmap = Bitmap.createBitmap(1536, 1152, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        try {
            val encoded = PhotoEncoder.dataUrl(bitmap)
            assertTrue(encoded.startsWith("data:image/jpeg;base64,"))
            val bytes = Base64.decode(encoded.substringAfter(','), Base64.NO_WRAP)
            assertTrue(bytes.size <= PhotoSizing.MAX_BYTES)
            assertEquals(255, bytes[0].toInt() and 255)
            assertEquals(216, bytes[1].toInt() and 255)
            assertFalse(bitmap.isRecycled)
        } finally { bitmap.recycle() }
    }
    @Test fun providerKeyIsEncryptedAndLegacyGatewayStillLoads() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("vision_test_$name", mode)
        }
        val prefs = context.getSharedPreferences("ai_calorie_service", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val settings = AiServiceSettings(context)
            val key = "sk-" + "test".repeat(8)
            settings.save(ServiceConfiguration.direct(key))
            assertFalse(prefs.all.toString().contains(key))
            assertEquals(key, settings.current().credential)
            assertTrue(settings.current().online)
            val custom = ServiceConfiguration.direct("short!", "https://example.cn/custom/v1", "text-any", "vision-any")
            settings.save(custom)
            assertEquals(custom, settings.current())
            prefs.edit().putString("mode", "DASHSCOPE").commit()
            assertTrue(settings.current().endpoint.startsWith("https://dashscope.aliyuncs.com/"))
            assertEquals("short!", settings.current().credential)
            settings.save(ServiceConfiguration(ServiceMode.OFFLINE))
            assertFalse(prefs.contains("provider_key"))
            prefs.edit().remove("mode").putString("endpoint", "https://example.cn").putString("access_code", "x".repeat(32)).commit()
            assertEquals(ServiceMode.GATEWAY, settings.current().mode)
        } finally { prefs.edit().clear().commit() }
    }
}
