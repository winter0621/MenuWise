package com.example.careium.core.wellness

import com.example.careium.core.planning.DailyMealPlan
import com.example.careium.core.planning.PlanGoal
import kotlin.math.abs
import kotlin.math.roundToInt

data class WellnessDay(val date: String, val plan: DailyMealPlan?, val waterMl: Int) {
    val hasMealRecord get() = (plan?.trackedFoods ?: 0) > 0
    val mealTarget get() = plan?.target ?: 0
    val eaten get() = plan?.eaten ?: 0.0
    val knownProteinFoods get() = plan?.meals?.flatMap { it.foods }
        ?.filter { it.eatenGrams != null && it.nutrition.protein != null }.orEmpty()
    val proteinGrams get() = knownProteinFoods.sumOf { it.nutrition.protein!! * it.eatenGrams!! / it.grams }
}

data class WellnessSignal(val title: String, val detail: String, val level: Level) {
    enum class Level { POSITIVE, NOTICE, ACTION }
}

data class WellnessSummary(
    val days: List<WellnessDay>,
    val mealDays: Int,
    val waterGoalDays: Int,
    val todayChecks: Int,
    val signals: List<WellnessSignal>
)

object WellnessInsightCalculator {
    const val WATER_GOAL_ML = 1500

    fun summarize(days: List<WellnessDay>): WellnessSummary {
        val mealDays = days.count { it.hasMealRecord }
        val waterGoalDays = days.count { it.waterMl >= WATER_GOAL_ML }
        val today = days.lastOrNull()
        val checks = listOf(today?.plan != null, today?.hasMealRecord == true, (today?.waterMl ?: 0) >= WATER_GOAL_ML).count { it }
        val signals = mutableListOf<WellnessSignal>()

        if (mealDays == 0) {
            signals += WellnessSignal("开始建立饮食画像", "近七天还没有实际食用记录。记录一餐实际份量后，系统才能生成个性化趋势。", WellnessSignal.Level.NOTICE)
        } else {
            val logged = days.filter { it.hasMealRecord }
            val energyRatio = logged.map { it.eaten / it.mealTarget.coerceAtLeast(1) }.average()
            when {
                logged.size >= 3 && energyRatio > 1.15 -> signals += WellnessSignal("能量摄入偏高", "近七天有记录日期平均比目标高 ${(energyRatio * 100 - 100).roundToInt()}%，下一餐可优先选择清淡搭配并关注分量。", WellnessSignal.Level.ACTION)
                logged.size >= 3 && energyRatio < 0.75 -> signals += WellnessSignal("能量摄入偏低", "近七天有记录日期平均仅为目标的 ${(energyRatio * 100).roundToInt()}%，请确认是否漏记；若持续偏低，建议咨询专业人士。", WellnessSignal.Level.ACTION)
                logged.size >= 3 -> signals += WellnessSignal("能量节奏较稳定", "近七天已记录日期的平均摄入接近每日目标，继续保持记录习惯。", WellnessSignal.Level.POSITIVE)
            }

            val profile = logged.lastOrNull()?.plan?.profile
            val proteinTarget = profile?.let {
                val factor = when (it.goal) { PlanGoal.LOSE -> 1.5; PlanGoal.GAIN -> 1.6; else -> 1.2 }
                (it.weightKg * factor).coerceAtLeast(45.0)
            }
            val proteinDays = logged.filter { it.knownProteinFoods.isNotEmpty() }
            if (proteinTarget != null && proteinDays.size >= 3) {
                val proteinAverage = proteinDays.map { it.proteinGrams }.average()
                if (proteinAverage < proteinTarget * 0.75) signals += WellnessSignal("蛋白质可能不足", "已知营养数据中，日均蛋白质约 ${proteinAverage.roundToInt()} 克，低于参考目标 ${proteinTarget.roundToInt()} 克。可考虑在正餐补充蛋、奶、豆制品或瘦肉。", WellnessSignal.Level.NOTICE)
            }
        }

        val missingMealStreak = days.reversed().takeWhile { !it.hasMealRecord }.size
        if (missingMealStreak >= 2) signals += WellnessSignal("记录出现中断", "已连续 $missingMealStreak 天没有记录实际食用量。即使只记录一餐，也能让建议更贴近你。", WellnessSignal.Level.NOTICE)

        val waterLow = days.count { it.waterMl < WATER_GOAL_ML }
        when {
            waterLow >= 5 -> signals += WellnessSignal("饮水记录不足", "近七天有 $waterLow 天未达到 ${WATER_GOAL_ML}ml 参考目标。可使用首页快捷饮水记录分次补充。", WellnessSignal.Level.NOTICE)
            waterGoalDays >= 5 -> signals += WellnessSignal("饮水习惯良好", "近七天有 $waterGoalDays 天达到 ${WATER_GOAL_ML}ml 参考目标。", WellnessSignal.Level.POSITIVE)
        }
        signals += WellnessSignal("糖与钠暂未统计", "当前食物数据没有统一的糖和钠字段，因此不会给出高糖或高盐结论；后续补充营养标签后可继续扩展。", WellnessSignal.Level.NOTICE)
        return WellnessSummary(days, mealDays, waterGoalDays, checks, signals)
    }
}
