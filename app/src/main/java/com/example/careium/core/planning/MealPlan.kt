package com.example.careium.core.planning

import kotlin.math.floor
import kotlin.math.roundToInt

enum class PlanSex(val label: String, val offset: Int, val referenceFloor: Int) {
    FEMALE("女性", -161, 1200), MALE("男性", 5, 1500)
}
enum class PlanActivity(val label: String, val factor: Double) {
    SEDENTARY("久坐，基本不运动", 1.2), LIGHT("轻度活动，每周运动1–3次", 1.375),
    MODERATE("中度活动，每周运动3–5次", 1.55), HIGH("较高活动，每周运动6–7次", 1.725)
}
enum class PlanGoal(val label: String) { MAINTAIN("维持体重"), LOSE("温和减脂"), GAIN("温和增重") }

data class PlanProfile(val age: Int, val heightCm: Double, val weightKg: Double,
    val sex: PlanSex, val activity: PlanActivity, val goal: PlanGoal)
data class MacroNutrients(val protein: Double? = null, val carbs: Double? = null, val fat: Double? = null) {
    fun validate() {
        require(listOf(protein, carbs, fat).all { it == null || it.isFinite() && it in 0.0..10000.0 }) {
            "营养素需为0–10000克，未知时请留空"
        }
    }
}

data class NutrientTotal(val grams: Double, val knownItems: Int, val totalItems: Int) {
    fun display(): String = when {
        totalItems == 0 || knownItems == 0 -> "未提供"
        knownItems < totalItems -> "已知 %.1f 克（%d/%d项）".format(java.util.Locale.CHINA, grams, knownItems, totalItems)
        else -> "%.1f 克".format(java.util.Locale.CHINA, grams)
    }
}

data class PlannedFood(val name: String, val grams: Double, val calories: Double, val eatenGrams: Double? = null,
    val nutrition: MacroNutrients = MacroNutrients()) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 100) { "食物名称需为1–100个字符" }
        require(grams.isFinite() && grams > 0 && grams <= 10000) { "重量需大于0且不超过10000克" }
        require(calories.isFinite() && calories >= 0 && calories <= 20000) { "请填写有效的食物热量" }
        require(eatenGrams == null || eatenGrams.isFinite() && eatenGrams >= 0 && eatenGrams <= 10000) { "实际食用重量需在0–10000克之间" }
        nutrition.validate()
    }
}
data class PlannedMeal(val id: String, val label: String, val percent: Int, val target: Int,
    val foods: List<PlannedFood> = emptyList(), val reminderMinute: Int? = null) {
    val total: Double get() = foods.sumOf { it.calories }
    val eaten: Double get() = foods.sumOf { food -> food.eatenGrams?.let { food.calories * it / food.grams } ?: 0.0 }
}
data class DailyMealPlan(val date: String, val profile: PlanProfile, val bmr: Int, val tdee: Int,
    val target: Int, val preferences: String, val meals: List<PlannedMeal>, val notes: List<String> = emptyList(),
    val customTarget: Int? = null, val excludedFoods: String = "") {
    val total: Double get() = meals.sumOf { it.total }
    val eaten: Double get() = meals.sumOf { it.eaten }
    val trackedFoods: Int get() = meals.sumOf { meal -> meal.foods.count { it.eatenGrams != null } }
    fun copyToDate(newDate: String): DailyMealPlan {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { isLenient = false }
        require(newDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && runCatching { format.parse(newDate) }.getOrNull() != null) { "日期无效" }
        require(newDate != date) { "请选择不同的日期" }
        return copy(date = newDate, meals = meals.map { meal ->
            meal.copy(reminderMinute = null, foods = meal.foods.map { it.copy(eatenGrams = null) })
        })
    }
    fun replaceFoods(mealId: String, foods: List<PlannedFood>): DailyMealPlan {
        require(foods.size <= 30) { "每餐最多安排30项食物" }
        foods.forEach { it.validate() }
        require(meals.any { it.id == mealId }) { "找不到这餐" }
        return copy(meals = meals.map { if (it.id == mealId) it.copy(foods = foods) else it })
    }
}

data class GroceryItem(val name: String, val grams: Double, val purchased: Boolean)
data class WeeklyReview(val firstDate: String, val lastDate: String, val loggedDays: Int,
    val recordedKcal: Double, val loggedTargetKcal: Int, val plans: List<DailyMealPlan> = emptyList(),
    val throughDate: String = lastDate) {
    val averageRecordedKcal: Double get() = if (loggedDays == 0) 0.0 else recordedKcal / loggedDays
    fun nutrient(select: (MacroNutrients) -> Double?): NutrientTotal {
        val foods = plans.flatMap { it.meals }.flatMap { it.foods }.filter { it.eatenGrams != null }
        val known = foods.filter { select(it.nutrition) != null }
        return NutrientTotal(known.sumOf { select(it.nutrition)!! * it.eatenGrams!! / it.grams }, known.size, foods.size)
    }
}

