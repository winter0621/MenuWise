package com.example.careium.core.ai

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.careium.frontend.home.activities.AiCalorieActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses public accessibility APIs: the project's older Espresso injector cannot run on API 36. */
@RunWith(AndroidJUnit4::class)
class AiCalorieUiTest {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun isolatedSettings(action: () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshots = listOf("ai_calorie_service", "ai_calorie_history").map {
            val prefs = context.getSharedPreferences(it, Context.MODE_PRIVATE)
            prefs to prefs.all.toMap()
        }
        snapshots.forEach { it.first.edit().clear().commit() }
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try { action() } finally {
            snapshots.forEach { (prefs, previous) ->
                prefs.edit().clear().apply { previous.forEach { (key, value) -> when (value) { is String -> putString(key, value); is Int -> putInt(key, value) } } }.commit()
            }
        }
    }
    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        return listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    }
    private fun awaitNode(scroll: Boolean = false, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) {
            val visible = nodes(automation.rootInActiveWindow)
            visible.firstOrNull { it.isVisibleToUser && predicate(it) }?.let { return it }
            if (scroll) visible.firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(200)
        }
        throw AssertionError("Expected UI element did not appear")
    }
    private fun id(name: String, scroll: Boolean = false) = awaitNode(scroll) { it.viewIdResourceName == "com.example.careium:id/$name" }
    private fun text(value: String) = awaitNode { it.text?.toString() == value }
    private fun click(node: AccessibilityNodeInfo) { assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); SystemClock.sleep(200) }
    private fun enter(node: AccessibilityNodeInfo, value: String) {
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
    }
    @Test fun oneKeyCanBeSavedWithoutTypingAnEndpoint() = isolatedSettings {
        ActivityScenario.launch(AiCalorieActivity::class.java).use {
            click(id("ai_settings"))
            assertFalse(nodes(automation.rootInActiveWindow).any { it.isVisibleToUser && it.className?.toString() == "android.widget.Spinner" })
            assertEquals(1, nodes(automation.rootInActiveWindow).count { it.isVisibleToUser && it.className?.toString() == "android.widget.EditText" })
            val input = awaitNode { it.className?.toString() == "android.widget.EditText" && it.isEditable }
            enter(input, "a!")
            click(text("保存"))
            awaitNode { it.viewIdResourceName == "com.example.careium:id/ai_service_status" && it.text.toString().contains("API 已配置") }
            val config = AiServiceSettings(ApplicationProvider.getApplicationContext<Context>()).current()
            assertEquals(ServiceConfiguration.DEEPSEEK_URL, config.endpoint)
            assertEquals(ServiceConfiguration.DEEPSEEK_MODEL, config.visionModel)
            click(id("ai_settings"))
            val saved = awaitNode { it.className?.toString() == "android.widget.EditText" && it.isEditable }
            assertFalse(saved.text?.toString().orEmpty().contains("a!"))
            click(text("取消"))
        }
    }
    @Test fun advancedCustomEndpointAndModelsAreSavedWithoutProviderSelection() = isolatedSettings {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch(AiCalorieActivity::class.java).use {
            click(id("ai_settings"))
            enter(awaitNode { it.isEditable && it.isPassword }, "key!")
            click(text("高级设置（可选）"))
            enter(text(ServiceConfiguration.DEEPSEEK_URL), "https://custom.example.cn/api/v2")
            enter(text(ServiceConfiguration.DEEPSEEK_MODEL), "custom-text")
            enter(text(ServiceConfiguration.DEEPSEEK_MODEL), "custom-vision")
            click(text("保存"))
            awaitNode { it.viewIdResourceName == "com.example.careium:id/ai_service_status" && it.text.toString().contains("API 已配置") }
            val saved = AiServiceSettings(context).current()
            assertEquals("https://custom.example.cn/api/v2/chat/completions", saved.endpoint)
            assertEquals("custom-text", saved.textModel)
            assertEquals("custom-vision", saved.visionModel)
            assertEquals("key!", saved.credential)
            click(id("ai_settings"))
            assertEquals(1, nodes(automation.rootInActiveWindow).count { it.isVisibleToUser && it.isEditable })
            click(text("保存"))
            assertEquals(saved, AiServiceSettings(context).current())
        }
    }
    @Test fun portionEditorUpdatesCaloriesAndPreservesUnknownNutrition() = isolatedSettings {
        ActivityScenario.launch(AiCalorieActivity::class.java).use {
            enter(id("ai_description", true), "rice200g,egg100g")
            click(id("ai_query", true))
            click(id("ai_adjust_portions", true))
            enter(text("200.0"), "100")
            click(text("重新计算"))
            val result = id("ai_result", true).text.toString()
            assertTrue(result.contains("285.0"))
            assertTrue(result.contains("蛋白质 未提供"))
        }
    }
}
