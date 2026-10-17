package com.example.careium.core.ai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

object CaloriePortions {
    fun adjust(original: JSONObject, grams: List<Double>): JSONObject {
        val result = JSONObject(original.toString())
        val items = result.getJSONArray("items")
        require(grams.size == items.length() && grams.all { it.isFinite() && it > 0 && it <= 10000 }) { "每项重量需大于0且不超过10000克" }
        for (index in grams.indices) {
            val item = items.getJSONObject(index)
            val previous = item.getDouble("grams")
            require(previous.isFinite() && previous > 0) { "原始份量无效，请重新查询" }
            val next = round(grams[index] * 10) / 10
            require(next > 0) { "重量至少为0.1克" }
            val ratio = next / previous
            for (field in listOf("calories_kcal", "protein_g", "carbs_g", "fat_g", "calories_min_kcal", "calories_max_kcal")) {
                if (!item.isNull(field)) {
                    val adjusted = round(item.getDouble(field) * ratio * 10) / 10
                    require(adjusted.isFinite() && adjusted in 0.0..(if (field.startsWith("calories")) 20000.0 else 10000.0)) { "调整后数值过大，请核对份量" }
                    item.put(field, adjusted)
                }
            }
            item.put("grams", next)
        }
        result.put("totals", NutritionResponse.totals(items)).put("portion_adjusted", true)
        val notes = result.optJSONArray("assumptions") ?: JSONArray()
        val updated = JSONArray()
        for (index in 0 until minOf(notes.length(), 7)) updated.put(notes.get(index))
        updated.put("份量已按填写的克数校准；热量和营养素按原估算密度折算，用油与食材假设仍需核对。")
        return result.put("assumptions", updated)
    }
}
