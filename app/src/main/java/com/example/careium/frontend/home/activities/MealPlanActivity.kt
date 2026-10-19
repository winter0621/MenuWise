package com.example.careium.frontend.home.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.lifecycle.ViewModelProvider
import com.example.careium.R
import com.example.careium.core.planning.*
import com.example.careium.core.planning.MealPlanReminder
import com.example.careium.databinding.ActivityMealPlanBinding
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MealPlanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMealPlanBinding
    private lateinit var model: MealPlanViewModel
    private var pendingImport: String? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun number(value: Double) = String.format(Locale.CHINA, "%.1f", value)
    private fun percentFields() = listOf(binding.planBreakfast, binding.planLunch, binding.planDinner, binding.planSnack)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMealPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        model = ViewModelProvider(this).get(MealPlanViewModel::class.java)
        (savedInstanceState?.getString("plan_date") ?: intent.getStringExtra("plan_date"))?.let { if (it != model.state.value?.date) model.selectDate(it) }
        pendingImport = if (savedInstanceState != null) savedInstanceState.getString("pending_import") else intent.getStringExtra("query_result")
        binding.planSex.adapter = adapter(listOf("请选择") + PlanSex.values().map { it.label })
        binding.planActivity.adapter = adapter(PlanActivity.values().map { it.label })
        binding.planGoal.adapter = adapter(PlanGoal.values().map { it.label })
        fillForm()
        showProfile(savedInstanceState?.getBoolean("profile_expanded") ?: (model.state.value?.plan == null))
        binding.planEditProfile.setOnClickListener { showProfile(binding.planForm.visibility != View.VISIBLE) }
        binding.planApplyHousehold.setOnClickListener { applyActiveHouseholdProfile() }
        binding.planCopy.setOnClickListener { copyPlan() }
        binding.planBack.setOnClickListener { finish() }
        binding.planDate.setOnClickListener {
            val parts = model.state.value!!.date.split("-").map { it.toInt() }
            DatePickerDialog(this, { _, year, month, day ->
                model.selectDate(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
                fillForm()
                showProfile(model.state.value?.plan == null)
                offerImport()
            }, parts[0], parts[1] - 1, parts[2]).show()
        }
        binding.planHistory.setOnClickListener {
            val dates = model.savedDates()
            if (dates.isEmpty()) toast("暂无已保存计划") else AlertDialog.Builder(this).setTitle("选择计划日期")
                .setItems(dates.toTypedArray()) { _, index -> model.selectDate(dates[index]); fillForm(); showProfile(false); offerImport() }.show()
        }
        binding.planCalculate.setOnClickListener {
            safely {
                require(binding.planSex.selectedItemPosition > 0) { "请选择用于代谢估算的性别" }
                val profile = PlanProfile(binding.planAge.text.toString().toIntOrNull() ?: error("请填写年龄"),
                    binding.planHeight.text.toString().toDoubleOrNull() ?: error("请填写身高"),
                    binding.planWeight.text.toString().toDoubleOrNull() ?: error("请填写体重"),
                    PlanSex.values()[binding.planSex.selectedItemPosition - 1],
                    PlanActivity.values()[binding.planActivity.selectedItemPosition], PlanGoal.values()[binding.planGoal.selectedItemPosition])
                val targetText = binding.planTarget.text.toString().trim()
                val target = if (targetText.isEmpty()) null else targetText.toIntOrNull() ?: error("请填写整数热量目标")
                model.calculate(profile, percentFields().map { it.text.toString().toIntOrNull() ?: error("请填写各餐百分比") },
                    target, binding.planPreferences.text.toString(), binding.planExcludedFoods.text.toString())
                toast("热量分配已保存，已有食物会保留")
                showProfile(false)
                binding.root.post { binding.root.smoothScrollTo(0, 0) }
                offerImport()
            }
        }
        binding.planGenerate.setOnClickListener { safely {
            require(!hasFormChanges()) { "资料或比例已修改，请先点击计算并保存热量分配" }
            model.generateSuggestions()
        } }
        binding.planService.setOnClickListener {
            startActivity(Intent(this, AiCalorieActivity::class.java).putExtra("open_service_settings", true))
        }
        binding.planWeekReview.setOnClickListener { showWeeklyReview() }
        binding.planShopping.setOnClickListener { showShoppingList() }
        binding.planAdopt.setOnClickListener {
            if (hasFormChanges()) { toast("资料或比例已修改，请先计算保存，再生成新的AI建议"); return@setOnClickListener }
            AlertDialog.Builder(this).setTitle("采用AI建议？").setMessage("将替换此日期已安排的各餐食物，热量目标保持不变。")
                .setNegativeButton("取消", null).setPositiveButton("采用并保存") { _, _ -> model.adoptSuggestion() }.show()
        }
        binding.planDismiss.setOnClickListener { model.dismissSuggestion() }
        model.state.observe(this) { state ->
            binding.planDate.text = "计划日期 ${state.date}"
            binding.planProgress.visibility = if (state.busy) View.VISIBLE else View.GONE
            setEnabled(binding.planForm, !state.busy)
            listOf(binding.planDate, binding.planHistory, binding.planCalculate, binding.planService).forEach { it.isEnabled = !state.busy }
            binding.planWeekReview.isEnabled = !state.busy
            binding.planShopping.isEnabled = !state.busy
            binding.planCopy.isEnabled = !state.busy && state.plan != null
            binding.planEditProfile.isEnabled = !state.busy
            binding.planGenerate.isEnabled = !state.busy && state.plan != null
            binding.planError.visibility = if (state.error == null) View.GONE else View.VISIBLE
            binding.planError.text = state.error
            binding.planSummary.visibility = if (state.plan == null) View.GONE else View.VISIBLE
            state.plan?.let { plan ->
                binding.planSummary.text = "每日目标 ${plan.target} 千卡\n基础代谢约 ${plan.bmr} · 每日消耗约 ${plan.tdee} 千卡\n" +
                    "已安排 ${number(plan.total)} 千卡 · 实际记录 ${number(plan.eaten)} 千卡\n${difference(plan.target - plan.eaten)} · 已保存到本机"
            }
            binding.planPreview.visibility = if (state.suggestion == null) View.GONE else View.VISIBLE
            binding.planPreviewActions.visibility = binding.planPreview.visibility
            state.suggestion?.let { binding.planPreview.text = preview(it) }
            renderMeals(state)
            binding.planNotes.text = state.plan?.notes?.joinToString("\n").orEmpty()
        }
        if (pendingImport != null && model.state.value?.plan == null) toast("查询结果已带入，请先填写资料并计算该日热量分配")
        offerImport()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("profile_expanded", binding.planForm.visibility == View.VISIBLE)
        outState.putString("plan_date", model.state.value?.date)
        outState.putString("pending_import", pendingImport)
        super.onSaveInstanceState(outState)
    }

    private fun adapter(items: List<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
    private fun fillForm() {
        val plan = model.state.value?.plan
        val profile = model.profile()
        binding.planAge.setText(profile?.age?.toString().orEmpty())
        binding.planHeight.setText(profile?.heightCm?.toString().orEmpty())
        binding.planWeight.setText(profile?.weightKg?.toString().orEmpty())
        binding.planSex.setSelection(profile?.sex?.ordinal?.plus(1) ?: 0)
        binding.planActivity.setSelection(profile?.activity?.ordinal ?: 0)
        binding.planGoal.setSelection(profile?.goal?.ordinal ?: 0)
        binding.planTarget.setText(plan?.customTarget?.toString().orEmpty())
        binding.planPreferences.setText(plan?.preferences.orEmpty())
        binding.planExcludedFoods.setText(plan?.excludedFoods.orEmpty())
        percentFields().forEachIndexed { index, field -> field.setText((plan?.meals?.get(index)?.percent ?: MealPlanCalculator.defaultPercents[index]).toString()) }
    }
    private fun applyActiveHouseholdProfile() {
        val household = HouseholdProfileStore(this).active()
        if (household == null) {
            toast("请先在设置中添加并选择家庭成员")
            return
        }
        binding.planPreferences.setText(household.preferences)
        binding.planExcludedFoods.setText(household.excludedFoods)
        toast("已带入${household.name}的饮食偏好，请点击计算并保存目标")
    }
    private fun setEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (index in 0 until view.childCount) setEnabled(view.getChildAt(index), enabled)
    }
    private fun hasFormChanges(): Boolean {
        val plan = model.state.value?.plan ?: return true
        val p = plan.profile
        return binding.planAge.text.toString().toIntOrNull() != p.age ||
            binding.planHeight.text.toString().toDoubleOrNull() != p.heightCm ||
            binding.planWeight.text.toString().toDoubleOrNull() != p.weightKg ||
            binding.planSex.selectedItemPosition != p.sex.ordinal + 1 ||
            binding.planActivity.selectedItemPosition != p.activity.ordinal ||
            binding.planGoal.selectedItemPosition != p.goal.ordinal ||
            binding.planTarget.text.toString().trim() != plan.customTarget?.toString().orEmpty() ||
            binding.planPreferences.text.toString().trim() != plan.preferences ||
            binding.planExcludedFoods.text.toString().split(',', '，', '、', ';', '；').map { it.trim() }
                .filter { it.isNotEmpty() }.distinct().joinToString("、") != plan.excludedFoods ||
            percentFields().map { it.text.toString().toIntOrNull() } != plan.meals.map { it.percent }
    }
    private fun difference(amount: Double) = if (amount >= 0) "尚余 ${number(amount)} 千卡" else "超出 ${number(abs(amount))} 千卡"
    private fun text(value: String, size: Float = 15f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(getColor(R.color.mw_ink)); setPadding(0, dp(4), 0, dp(4))
    }
    private fun button(label: String, enabled: Boolean, action: () -> Unit) = (layoutInflater.inflate(R.layout.mw_secondary_button, null) as com.google.android.material.button.MaterialButton).apply {
        cornerRadius = dp(12); textSize = 13f; isAllCaps = false; minHeight = dp(48)
        text = label; isEnabled = enabled; setOnClickListener { action() }
    }
    private fun renderMeals(state: MealPlanState) {
        binding.planMeals.removeAllViews()
        state.plan?.meals?.sortedBy { if (it.id == intent.getStringExtra("focus_meal")) 0 else 1 }?.forEach { meal ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundResource(R.drawable.mw_surface_card)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            }
            card.addView(text("${meal.label} · 目标 ${meal.target} 千卡（${meal.percent}%）", 18f))
            card.addView(text("已安排 ${number(meal.total)} 千卡 · ${difference(meal.target - meal.total)}"))
            card.addView(text(if (meal.foods.any { it.eatenGrams != null }) "实际已记录 ${number(meal.eaten)} 千卡" else "尚未记录实际份量", 13f))
            if (meal.foods.isEmpty()) card.addView(text("尚未安排食物"))
            meal.foods.forEachIndexed { index, food ->
                card.addView(text("${food.name} ${number(food.grams)}克 · 约${number(food.calories)}千卡"))
                val loggedGrams = food.eatenGrams ?: 0.0
                card.addView(text(if (food.eatenGrams == null) "尚未记录实际份量" else "实际 ${number(loggedGrams)} 克 · 约 ${number(loggedGrams * food.calories / food.grams)} 千卡", 13f))
                val eatenRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val checkbox = AppCompatCheckBox(this).apply {
                    text = "整份已吃"
                    isChecked = food.eatenGrams != null && abs(food.eatenGrams - food.grams) < 0.01
                    isEnabled = !state.busy
                    setOnCheckedChangeListener { _, checked -> safely {
                        model.setFoodEaten(meal.id, index, if (checked) food.grams else null)
                    } }
                }
                eatenRow.addView(checkbox, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
                eatenRow.addView(button("实际克数", !state.busy) { recordEatenFood(meal, index, food) })
                card.addView(eatenRow)
                val editRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                editRow.addView(button("调整计划", !state.busy) { editFood(meal, index) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
                editRow.addView(button("移除", !state.busy) { AlertDialog.Builder(this).setTitle("移除${food.name}？").setMessage("该食物的计划与实际摄入记录会一并移除。")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ -> safely { model.editFood(meal.id, index, null) } }.show() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
                card.addView(editRow)
                card.addView(button("替换建议", !state.busy) { showSubstitutionSuggestions(meal, index, food, state.plan?.excludedFoods.orEmpty()) })
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(button("添加食物", !state.busy) { editFood(meal, null) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            actions.addView(button("查询记录", !state.busy) { selectQuery(meal.id) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            card.addView(actions)
            val reminderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val reminderTitle = meal.reminderMinute?.let { "${meal.label}提醒 ${MealPlanReminder.timeLabel(it)}" } ?: "设置${meal.label}提醒"
            reminderRow.addView(button(reminderTitle, !state.busy) { setMealReminder(meal) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            if (meal.reminderMinute != null) reminderRow.addView(button("取消", !state.busy) { safely {
                MealPlanReminder.cancel(this, state.date, meal.id)
                model.setReminder(meal.id, null)
            } }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            card.addView(reminderRow)
            binding.planMeals.addView(card)
        }
    }
    private fun setMealReminder(meal: PlannedMeal) {
        val now = Calendar.getInstance()
        val current = meal.reminderMinute?.let { it / 60 to it % 60 }
            ?: (now.get(Calendar.HOUR_OF_DAY) to now.get(Calendar.MINUTE))
        TimePickerDialog(this, { _, hour, minute -> safely {
            val selected = hour * 60 + minute
            require(MealPlanReminder.schedule(this, model.state.value!!.date, meal.id, meal.label, selected)) {
                "该日期和时间已过，请选择未来时间"
            }
            model.setReminder(meal.id, selected)
            toast("${meal.label}提醒已保存")
        } }, current.first, current.second, true).show()
    }
    private fun recordEatenFood(meal: PlannedMeal, index: Int, food: PlannedFood) {
        val amount = EditText(this).apply {
            hint = "实际食用克数"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(food.eatenGrams?.toString().orEmpty())
            setSelectAllOnFocus(true)
        }
        val dialog = AlertDialog.Builder(this).setTitle("记录${food.name}实际份量")
            .setMessage("计划 ${number(food.grams)} 克 · 整份估算 ${number(food.calories)} 千卡")
            .setView(amount).setNegativeButton("取消", null).setNeutralButton("清除记录") { _, _ ->
                safely { model.setFoodEaten(meal.id, index, null) }
            }.setPositiveButton("记录", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { safely {
                val eaten = amount.text.toString().toDoubleOrNull() ?: error("请填写实际食用克数")
                require(eaten.isFinite() && eaten > 0 && eaten <= 10000) { "实际食用克数需大于0且不超过10000克" }
                model.setFoodEaten(meal.id, index, eaten)
                dialog.dismiss()
            } }
        }
        dialog.show()
    }
    private fun editFood(meal: PlannedMeal, index: Int?) {
        val food = index?.let { meal.foods[it] }
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        fun input(hint: String, initial: String, numeric: Boolean) = EditText(this).apply {
            this.hint = hint; setText(initial)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
            fields.addView(this)
        }
        val name = input("食物名称", food?.name.orEmpty(), false)
        val grams = input("重量（克）", food?.grams?.toString().orEmpty(), true)
        val kcal = input("这份食物的总千卡", food?.calories?.toString().orEmpty(), true)
        val protein = input("整份蛋白质（克，可留空）", food?.nutrition?.protein?.toString().orEmpty(), true)
        val carbs = input("整份碳水（克，可留空）", food?.nutrition?.carbs?.toString().orEmpty(), true)
        val fat = input("整份脂肪（克，可留空）", food?.nutrition?.fat?.toString().orEmpty(), true)
        fun optionalValue(field: EditText): Double? = field.text.toString().trim().let {
            if (it.isEmpty()) null else it.toDoubleOrNull() ?: error("请填写有效的营养素数值，未知时留空")
        }
        fields.addView(text("热量与营养素按整份填写。调整重量时请同时核对各项数值；未知营养素留空，不要填0。", 12f))
        val dialog = AlertDialog.Builder(this).setTitle("${meal.label} · ${if (index == null) "添加" else "调整"}食物")
            .setView(ScrollView(this).apply { addView(fields) }).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                safely {
                    val item = PlannedFood(name.text.toString().trim(), grams.text.toString().toDoubleOrNull() ?: error("请填写重量"),
                        kcal.text.toString().toDoubleOrNull() ?: error("请填写这份食物的热量"),
                        nutrition = MacroNutrients(optionalValue(protein), optionalValue(carbs), optionalValue(fat)))
                    item.validate(); model.editFood(meal.id, index, item); dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
    private fun showSubstitutionSuggestions(meal: PlannedMeal, index: Int, food: PlannedFood, excludedFoods: String) {
        val suggestions = FoodSubstitutionCatalog.suggestions(food, excludedFoods)
        if (suggestions.isEmpty()) {
            AlertDialog.Builder(this).setTitle("暂无可靠替换建议")
                .setMessage("目前仅为常见主食、蛋白质和乳制品提供离线同类替换。你仍可使用“调整计划”手动修改食物与份量。")
                .setPositiveButton("知道了", null).show()
            return
        }
        val choices = suggestions.map { suggestion ->
            "${suggestion.food.name} ${number(suggestion.food.grams)}克 · 约${number(suggestion.food.calories)}千卡\n${suggestion.reason}"
        }
        AlertDialog.Builder(this).setTitle("替换 ${food.name}")
            .setMessage("推荐热量按原食物估算，并已过滤已填写的忌口；请结合实际包装和做法核对。")
            .setItems(choices.toTypedArray()) { _, selected ->
                val choice = suggestions[selected]
                val message = buildString {
                    append("将 ${food.name} 替换为 ${choice.food.name}。")
                    if (food.eatenGrams != null) append("该食物的实际摄入记录会清除，其他食物和提醒不受影响。")
                }
                AlertDialog.Builder(this).setTitle("应用这个替换？").setMessage(message)
                    .setNegativeButton("取消", null).setPositiveButton("应用") { _, _ -> safely {
                        model.applySubstitution(meal.id, index, choice.food)
                        toast("已替换为${choice.food.name}")
                    } }.show()
            }.setNegativeButton("取消", null).show()
    }
    private fun selectQuery(mealId: String) {
        safely {
            val entries = JSONArray(getSharedPreferences("ai_calorie_history", 0).getString("entries", "[]"))
            if (entries.length() == 0) {
                AlertDialog.Builder(this).setTitle("还没有查询记录").setMessage("先查询食物热量，再将结果加入这一餐。")
                    .setNegativeButton("取消", null).setPositiveButton("去查询") { _, _ ->
                        startActivity(Intent(this, AiCalorieActivity::class.java))
                    }.show()
                return@safely
            }
            val results = (0 until entries.length()).map { entries.getJSONObject(it).getJSONObject("result") }
            val names = results.map { result ->
                val items = result.getJSONArray("items")
                (0 until items.length()).joinToString("、") { items.getJSONObject(it).getString("name") } +
                    " · 约${number(result.getJSONObject("totals").getDouble("calories_kcal"))}千卡"
            }
            AlertDialog.Builder(this).setTitle("添加整份查询结果").setItems(names.toTypedArray()) { _, index ->
                safely { model.importQuery(mealId, results[index]) }
            }.show()
        }
    }
    private fun offerImport() {
        val raw = pendingImport ?: return
        val plan = model.state.value?.plan ?: return
        AlertDialog.Builder(this).setTitle("将查询结果加入哪一餐？")
            .setItems(plan.meals.map { it.label }.toTypedArray()) { _, index ->
                safely { model.importQuery(plan.meals[index].id, JSONObject(raw)); pendingImport = null }
            }.setNegativeButton("取消") { _, _ -> pendingImport = null }.show()
    }
    private fun preview(plan: DailyMealPlan): String = buildString {
        append("AI建议预览 · 尚未替换原计划\n合计约 ${number(plan.total)} 千卡\n")
        plan.meals.filter { it.target > 0 }.forEach { meal ->
            append("\n${meal.label}：${number(meal.total)} / ${meal.target} 千卡\n")
            meal.foods.forEach { append("${it.name} ${number(it.grams)}克，约${number(it.calories)}千卡\n") }
        }
        if (plan.notes.isNotEmpty()) append("\n" + plan.notes.joinToString("\n"))
        append("\n\nAI份量和热量均为估算，采用前请核对食材与饮食限制。")
    }
    private fun showProfile(expanded: Boolean) {
        binding.planForm.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.planCalculate.visibility = binding.planForm.visibility
        binding.planEditProfile.text = if (expanded) "收起资料与目标" else "修改资料与每日目标"
    }
    private fun copyPlan() {
        if (hasFormChanges()) { toast("请先保存修改后的资料和目标"); showProfile(true); return }
        val source = model.state.value?.plan ?: return
        val parts = source.date.split("-").map { it.toInt() }
        DatePickerDialog(this, { _, year, month, day ->
            val date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            if (date == source.date) { toast("请选择不同的日期"); return@DatePickerDialog }
            val exists = model.savedDates().contains(date)
            AlertDialog.Builder(this).setTitle(if (exists) "替换 $date 的计划？" else "复制到 $date？")
                .setMessage(if (exists) "目标日期原有食物、摄入记录和提醒将被替换。复制仅包含目标和食物，不复制已吃记录及提醒。" else "复制目标和食物，实际摄入与提醒重新设置。")
                .setNegativeButton("取消", null).setPositiveButton("确认复制") { _, _ -> safely {
                    model.copyToDate(date, exists); fillForm(); showProfile(false); toast("已复制到 $date")
                } }.show()
        }, parts[0], parts[1] - 1, parts[2]).show()
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun showWeeklyReview() { startActivity(Intent(this, MealPlanToolsActivity::class.java).putExtra("mode", "review").putExtra("plan_date", model.state.value?.date)) }
    private fun showShoppingList() { startActivity(Intent(this, MealPlanToolsActivity::class.java).putExtra("mode", "shopping").putExtra("plan_date", model.state.value?.date)) }
    private fun safely(action: () -> Unit) { try { action() } catch (error: Exception) { toast(error.message ?: "操作失败，请检查输入") } }
}
