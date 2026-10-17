package com.example.careium.core.ai

import com.example.careium.core.planning.MacroNutrients
import com.example.careium.core.planning.NutrientTotal
import org.json.JSONObject

/** Keeps absent nutrition distinct from a measured/estimated zero, including old offline history. */
object CalorieResult {
    fun nutrition(item: JSONObject, result: JSONObject): MacroNutrients {
        if (result.optJSONArray("assumptions")?.toString()?.contains("未提供宏量营养素") == true) return MacroNutrients()
        fun value(key: String): Double? = if (item.isNull(key)) null else item.getDouble(key)
        return MacroNutrients(value("protein_g"), value("carbs_g"), value("fat_g")).also { it.validate() }
    }

    fun nutrientTotal(result: JSONObject, select: (MacroNutrients) -> Double?): NutrientTotal {
        val items = result.getJSONArray("items")
        val values = (0 until items.length()).map { select(nutrition(items.getJSONObject(it), result)) }
        return NutrientTotal(values.filterNotNull().sum(), values.count { it != null }, values.size)
    }
}
