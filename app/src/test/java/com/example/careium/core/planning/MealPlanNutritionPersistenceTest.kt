package com.example.careium.core.planning

import com.example.careium.core.ai.CalorieResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MealPlanNutritionPersistenceTest {
    @Test fun savedPlanRoundTripPreservesPortionsAndOptionalNutrition() {
        val plan = MealPlanCalculator.create("2026-10-17", PlanProfile(30, 175.0, 70.0,
            PlanSex.MALE, PlanActivity.SEDENTARY, PlanGoal.MAINTAIN))
            .replaceFoods("lunch", listOf(
                PlannedFood("鸡蛋", 100.0, 155.0, 150.0, MacroNutrients(13.0, null, 0.0)),
                PlannedFood("未知营养素", 200.0, 260.0)))
        val restored = MealPlanStore.decode(JSONObject(MealPlanStore.encode(plan).toString()))
        assertEquals(plan, restored)
        val review = MealPlanReview.thisWeek(listOf(restored), "2026-10-17")
        assertEquals(19.5, review.nutrient { it.protein }.grams, 0.001)
        assertEquals("未提供", review.nutrient { it.carbs }.display())
        assertEquals("0.0 克", review.nutrient { it.fat }.display())
    }
    @Test fun oldFoodWithoutNutrientsStillLoadsAsUnknown() {
        val oldFood = JSONObject().put("name", "米饭").put("grams", 100).put("calories_kcal", 130).put("eaten_grams", 50)
        val food = MealPlanStore.readFood(oldFood)
        assertEquals(MacroNutrients(), food.nutrition)
        assertEquals(50.0, food.eatenGrams!!, 0.001)
    }
    @Test fun importedOnlineNutrientsAreKeptAndNeverAutoMarkedEaten() {
        val item = JSONObject().put("name", "鸡蛋").put("grams", 100).put("calories_kcal", 155)
            .put("protein_g", 13).put("carbs_g", 1).put("fat_g", 10)
        val food = MealPlanStore.readFood(item)
        assertNull(food.eatenGrams)
        assertEquals(MacroNutrients(13.0, 1.0, 10.0), food.nutrition)
    }
    @Test fun oldOfflineHistoryZerosAreRenderedAsUnknown() {
        val item = JSONObject().put("protein_g", 0).put("carbs_g", 0).put("fat_g", 0)
        val result = JSONObject().put("items", JSONArray().put(item))
            .put("assumptions", JSONArray().put("离线食物库的约略热量估算，未提供宏量营养素"))
        assertEquals(MacroNutrients(), CalorieResult.nutrition(item, result))
        assertEquals("未提供", CalorieResult.nutrientTotal(result) { it.protein }.display())
        result.put("assumptions", JSONArray())
        assertEquals("0.0 克", CalorieResult.nutrientTotal(result) { it.protein }.display())
    }
    @Test fun mixedQueryResultsShowCoverageInsteadOfACompleteTotal() {
        val result = JSONObject().put("items", JSONArray()
            .put(JSONObject().put("protein_g", 12))
            .put(JSONObject().put("protein_g", JSONObject.NULL)))
        assertEquals("已知 12.0 克（1/2项）", CalorieResult.nutrientTotal(result) { it.protein }.display())
    }
}
