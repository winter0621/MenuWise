package com.example.careium.core.planning

import kotlin.math.roundToInt

data class FoodSubstitution(
    val food: PlannedFood,
    val reason: String
)

/**
 * Small offline substitution catalogue. Values are reference estimates for a
 * prepared edible portion, so the user still reviews them before applying.
 */
object FoodSubstitutionCatalog {
    private data class Template(
        val name: String,
        val caloriesPer100g: Double,
        val proteinPer100g: Double?,
        val carbsPer100g: Double?,
        val fatPer100g: Double?,
        val reason: String
    )

    fun suggestions(food: PlannedFood, excludedFoods: String): List<FoodSubstitution> {
        val templates = when {
            food.name.containsAny("猪", "五花", "培根", "香肠", "腊") -> listOf(
                Template("去皮鸡胸肉", 118.0, 24.0, 0.0, 2.0, "同类蛋白质来源，通常脂肪更低"),
                Template("清蒸鱼肉", 105.0, 20.0, 0.0, 2.0, "同类蛋白质来源，烹调更清淡")
            )
            food.name.containsAny("鸡", "牛", "羊", "鱼", "虾", "肉", "蛋", "豆腐", "豆干") -> listOf(
                Template("去皮鸡胸肉", 118.0, 24.0, 0.0, 2.0, "高蛋白、低脂的常见替换"),
                Template("北豆腐", 81.0, 8.1, 1.9, 4.2, "植物蛋白替换，适合换口味")
            )
            food.name.containsAny("米饭", "炒饭", "白米", "面条", "面包", "馒头", "饼") -> listOf(
                Template("糙米饭", 116.0, 2.6, 25.9, 0.9, "全谷物替换，膳食纤维通常更多"),
                Template("蒸红薯", 86.0, 1.6, 20.1, 0.1, "天然主食替换，饱腹感更强")
            )
            food.name.containsAny("牛奶", "酸奶", "奶酪", "奶茶") -> listOf(
                Template("无糖高蛋白酸奶", 72.0, 8.5, 5.0, 1.5, "减少添加糖风险，同时补充蛋白质"),
                Template("低脂牛奶", 46.0, 3.4, 5.0, 1.5, "较低脂的乳制品替换")
            )
            else -> emptyList()
        }
        val exclusions = excludedFoods.split(',', '，', '、', ';', '；')
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }
        return templates.filterNot { option -> exclusions.any { excluded -> option.name.lowercase().contains(excluded) } }
            .map { option -> FoodSubstitution(toFood(option, food.calories), option.reason) }
    }

    private fun toFood(template: Template, sourceCalories: Double): PlannedFood {
        val grams = ((sourceCalories.coerceAtLeast(20.0) / template.caloriesPer100g) * 100.0)
            .coerceIn(30.0, 500.0)
        val factor = grams / 100.0
        return PlannedFood(
            name = template.name,
            grams = (grams * 10).roundToInt() / 10.0,
            calories = (template.caloriesPer100g * factor * 10).roundToInt() / 10.0,
            nutrition = MacroNutrients(
                template.proteinPer100g?.times(factor),
                template.carbsPer100g?.times(factor),
                template.fatPer100g?.times(factor)
            )
        )
    }

    private fun String.containsAny(vararg values: String) = values.any { contains(it, ignoreCase = true) }
}
