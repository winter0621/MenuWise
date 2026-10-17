package com.example.careium.core.ai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

/** Validate model data and compute totals instead of accepting model arithmetic. */
object NutritionResponse {
    val fields = listOf("grams", "calories_kcal", "protein_g", "carbs_g", "fat_g")
    fun number(row: JSONObject, key: String, maximum: Double): Double {
        val raw = row.opt(key)
        require(raw is Number) { "营养数值格式异常，请重试" }
        val number = raw.toDouble()
        require(number.isFinite() && number in 0.0..maximum) { "营养数值超出合理范围，请补充份量后重试" }
        return round(number * 10) / 10
    }
    fun normalize(value: JSONObject): JSONObject {
        if (value.has("food_detected")) {
            require(value.opt("food_detected") is Boolean) { "食物识别结果格式异常" }
            require(value.getBoolean("food_detected")) { "未能识别食物，请拍清整盘或补充食物名称" }
        }
        val items = value.getJSONArray("items")
        require(items.length() in 1..20) { "食物识别列表异常，请重试" }
        val notes = value.getJSONArray("assumptions")
        require(notes.length() <= 8 && (0 until notes.length()).all { notes.opt(it) is String && notes.getString(it).length <= 300 }) { "份量说明格式异常" }
        val clean = JSONArray()
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            require(item.opt("name") is String && item.getString("name").trim().length in 1..100) { "食物名称格式异常" }
            val row = JSONObject().put("name", item.getString("name").trim())
            fields.forEach { field -> row.put(field, number(item, field, if (field == "calories_kcal") 20000.0 else 10000.0)) }
            val grams = row.getDouble("grams")
            require(grams > 0 && row.getDouble("calories_kcal") <= grams * 9 + 20) { "食物份量与热量不一致，请核对后重试" }
            require(listOf("protein_g", "carbs_g", "fat_g").sumOf { row.getDouble(it) } <= grams * 1.1 + 1) { "营养素与食物重量不一致，请重试" }
            if (item.has("calories_min_kcal") || item.has("calories_max_kcal")) {
                val low = number(item, "calories_min_kcal", 20000.0)
                val high = number(item, "calories_max_kcal", 20000.0)
                require(low <= row.getDouble("calories_kcal") && row.getDouble("calories_kcal") <= high) { "热量估算范围异常，请重试" }
                row.put("calories_min_kcal", low).put("calories_max_kcal", high)
            }
            clean.put(row)
        }
        return JSONObject().put("items", clean).put("totals", totals(clean)).put("assumptions", notes).put("estimated", true)
    }
    fun totals(items: JSONArray): JSONObject {
        val total = JSONObject()
        for (field in fields + listOf("calories_min_kcal", "calories_max_kcal")) {
            val rows = (0 until items.length()).map { items.getJSONObject(it) }
            val known = rows.all { !it.isNull(field) }
            if (known) total.put(field, round(rows.sumOf { it.getDouble(field) } * 10) / 10)
            else if (field in fields) total.put(field, JSONObject.NULL)
        }
        return total
    }
}
