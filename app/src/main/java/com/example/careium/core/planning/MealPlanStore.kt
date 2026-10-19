package com.example.careium.core.planning

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import com.google.firebase.auth.FirebaseAuth

class MealPlanStore(context: Context) {
    // Keep plans, profile, and grocery state in a separate private store for each signed-in account.
    // A signed-out user still gets a local-only store.
    private val accountId = if (context.getSharedPreferences("menuwise_local", Context.MODE_PRIVATE).getBoolean("offline_mode", false)) "local" else FirebaseAuth.getInstance().currentUser?.uid ?: "local"
    private val preferences = context.getSharedPreferences("meal_plans_${accountId}", Context.MODE_PRIVATE)

    fun dates(): List<String> = preferences.all.keys.filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }.sortedDescending()
    fun weeklyReview(date: String, today: String = today()) =
        MealPlanReview.thisWeek(dates().mapNotNull { load(it) }, date, today)
    fun load(date: String): DailyMealPlan? = try {
        preferences.getString(date, null)?.let { decode(JSONObject(it)) }
    } catch (_: Exception) { null }
    fun save(plan: DailyMealPlan) {
        val editor = preferences.edit().putString(plan.date, encode(plan).toString())
            .putString("last_profile", profileJson(plan.profile).toString())
        editor.apply()
    }
    fun lastProfile(): PlanProfile? = try {
        preferences.getString("last_profile", null)?.let { decodeProfile(JSONObject(it)) }
    } catch (_: Exception) { null }
    fun groceryItems(today: String): List<GroceryItem> {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = format.parse(today) ?: return emptyList()
        val calendar = Calendar.getInstance().apply { time = start; add(Calendar.DATE, 7) }
        val end = format.format(calendar.time)
        val checked = try { JSONObject((preferences.getString("shopping_checklist", "{}") ?: "{}")) } catch (_: Exception) { JSONObject() }
        val expiries = expiryJson()
        val totals = linkedMapOf<String, Double>()
        dates().filter { it >= today && it < end }.forEach { date ->
            load(date)?.meals?.forEach { meal -> meal.foods.forEach { food ->
                val name = food.name.trim()
                if (name.isNotEmpty()) totals[name] = (totals[name] ?: 0.0) + food.grams
            } }
        }
        return totals.toSortedMap().map { (name, grams) -> GroceryItem(name, grams,
            checked.optBoolean(groceryKey(name), false), expiries.optString(groceryKey(name)).ifBlank { null }) }
    }
    fun setPurchased(name: String, purchased: Boolean) {
        val checked = try { JSONObject((preferences.getString("shopping_checklist", "{}") ?: "{}")) } catch (_: Exception) { JSONObject() }
        if (purchased) checked.put(groceryKey(name), true) else checked.remove(groceryKey(name))
        preferences.edit().putString("shopping_checklist", checked.toString()).apply()
    }
    fun setExpiry(name: String, date: String?) {
        val expiries = expiryJson()
        if (date == null) expiries.remove(groceryKey(name)) else {
            require(validDate(date)) { "保质期日期无效" }
            expiries.put(groceryKey(name), date)
        }
        check(preferences.edit().putString("grocery_expiries", expiries.toString()).commit()) { "保质期保存失败" }
    }
    fun expiryEntries(): List<Pair<String, String>> {
        val values = expiryJson()
        return values.keys().asSequence().mapNotNull { key -> values.optString(key).takeIf { validDate(it) }?.let { key to it } }.toList()
    }
    private fun expiryJson() = try { JSONObject((preferences.getString("grocery_expiries", "{}") ?: "{}")) } catch (_: Exception) { JSONObject() }
    private fun groceryKey(name: String) = name.trim().lowercase(Locale.ROOT)
    private fun validDate(value: String): Boolean = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value) != null
    } catch (_: Exception) { false }

    companion object {
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        private fun profileJson(p: PlanProfile) = JSONObject().put("age", p.age).put("height", p.heightCm)
            .put("weight", p.weightKg).put("sex", p.sex.name).put("activity", p.activity.name).put("goal", p.goal.name)
        private fun decodeProfile(p: JSONObject) = PlanProfile(p.getInt("age"), p.getDouble("height"),
            p.getDouble("weight"), PlanSex.valueOf(p.getString("sex")),
            PlanActivity.valueOf(p.getString("activity")), PlanGoal.valueOf(p.getString("goal")))
        fun foodJson(food: PlannedFood) = JSONObject().put("name", food.name).put("grams", food.grams)
            .put("calories_kcal", food.calories).put("eaten_grams", food.eatenGrams ?: JSONObject.NULL)
            .put("protein_g", food.nutrition.protein ?: JSONObject.NULL)
            .put("carbs_g", food.nutrition.carbs ?: JSONObject.NULL)
            .put("fat_g", food.nutrition.fat ?: JSONObject.NULL)
        fun readFood(food: JSONObject): PlannedFood = PlannedFood(food.getString("name"),
            food.getDouble("grams"), food.getDouble("calories_kcal"),
            if (food.isNull("eaten_grams")) null else food.getDouble("eaten_grams"),
            MacroNutrients(optionalNumber(food, "protein_g"), optionalNumber(food, "carbs_g"), optionalNumber(food, "fat_g"))).also { it.validate() }
        private fun optionalNumber(value: JSONObject, key: String): Double? = if (value.isNull(key)) null else value.getDouble(key)
        fun encode(plan: DailyMealPlan): JSONObject = JSONObject().put("date", plan.date)
            .put("profile", profileJson(plan.profile)).put("bmr", plan.bmr).put("tdee", plan.tdee)
            .put("target", plan.target).put("preferences", plan.preferences).put("notes", JSONArray(plan.notes))
            .put("custom_target", plan.customTarget ?: JSONObject.NULL).put("excluded_foods", plan.excludedFoods)
            .put("meals", JSONArray().apply {
                plan.meals.forEach { meal -> put(JSONObject().put("id", meal.id).put("label", meal.label)
                    .put("percent", meal.percent).put("target", meal.target)
                    .put("reminder_minute", meal.reminderMinute ?: JSONObject.NULL)
                    .put("foods", JSONArray().apply { meal.foods.forEach { put(foodJson(it)) } })) }
            })
        fun decode(value: JSONObject): DailyMealPlan {
            val meals = value.getJSONArray("meals")
            val notes = value.optJSONArray("notes") ?: JSONArray()
            return DailyMealPlan(value.getString("date"), decodeProfile(value.getJSONObject("profile")),
                value.getInt("bmr"), value.getInt("tdee"), value.getInt("target"), value.optString("preferences"),
                (0 until meals.length()).map { index ->
                    val meal = meals.getJSONObject(index)
                    val foods = meal.getJSONArray("foods")
                    PlannedMeal(meal.getString("id"), meal.getString("label"), meal.getInt("percent"),
                        meal.getInt("target"), (0 until foods.length()).map { readFood(foods.getJSONObject(it)) },
                        if (meal.isNull("reminder_minute")) null else meal.getInt("reminder_minute"))
                }, (0 until notes.length()).map { notes.getString(it) },
                if (value.isNull("custom_target")) null else value.getInt("custom_target"),
                value.optString("excluded_foods", ""))
        }
    }
}
