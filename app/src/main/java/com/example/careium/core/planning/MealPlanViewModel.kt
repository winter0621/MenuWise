package com.example.careium.core.planning

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.careium.core.ai.CalorieClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.abs

data class MealPlanState(val date: String, val plan: DailyMealPlan? = null, val busy: Boolean = false,
    val suggestion: DailyMealPlan? = null, val error: String? = null)

class MealPlanViewModel(application: Application) : AndroidViewModel(application) {
    private val store = MealPlanStore(application)
    private val executor = Executors.newSingleThreadExecutor()
    val state = MutableLiveData(MealPlanState(MealPlanStore.today(), store.load(MealPlanStore.today())))
    fun profile(): PlanProfile? = state.value?.plan?.profile ?: store.lastProfile()
    fun savedDates() = store.dates()
    fun selectDate(date: String) {
        if (state.value?.busy != true) state.value = MealPlanState(date, store.load(date))
    }
    fun calculate(profile: PlanProfile, percents: List<Int>, target: Int?, preferences: String, excludedFoods: String) {
        val old = state.value!!
        if (old.busy) return
        val calculated = MealPlanCalculator.create(old.date, profile, percents, target, preferences, excludedFoods)
        // A budget change preserves manually arranged dishes, but invalidates any pending AI preview.
        val plan = calculated.copy(meals = calculated.meals.map { meal ->
            val existing = old.plan?.meals?.find { it.id == meal.id }
            meal.copy(foods = existing?.foods ?: emptyList(), reminderMinute = existing?.reminderMinute)
        })
        save(plan)
    }
    private fun save(plan: DailyMealPlan) {
        store.save(plan)
        state.value = MealPlanState(plan.date, plan)
    }
    fun editFood(mealId: String, index: Int?, food: PlannedFood?) {
        val old = state.value!!
        if (old.busy) return
        val plan = old.plan ?: throw IllegalArgumentException("请先计算每日目标")
        val foods = plan.meals.first { it.id == mealId }.foods.toMutableList()
        if (index == null && food != null) foods.add(food)
        else if (index != null && food == null) foods.removeAt(index)
        else if (index != null && food != null) foods[index] = food.copy(eatenGrams = foods[index].eatenGrams)
        save(plan.replaceFoods(mealId, foods))
    }
    fun importQuery(mealId: String, result: JSONObject) {
        val old = state.value!!
        if (old.busy) return
        val plan = old.plan ?: throw IllegalArgumentException("请先计算每日目标")
        val items = result.getJSONArray("items")
        require(items.length() in 1..20) { "查询结果格式不正确" }
        val foods = (0 until items.length()).map {
            MealPlanStore.readFood(items.getJSONObject(it)).copy(eatenGrams = null,
                nutrition = com.example.careium.core.ai.CalorieResult.nutrition(items.getJSONObject(it), result))
        }
        save(plan.replaceFoods(mealId, plan.meals.first { it.id == mealId }.foods + foods))
    }
    fun setFoodEaten(mealId: String, index: Int, grams: Double?) {
        val plan = state.value?.plan ?: error("请先保存计划")
        val meal = plan.meals.first { it.id == mealId }
        val food = meal.foods[index]
        require(grams == null || grams.isFinite() && grams > 0 && grams <= 10000) { "实际食用克数需大于0且不超过10000克" }
        val updated = food.copy(eatenGrams = grams)
        save(plan.replaceFoods(mealId, meal.foods.toMutableList().also { it[index] = updated }))
    }
    fun setReminder(mealId: String, minute: Int?) {
        require(minute == null || minute in 0 until 24 * 60) { "提醒时间无效" }
        val plan = state.value?.plan ?: error("请先保存计划")
        val updated = plan.copy(meals = plan.meals.map { if (it.id == mealId) it.copy(reminderMinute = minute) else it })
        save(updated)
    }
    fun generateSuggestions() {
        val old = state.value!!
        if (old.busy) return
        val plan = old.plan ?: throw IllegalArgumentException("请先计算每日目标")
        val config = com.example.careium.core.ai.AiServiceSettings(getApplication()).current()
        require(config.online) { "请先在热量查询页面的 API 设置中填写 API Key 或配置自建服务" }
        state.value = old.copy(busy = true, suggestion = null, error = null)
        executor.execute {
            try {
                val payload = JSONObject().put("preferences", plan.preferences).put("excluded_foods", plan.excludedFoods).put("meals", JSONArray().apply {
                    plan.meals.filter { it.target > 0 }.forEach {
                        put(JSONObject().put("id", it.id).put("target_kcal", it.target))
                    }
                })
                val result = CalorieClient().plan(config, payload)
                val meals = result.getJSONArray("meals")
                val returned = (0 until meals.length()).map { meals.getJSONObject(it) }
                val expected = plan.meals.filter { it.target > 0 }.map { it.id }.toSet()
                require(returned.size == expected.size && returned.map { it.getString("id") }.toSet() == expected) { "AI返回的餐次不完整，请重试" }
                val suggested = plan.meals.map { meal ->
                    val entry = returned.find { it.getString("id") == meal.id }
                    if (entry == null) meal.copy(foods = emptyList()) else {
                        val items = entry.getJSONArray("items")
                        require(items.length() in 1..8) { "AI返回的食物数量异常" }
                        val foods = (0 until items.length()).map { MealPlanStore.readFood(items.getJSONObject(it)) }
                        require(abs(foods.sumOf { it.calories } - meal.target) <= maxOf(50.0, meal.target * 0.15)) { "AI配餐热量与目标相差较大，请重试" }
                        meal.copy(foods = foods)
                    }
                }
                val notes = result.getJSONArray("notes")
                require(notes.length() <= 8) { "AI返回的说明格式不正确" }
                val preview = plan.copy(meals = suggested, notes = (0 until notes.length()).map { notes.getString(it) })
                state.postValue(old.copy(suggestion = preview, busy = false, error = null))
            } catch (error: Exception) {
                val message = if (error is java.io.IOException) "无法连接配餐服务，请检查网络或稍后重试" else error.message ?: "生成建议失败"
                state.postValue(old.copy(busy = false, suggestion = null, error = message))
            }
        }
    }
    fun adoptSuggestion() { state.value?.suggestion?.let { save(it) } }
    fun dismissSuggestion() { state.value = state.value?.copy(suggestion = null) }
    fun copyToDate(date: String, overwrite: Boolean = false) {
        require(state.value?.busy != true) { "请等待当前操作完成" }
        val plan = state.value?.plan ?: error("请先保存计划")
        val copied = plan.copyToDate(date)
        require(overwrite || store.load(date) == null) { "该日期已有计划" }
        store.load(date)?.meals?.forEach { MealPlanReminder.cancel(getApplication(), date, it.id) }
        save(copied)
    }
    fun shoppingItems(date: String = MealPlanStore.today()) = store.groceryItems(date)
    fun markPurchased(name: String, purchased: Boolean) = store.setPurchased(name, purchased)
    fun weeklyReview(date: String = MealPlanStore.today()) = store.weeklyReview(date)
    fun savedPlans() = store.dates().mapNotNull { store.load(it) }
    override fun onCleared() { executor.shutdownNow(); super.onCleared() }
}
