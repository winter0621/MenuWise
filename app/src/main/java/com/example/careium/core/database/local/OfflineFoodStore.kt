package com.example.careium.core.database.local

import android.content.Context
import com.example.careium.core.database.authentication.LocalAccountStore
import org.json.JSONArray
import org.json.JSONObject

/** Device-local meal log used by guest/offline mode. */
class OfflineFoodStore(context: Context) {
    private val preferences = context.getSharedPreferences("offline_food_log_" + LocalAccountStore.currentAccountId(context), Context.MODE_PRIVATE)

    fun add(name: String, nutrition: List<Float>) {
        val entries = read()
        entries.put(JSONObject()
            .put("time", System.currentTimeMillis())
            .put("name", name)
            .put("calories", nutrition.getOrElse(0) { 0f })
            .put("grams", nutrition.getOrElse(1) { 0f })
            .put("fat", nutrition.getOrElse(2) { 0f })
            .put("carbs", nutrition.getOrElse(3) { 0f })
            .put("protein", nutrition.getOrElse(4) { 0f }))
        preferences.edit().putString("entries", entries.toString()).apply()
    }

    fun read(): JSONArray = try { JSONArray(preferences.getString("entries", "[]")) } catch (_: Exception) { JSONArray() }
}
