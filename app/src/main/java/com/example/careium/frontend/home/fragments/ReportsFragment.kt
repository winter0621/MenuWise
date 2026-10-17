package com.example.careium.frontend.home.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.example.careium.R
import com.example.careium.core.planning.*
import com.example.careium.databinding.FragmentReportsBinding
import com.example.careium.databinding.LayoutComponentsItemBinding
import com.example.careium.frontend.home.activities.MealPlanActivity
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class ReportsFragment : Fragment(R.layout.fragment_reports) {
    private var viewBinding: FragmentReportsBinding? = null
    private val binding get() = viewBinding!!
    private var weeksAgo = 0
    companion object { fun newInstance() = ReportsFragment() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding = FragmentReportsBinding.bind(view)
        weeksAgo = savedInstanceState?.getInt("weeks_ago")?.coerceAtLeast(0) ?: 0
        binding.imageButtonPrevDate.setOnClickListener { weeksAgo++; render() }
        binding.imageButtonNextDate.setOnClickListener { weeksAgo = (weeksAgo - 1).coerceAtLeast(0); render() }
        render()
    }

    private fun render() {
        val today = MealPlanStore.today()
        val date = MealPlanReview.reportDate(today, weeksAgo)
        val review = MealPlanStore(requireContext()).weeklyReview(date, today)
        binding.waitContainer.visibility = View.GONE
        binding.textCalendarDate.text = "${review.firstDate} 至 ${review.lastDate}"
        binding.imageButtonNextDate.visibility = if (weeksAgo == 0) View.INVISIBLE else View.VISIBLE
        binding.layoutCalsItem.apply {
            textCmpntLabel.text = "已记录热量"
            textCmpntValue.text = if (review.loggedDays == 0) "未记录" else "${review.recordedKcal.roundToInt()} 千卡"
            textCmpntTarget.text = if (review.loggedDays == 0) "" else " / 目标 ${review.loggedTargetKcal}"
            progressCmpnt.max = review.loggedTargetKcal.coerceAtLeast(1)
            progressCmpnt.progress = review.recordedKcal.roundToInt().coerceIn(0, progressCmpnt.max)
        }
        nutrient(binding.layoutProteinsItem, "蛋白质", review.nutrient { it.protein })
        nutrient(binding.layoutCarbsItem, "碳水", review.nutrient { it.carbs })
        nutrient(binding.layoutFatsItem, "脂肪", review.nutrient { it.fat })
        binding.reportTitle.text = "已记录 ${review.loggedDays} 天"
        binding.reportWeightTitle.text = "有记录日期的日均摄入"
        binding.reportWeight.text = if (review.loggedDays == 0) "—" else "${review.averageRecordedKcal.roundToInt()} 千卡"
        binding.reportMessage.text = if (review.loggedDays == 0) {
            "这一周还没有实际摄入记录。请在计划中填写实际食用克数，或勾选按计划吃完。"
        } else {
            val difference = review.recordedKcal - review.loggedTargetKcal
            "截至 ${review.throughDate}，已记录摄入比有记录日期的目标合计" +
                if (difference > 0) "高 ${difference.roundToInt()} 千卡。" else "少 ${(-difference).roundToInt()} 千卡。"
        }
        binding.reportNextDaysMessage.text = "与首页、计划周回顾使用相同的实际份量。未记录日期不按零摄入计算；只记录部分餐次时，统计也只是部分摄入。营养素缺失显示未提供，部分已知会标明覆盖项数。"
        binding.reportDayItems.removeAllViews()
        review.plans.forEach { plan ->
            val summary = if (plan.trackedFoods == 0) "未记录" else "已记录 ${plan.eaten.roundToInt()} 千卡"
            val button = layoutInflater.inflate(R.layout.mw_secondary_button, binding.reportDayItems, false) as MaterialButton
            button.text = "${plan.date} · $summary\n目标 ${plan.target} 千卡 · 查看或补记"
            button.isAllCaps = false
            button.setOnClickListener { openPlan(plan.date) }
            binding.reportDayItems.addView(button, LinearLayout.LayoutParams(-1, -2))
        }
        binding.reportOpenPlan.setOnClickListener { openPlan(date) }
    }

    private fun nutrient(row: LayoutComponentsItemBinding, label: String, total: NutrientTotal) {
        row.textCmpntLabel.text = label
        row.textCmpntValue.text = total.display()
        row.textCmpntTarget.text = ""
        row.progressCmpnt.visibility = View.GONE
    }
    private fun openPlan(date: String) {
        startActivity(Intent(requireContext(), MealPlanActivity::class.java).putExtra("plan_date", date))
    }
    override fun onResume() { super.onResume(); if (viewBinding != null) render() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("weeks_ago", weeksAgo); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { viewBinding = null; super.onDestroyView() }
}
