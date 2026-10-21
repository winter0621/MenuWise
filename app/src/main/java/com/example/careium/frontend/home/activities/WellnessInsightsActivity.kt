package com.example.careium.frontend.home.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.careium.R
import com.example.careium.core.planning.MealPlanStore
import com.example.careium.core.wellness.WellnessDay
import com.example.careium.core.wellness.WellnessInsightCalculator
import com.example.careium.core.wellness.WellnessSignal
import com.google.android.material.button.MaterialButton
import com.example.careium.core.database.authentication.LocalAccountStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class WellnessInsightsActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mw_canvas))
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        root.addView(button("‹ 返回") { finish() })
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(32)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        val store = MealPlanStore(this)
        val summary = WellnessInsightCalculator.summarize(lastSevenDays(store))
        content.addView(text("健康洞察", 28f, bold = true))
        content.addView(text("基于近 7 天的实际饮食、饮水和计划完成情况生成。仅作日常健康参考。", 14f, muted = true))

        val today = summary.days.last()
        val checkin = card()
        checkin.addView(text("今日打卡", 19f, bold = true))
        checkin.addView(text("已完成 ${summary.todayChecks} / 3 项", 26f, color = R.color.mw_primary, bold = true))
        checkin.addView(text("${if (today.plan != null) "已制定饮食计划" else "尚未制定饮食计划"}\n${if (today.hasMealRecord) "已记录实际食用量" else "尚未记录实际食用量"}\n饮水 ${today.waterMl} / ${WellnessInsightCalculator.WATER_GOAL_ML} ml", 14f, muted = true))
        checkin.addView(button("记录饮食与设置餐次提醒") {
            startActivity(Intent(this, MealPlanActivity::class.java).putExtra("plan_date", today.date))
        })

        val overview = card()
        overview.addView(text("近 7 天概览", 19f, bold = true))
        overview.addView(text("饮食记录 ${summary.mealDays} / 7 天\n饮水达标 ${summary.waterGoalDays} / 7 天", 22f, color = R.color.mw_ink, bold = true))
        overview.addView(text("饮水参考目标为 ${WellnessInsightCalculator.WATER_GOAL_ML} ml；记录不足不等于实际摄入不足。", 13f, muted = true))

        content.addView(text("趋势提示", 19f, bold = true, top = 22))
        summary.signals.forEach { signalCard(it) }

        content.addView(text("每日记录", 19f, bold = true, top = 22))
        val dates = card()
        summary.days.forEach { day ->
            val meal = if (day.hasMealRecord) "饮食 ${day.eaten.roundToInt()} / ${day.mealTarget} 千卡" else "未记录实际饮食"
            val water = "饮水 ${day.waterMl} ml"
            dates.addView(text("${day.date}  ·  $meal\n$water", 14f, muted = !day.hasMealRecord))
            if (day != summary.days.last()) dates.addView(divider())
        }
    }

    private fun lastSevenDays(store: MealPlanStore): List<WellnessDay> {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val water = getSharedPreferences("water_intake", Context.MODE_PRIVATE)
        val account = LocalAccountStore.currentAccountId(this)
        return (6 downTo 0).map { offset ->
            val date = format.format(Calendar.getInstance().apply { add(Calendar.DATE, -offset) }.time)
            WellnessDay(date, store.load(date), water.getInt("$account:$date", 0))
        }
    }

    private fun signalCard(signal: WellnessSignal): View {
        val card = card()
        val color = when (signal.level) {
            WellnessSignal.Level.POSITIVE -> R.color.mw_primary
            WellnessSignal.Level.NOTICE -> R.color.mw_calories
            WellnessSignal.Level.ACTION -> R.color.mw_fat
        }
        card.addView(text(signal.title, 17f, color = color, bold = true))
        card.addView(text(signal.detail, 14f, muted = true))
        return card
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        setBackgroundResource(R.drawable.mw_surface_card)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        content.addView(this)
    }

    private fun text(value: String, size: Float, muted: Boolean = false, bold: Boolean = false, color: Int? = null, top: Int = 0) = TextView(this).apply {
        text = value; textSize = size; setLineSpacing(dp(3).toFloat(), 1f)
        setTextColor(getColor(color ?: if (muted) R.color.mw_muted else R.color.mw_ink))
        if (bold) setTypeface(typeface, 1)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { if (top > 0) topMargin = dp(top) }
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun divider() = View(this).apply { setBackgroundColor(getColor(R.color.mw_primary_soft)); layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(8) } }
    private fun button(label: String, action: () -> Unit) = (layoutInflater.inflate(R.layout.mw_secondary_button, null) as MaterialButton).apply {
        text = label; isAllCaps = false; minHeight = dp(48); cornerRadius = dp(14)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        setOnClickListener { action() }
    }
}
