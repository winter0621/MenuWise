package com.example.careium.frontend.home.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.lifecycle.ViewModelProvider
import com.example.careium.R
import com.example.careium.core.planning.*
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MealPlanToolsActivity : AppCompatActivity() {
    private lateinit var model: MealPlanViewModel
    private lateinit var content: LinearLayout
    private lateinit var date: String
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun number(n: Double) = String.format(Locale.CHINA, "%.0f", n)
    private fun label(value: String, size: Float = 15f, muted: Boolean = false) = TextView(this).apply {
        text = value; textSize = size
        setTextColor(getColor(if (muted) R.color.mw_muted else R.color.mw_ink))
        setPadding(0, dp(6), 0, dp(6)); setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun action(title: String, onClick: () -> Unit) = (layoutInflater.inflate(R.layout.mw_secondary_button, null) as MaterialButton).apply {
        text = title; isAllCaps = false; cornerRadius = dp(14); minHeight = dp(52)
        setOnClickListener { onClick() }
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(14))
        setBackgroundResource(R.drawable.mw_surface_card)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        content.addView(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this).get(MealPlanViewModel::class.java)
        date = intent.getStringExtra("plan_date") ?: MealPlanStore.today()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), 0)
            setBackgroundColor(getColor(R.color.mw_canvas))
        }
        root.addView(action("‹ 返回计划") { finish() })
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(24)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (intent.getStringExtra("mode") == "shopping") renderShopping() else renderReview()
    }
    private fun renderShopping() {
        content.removeAllViews()
        content.addView(label("一周采购清单", 28f))
        content.addView(label("从 $date 起的七天 · 按已安排食物汇总", 14f, true))
        val items = model.shoppingItems(date)
        val summary = label("", 16f)
        content.addView(summary)
        fun updateSummary() {
            val current = model.shoppingItems(date)
            summary.text = "已购 ${current.count { it.purchased }} / ${current.size} 项"
        }
        updateSummary()
        val list = card()
        if (items.isEmpty()) {
            list.addView(label("还没有需要采购的食物", 18f))
            list.addView(label("先在计划中添加食物，或将已安排的一天复制到其他日期。", muted = true))
            list.addView(action("去安排食物") { finish() })
        } else {
            items.forEach { item ->
                val itemRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val box = AppCompatCheckBox(this).apply {
                    text = "${item.name}  ·  ${number(item.grams)} 克"; textSize = 16f
                    setTextColor(getColor(R.color.mw_ink)); minHeight = dp(60)
                    isChecked = item.purchased
                    paintFlags = if (isChecked) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    setOnCheckedChangeListener { _, checked ->
                        model.markPurchased(item.name, checked)
                        paintFlags = if (checked) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        updateSummary()
                    }
                }
                itemRow.addView(box, LinearLayout.LayoutParams(-1, -2))
                itemRow.addView(label(expiryLabel(item.expiresOn), 13f, true))
                val expiryActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                expiryActions.addView(action(if (item.expiresOn == null) "设置保质期" else "修改保质期") { pickExpiry(item) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
                if (item.expiresOn != null) expiryActions.addView(action("清除日期") {
                    model.setFoodExpiry(item.name, null); FoodExpiryReminder.cancel(this, item.name); renderShopping()
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
                itemRow.addView(expiryActions)
                list.addView(itemRow, LinearLayout.LayoutParams(-1, -2))
            }
            content.addView(action("分享待采购清单") {
                val remaining = model.shoppingItems(date).filterNot { it.purchased }
                if (remaining.isEmpty()) Toast.makeText(this, "所有食物都已勾选购齐", Toast.LENGTH_SHORT).show()
                else {
                    val message = "MenuWise · 从 $date 起七天的采购清单\n" + remaining.joinToString("\n") { "□ ${it.name}  ${number(it.grams)} 克" }
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }, "分享采购清单"))
                }
            })
            content.addView(action("重置已购勾选") {
                AlertDialog.Builder(this).setTitle("重置本清单的勾选？").setMessage("保留食物与计划，只清除当前清单中的已购状态。")
                    .setNegativeButton("取消", null).setPositiveButton("重置") { _, _ ->
                        items.forEach { model.markPurchased(it.name, false) }; renderShopping()
                    }.show()
            })
        }
        content.addView(label("食物重量来自计划；复合菜品按菜名汇总，采购时请按做法拆分食材。保质期为本机记录，临期通知通常在到期前一天上午发送。", 12f, true))
    }
    private fun pickExpiry(item: GroceryItem) {
        val calendar = Calendar.getInstance()
        item.expiresOn?.let { value -> runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value) }.getOrNull()?.let { calendar.time = it } }
        DatePickerDialog(this, { _, year, month, day ->
            val date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            model.setFoodExpiry(item.name, date)
            FoodExpiryReminder.cancel(this, item.name)
            FoodExpiryReminder.schedule(this, item.name, date)
            renderShopping()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun expiryLabel(expiresOn: String?): String {
        if (expiresOn == null) return "保质期：未设置"
        val days = try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            ((format.parse(expiresOn)!!.time - format.parse(MealPlanStore.today())!!.time) / (24 * 60 * 60 * 1000)).toInt()
        } catch (_: Exception) { return "保质期日期无效" }
        return when {
            days < 0 -> "保质期：$expiresOn · 已过期${-days}天，请确认是否仍可食用"
            days == 0 -> "保质期：$expiresOn · 今天到期，建议优先使用"
            days == 1 -> "保质期：$expiresOn · 明天到期"
            else -> "保质期：$expiresOn · 还剩${days}天"
        }
    }
    private fun renderReview() {
        content.removeAllViews()
        val review = model.weeklyReview(date)
        content.addView(label("饮食周回顾", 28f))
        content.addView(label("${review.firstDate} 至 ${review.lastDate}", 14f, true))
        val overview = card()
        overview.addView(label("已记录 ${review.loggedDays} 天", 22f))
        if (review.loggedDays == 0) {
            overview.addView(label("这一周还没有实际摄入记录。食物安排好后，填写吃了多少克，这里才会计入。", muted = true))
            overview.addView(action("去记录实际份量") { finish() })
        } else {
            overview.addView(label("${number(review.recordedKcal)} 千卡", 26f))
            overview.addView(label("已记录摄入总量 · 有记录日期平均 ${number(review.averageRecordedKcal)} 千卡", 14f, true))
            overview.addView(label("有记录日期的目标合计 ${review.loggedTargetKcal} 千卡", 14f, true))
            overview.addView(label("蛋白质 ${review.nutrient { it.protein }.display()}\n碳水 ${review.nutrient { it.carbs }.display()}\n脂肪 ${review.nutrient { it.fat }.display()}", 14f, true))
        }
        val plans = review.plans
        plans.forEach { plan ->
            val row = card()
            row.addView(label(plan.date, 18f))
            row.addView(label(if (plan.trackedFoods == 0) "未记录 · 目标 ${plan.target} 千卡" else "已记录 ${number(plan.eaten)} / 目标 ${plan.target} 千卡", muted = true))
            row.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = plan.target; progress = plan.eaten.toInt().coerceAtMost(plan.target)
                progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.mw_primary))
            }, LinearLayout.LayoutParams(-1, dp(10)))
            row.addView(action("查看这一天") {
                startActivity(Intent(this, MealPlanActivity::class.java).putExtra("plan_date", plan.date)); finish()
            })
        }
        content.addView(label("仅汇总截至 ${review.throughDate} 的已记录份量，与首页和报告相同。未记录的日期不按零摄入计算；只记录部分餐次时，总量也只是部分摄入。", 12f, true))
    }
    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && intent.getStringExtra("mode") != "shopping") renderReview()
    }
}
