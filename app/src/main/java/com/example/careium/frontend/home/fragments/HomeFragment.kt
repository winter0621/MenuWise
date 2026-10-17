package com.example.careium.frontend.home.fragments

import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

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
    }
    override fun onResume() { super.onResume(); if (viewBinding != null) refreshDay() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("days_from", daysFrom); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { viewBinding = null; super.onDestroyView() }
}
