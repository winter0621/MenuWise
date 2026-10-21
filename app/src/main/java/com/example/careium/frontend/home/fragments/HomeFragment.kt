package com.example.careium.frontend.home.fragments

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.careium.R
import com.example.careium.core.planning.MealPlanStore
import com.example.careium.databinding.FragmentHomeBinding
import com.example.careium.frontend.factory.ClassifierViewModel
import com.example.careium.frontend.factory.NutritionViewModel
import com.example.careium.frontend.home.activities.AiCalorieActivity
import com.example.careium.frontend.home.activities.MealPlanActivity
import com.example.careium.frontend.home.activities.WellnessInsightsActivity
import com.example.careium.core.database.authentication.LocalAccountStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.max

private data class MacroBudget(val target: Double, val eaten: Double, val knownItems: Int, val totalItems: Int)

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var viewBinding: FragmentHomeBinding? = null
    private val binding get() = viewBinding!!
    private var daysFrom = 0

    companion object { @JvmStatic fun newInstance() = HomeFragment() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding = FragmentHomeBinding.bind(view)
        daysFrom = savedInstanceState?.getInt("days_from") ?: 0
        binding.imageButtonPrevDate.setOnClickListener { daysFrom++; refreshDay() }
        binding.imageButtonNextDate.setOnClickListener { daysFrom = (daysFrom - 1).coerceAtLeast(0); refreshDay() }
        binding.buttonMealPlan.setOnClickListener { openPlan() }
        binding.buttonRecordMeal.setOnClickListener { openPlan() }
        binding.homeMealSummary.setOnClickListener { openPlan() }
        binding.buttonAiCalories.setOnClickListener { startActivity(Intent(requireContext(), AiCalorieActivity::class.java)) }
        binding.buttonWellnessInsights.setOnClickListener { startActivity(Intent(requireContext(), WellnessInsightsActivity::class.java)) }
        binding.cardTodayTasks.setOnClickListener { startActivity(Intent(requireContext(), WellnessInsightsActivity::class.java)) }
        val classifier = ViewModelProvider(requireActivity()).get(ClassifierViewModel::class.java)
        classifier.mutableDishName.observe(viewLifecycleOwner) { binding.textLastCaptured.text = it }
        classifier.mutableDishImage.observe(viewLifecycleOwner) {
            binding.imageLastCaptured.setImageDrawable(it)
            binding.cardLastCaptured.visibility = View.VISIBLE
            binding.homeCaptureHeading.visibility = View.VISIBLE
            binding.textLastCapturedPlaceholder.text = "拍照估算保存在原有云端记录中；上方汇总来自本机计划。"
        }
        val nutrition = ViewModelProvider(requireActivity()).get(NutritionViewModel::class.java)
        nutrition.mutableNutrition.observe(viewLifecycleOwner) { values ->
            if (values.size >= 5) binding.textLastCapturedPlaceholder.text =
                "拍照估算约 ${values[0].roundToInt()} 千卡；未计入本机计划。"
        }
        refreshDay()
    }

    private fun selectedCalendar() = Calendar.getInstance().apply { add(Calendar.DATE, -daysFrom) }
    private fun selectedDate() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCalendar().time)
    private fun openPlan() {
        startActivity(Intent(requireContext(), MealPlanActivity::class.java).putExtra("plan_date", selectedDate()))
    }
    private fun refreshDay() {
        val plan = MealPlanStore(requireContext()).load(selectedDate())
        binding.textCalendarDate.text = SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(selectedCalendar().time)
        binding.imageButtonNextDate.visibility = if (daysFrom == 0) View.INVISIBLE else View.VISIBLE
        binding.progressHomeCircular.max = plan?.target ?: 100
        binding.progressHomeCircular.progress = plan?.eaten?.roundToInt()?.coerceAtMost(plan.target) ?: 0
        binding.texTProgressHome.text = plan?.let { (it.target - it.eaten).coerceAtLeast(0.0).roundToInt().toString() } ?: "—"
        binding.homeTargetLabel.text = plan?.let { "目标 ${it.target} 千卡" } ?: "先设置目标"
        binding.homePlanSummary.text = plan?.let {
            val difference = it.eaten - it.target
            "已记录 ${it.eaten.roundToInt()} 千卡" + if (difference > 0) "\n超出 ${difference.roundToInt()} 千卡" else "\n剩余可安排"
        } ?: "让饮食更有计划"
        binding.homeMealSummary.text = plan?.let { day ->
            day.meals.joinToString("\n") { meal ->
                val status = if (meal.foods.none { it.eatenGrams != null }) "未记录" else "已记录 ${meal.eaten.roundToInt()} 千卡"
                "${meal.label}  ·  ${meal.foods.size} 项食物  ·  $status"
            } + "\n\n点击查看或记录实际份量 · 数据保存在本机"
        } ?: "这一天还没有饮食计划。\n点击卡片设置目标，再添加食物并记录实际份量。"
        binding.buttonRecordMeal.text = if (plan == null) "开始记录 · 先设置目标" else "记录实际食用量"
        renderTodayTasks(plan)
        renderNutritionBudget(plan)
    }

    private fun renderTodayTasks(plan: com.example.careium.core.planning.DailyMealPlan?) {
        val planDone = plan != null
        val foodDone = plan?.trackedFoods?.let { it > 0 } == true
        val water = waterIntake(selectedDate())
        val waterDone = water >= 1500
        val done = listOf(planDone, foodDone, waterDone).count { it }
        binding.homeTaskSummary.text = "$done / 3 已完成"
        binding.homeTaskPlan.text = if (planDone) "✓ 已制定今天的饮食计划" else "○ 制定今天的饮食计划"
        binding.homeTaskFood.text = if (foodDone) "✓ 已记录实际食用量" else "○ 记录实际食用量"
        binding.homeTaskWater.text = if (waterDone) "✓ 饮水已达 1500 ml" else "○ 补充饮水 · 已记录 $water / 1500 ml"
    }

    private fun waterIntake(date: String): Int {
        val context = requireContext()
        val account = LocalAccountStore.currentAccountId(context)
        return context.getSharedPreferences("water_intake", 0).getInt("$account:$date", 0)
    }

    private fun renderNutritionBudget(plan: com.example.careium.core.planning.DailyMealPlan?) {
        val visibility = if (plan == null) View.GONE else View.VISIBLE
        binding.layoutComponentsContainer.visibility = visibility
        binding.homeBudgetHeading.visibility = visibility
        binding.homeBudgetNote.visibility = visibility
        if (plan == null) return

        val profile = plan.profile
        val proteinTarget = when (profile.goal.name) {
            "LOSE" -> profile.weightKg * 1.5
            "GAIN" -> profile.weightKg * 1.6
            else -> profile.weightKg * 1.2
        }.coerceAtLeast(45.0)
        val fatTarget = (profile.weightKg * 0.8).coerceAtLeast(35.0)
        val carbsTarget = max(0.0, (plan.target - proteinTarget * 4 - fatTarget * 9) / 4)

        val foods = plan.meals.flatMap { it.foods }.filter { it.eatenGrams != null }
        val protein = MacroBudget(proteinTarget, foods.sumOf { (it.nutrition.protein ?: 0.0) * it.eatenGrams!! / it.grams }, foods.count { it.nutrition.protein != null }, foods.size)
        val carbs = MacroBudget(carbsTarget, foods.sumOf { (it.nutrition.carbs ?: 0.0) * it.eatenGrams!! / it.grams }, foods.count { it.nutrition.carbs != null }, foods.size)
        val fats = MacroBudget(fatTarget, foods.sumOf { (it.nutrition.fat ?: 0.0) * it.eatenGrams!! / it.grams }, foods.count { it.nutrition.fat != null }, foods.size)

        bindBudget(binding.layoutCalsItem, getString(R.string.calories), plan.target.toDouble(), plan.eaten, foods.size, foods.size, Color.parseColor("#E4892E"), "kcal")
        bindBudget(binding.layoutProteinsItem, getString(R.string.proteins), protein.target, protein.eaten, protein.knownItems, protein.totalItems, Color.parseColor("#3674B8"), "g")
        bindBudget(binding.layoutCarbsItem, getString(R.string.carbs), carbs.target, carbs.eaten, carbs.knownItems, carbs.totalItems, Color.parseColor("#8B62A3"), "g")
        bindBudget(binding.layoutFatsItem, getString(R.string.fats), fats.target, fats.eaten, fats.knownItems, fats.totalItems, Color.parseColor("#CF5D59"), "g")
    }

    private fun bindBudget(
        item: com.example.careium.databinding.LayoutComponentsItemBinding,
        label: String,
        target: Double,
        eaten: Double,
        knownItems: Int,
        totalItems: Int,
        color: Int,
        unit: String
    ) {
        val remaining = target - eaten
        item.textCmpntLabel.text = label
        item.progressCmpnt.max = target.roundToInt().coerceAtLeast(1)
        item.progressCmpnt.progress = eaten.roundToInt().coerceIn(0, item.progressCmpnt.max)
        item.progressCmpnt.setIndicatorColor(color)
        item.textCmpntValue.text = getString(
            if (remaining >= 0) R.string.home_budget_remaining else R.string.home_budget_exceeded,
            kotlin.math.abs(remaining).roundToInt(), unit
        )
        item.textCmpntTarget.text = if (knownItems < totalItems) {
            getString(R.string.home_budget_known, eaten.roundToInt(), target.roundToInt(), unit, knownItems, totalItems)
        } else {
            getString(R.string.home_budget_recorded, eaten.roundToInt(), target.roundToInt(), unit)
        }
    }
    override fun onResume() { super.onResume(); if (viewBinding != null) refreshDay() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("days_from", daysFrom); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { viewBinding = null; super.onDestroyView() }
}