object MealPlanReview {
    private fun formatter() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { isLenient = false }
    private fun parse(value: String): java.util.Date {
        require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "计划日期格式错误" }
        return try { formatter().parse(value)!! } catch (_: java.text.ParseException) { throw IllegalArgumentException("计划日期无效") }
    }
    /** Past weeks include Sunday; the current week ends at today. Calendar handles month/year and DST boundaries. */
    fun reportDate(today: String, weeksAgo: Int): String {
        require(weeksAgo >= 0)
        val calendar = java.util.Calendar.getInstance().apply { time = parse(today) }
        if (weeksAgo == 0) return today
        while (calendar.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) calendar.add(java.util.Calendar.DATE, -1)
        calendar.add(java.util.Calendar.DATE, 6 - 7 * weeksAgo)
        return formatter().format(calendar.time)
    }
    fun thisWeek(plans: List<DailyMealPlan>, dateInWeek: String, today: String = dateInWeek): WeeklyReview {
        val formatter = formatter()
        val date = parse(dateInWeek)
        parse(today)
        val calendar = java.util.Calendar.getInstance().apply { time = date; firstDayOfWeek = java.util.Calendar.MONDAY; minimalDaysInFirstWeek = 4 }
        while (calendar.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) calendar.add(java.util.Calendar.DATE, -1)
        val monday = formatter.format(calendar.time)
        calendar.add(java.util.Calendar.DATE, 7)
        val nextMonday = formatter.format(calendar.time)
        val throughDate = minOf(dateInWeek, today)
        val included = plans.filter { it.date >= monday && it.date < nextMonday && it.date <= throughDate }.sortedBy { it.date }
        val tracked = included.filter { it.trackedFoods > 0 }
        calendar.add(java.util.Calendar.DATE, -1)
        return WeeklyReview(monday, formatter.format(calendar.time), tracked.size,
            tracked.sumOf { it.eaten }, tracked.sumOf { it.target }, included, throughDate)
    }
}

object MealPlanCalculator {
    val mealIds = listOf("breakfast", "lunch", "dinner", "snack")
    val mealLabels = listOf("早餐", "午餐", "晚餐", "加餐")
    val defaultPercents = listOf(25, 35, 30, 10)

    fun create(date: String, profile: PlanProfile, percents: List<Int> = defaultPercents,
        customTarget: Int? = null, preferences: String = "", excludedFoods: String = ""): DailyMealPlan {
        require(profile.age in 18..80) { "此通用估算适用于18–80岁的成年人" }
        require(profile.heightCm.isFinite() && profile.heightCm in 120.0..230.0) { "身高需为120–230厘米" }
        require(profile.weightKg.isFinite() && profile.weightKg in 30.0..250.0) { "体重需为30–250千克" }
        require(preferences.length <= 500) { "饮食偏好最多500个字符" }
        val excluded = excludedFoods.split(',', '，', '、', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }
        require(excludedFoods.length <= 300 && excluded.size <= 20 && excluded.all { it.length <= 30 }) {
            "忌口食材最多20种，每项最多30个字符"
        }
        val bmi = profile.weightKg / (profile.heightCm / 100).let { it * it }
        require(profile.goal != PlanGoal.LOSE || bmi >= 18.5) { "当前体重偏低，不适用减脂目标，请选择维持或咨询专业人士" }
        val bmr = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.age + profile.sex.offset
        val tdee = bmr * profile.activity.factor
        val low = maxOf(profile.sex.referenceFloor, kotlin.math.ceil(tdee * 0.8 / 10).toInt() * 10)
        val high = maxOf(low, floor(tdee * 1.2 / 10).toInt() * 10)
        val suggested = when (profile.goal) {
            PlanGoal.MAINTAIN -> tdee
            PlanGoal.LOSE -> tdee - minOf(300.0, tdee * 0.1)
            PlanGoal.GAIN -> tdee + minOf(200.0, tdee * 0.1)
        }
        val target = customTarget ?: ((suggested / 10).roundToInt() * 10).coerceIn(low, high)
        require(target in low..high) { "按当前资料，自定义目标请设为 $low–$high 千卡；超出范围请先核对资料或咨询专业人士" }
        val budgets = allocate(target, percents)
        return DailyMealPlan(date, profile, bmr.roundToInt(), tdee.roundToInt(), target, preferences.trim(),
            mealIds.indices.map { PlannedMeal(mealIds[it], mealLabels[it], percents[it], budgets[it]) },
            customTarget = customTarget, excludedFoods = excluded.distinct().joinToString("、"))
    }

    fun allocate(target: Int, percents: List<Int>): List<Int> {
        require(target > 0) { "每日目标必须大于0" }
        require(percents.size == 4 && percents.all { it in 0..100 } && percents.sum() == 100) {
            "早餐、午餐、晚餐和加餐比例必须为0–100的整数，合计100%"
        }
        val values = percents.map { target * it / 100 }.toMutableList()
        val order = percents.indices.sortedByDescending { target * percents[it] % 100 }
        repeat(target - values.sum()) { values[order[it]]++ }
        return values
    }
}
