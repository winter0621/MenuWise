package com.example.careium.frontend.home.activities

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.careium.R
import com.example.careium.core.planning.HouseholdProfile
import com.example.careium.core.planning.HouseholdProfileStore
import com.google.android.material.button.MaterialButton
import java.util.UUID

class HouseholdProfilesActivity : AppCompatActivity() {
    private lateinit var store: HouseholdProfileStore
    private lateinit var content: LinearLayout
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = HouseholdProfileStore(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.mw_canvas)); addView(content) })
        render()
    }

    private fun render() {
        content.removeAllViews()
        content.addView(button("‹ 返回") { finish() })
        content.addView(text("家庭饮食档案", 27f, true, 14))
        content.addView(text("档案只保存在当前设备和账号中。选定成员后，可在饮食计划中带入偏好与忌口。", 14f, false, 6))
        val active = store.active()
        val profiles = store.profiles()
        if (profiles.isEmpty()) content.addView(text("还没有家庭成员档案。添加后可快速复用常见口味和忌口。", 15f, false, 20))
        profiles.forEach { profile ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundResource(R.drawable.mw_surface_card)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            }
            card.addView(text(if (profile.id == active?.id) "${profile.name} · 当前成员" else profile.name, 18f, true))
            card.addView(text("偏好：${profile.preferences.ifBlank { "未填写" }}", 14f))
            card.addView(text("忌口：${profile.excludedFoods.ifBlank { "未填写" }}", 14f))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            actions.addView(button(if (profile.id == active?.id) "当前使用" else "设为当前") { store.setActive(profile.id); render() }, weighted())
            actions.addView(button("编辑") { showEditor(profile) }, weighted())
            actions.addView(button("删除") { confirmRemove(profile) }, weighted())
            card.addView(actions)
            content.addView(card)
        }
        content.addView(button("添加家庭成员") { showEditor(null) }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
        })
    }

    private fun weighted() = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
    private fun text(value: String, size: Float = 15f, bold: Boolean = false, top: Int = 0) = TextView(this).apply {
        text = value; textSize = size; setTextColor(getColor(R.color.mw_ink)); setPadding(0, dp(top), 0, dp(4))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun button(label: String, action: () -> Unit) = MaterialButton(this).apply {
        text = label; isAllCaps = false; minHeight = dp(48); cornerRadius = dp(10); textSize = 13f
        setOnClickListener { action() }
    }

    private fun showEditor(existing: HouseholdProfile?) {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        fun field(hint: String, initial: String, multiLine: Boolean): EditText = EditText(this).apply {
            this.hint = hint; setText(initial); inputType = if (multiLine) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
            if (multiLine) minLines = 2
            fields.addView(this)
        }
        val name = field("成员名称", existing?.name.orEmpty(), false)
        val preferences = field("饮食偏好，例如少油、清淡、家常菜", existing?.preferences.orEmpty(), true)
        val excluded = field("忌口或过敏食材，用逗号分隔", existing?.excludedFoods.orEmpty(), true)
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "添加家庭成员" else "编辑家庭成员")
            .setView(ScrollView(this).apply { addView(fields) }).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    store.save(HouseholdProfile(existing?.id ?: UUID.randomUUID().toString(), name.text.toString().trim(),
                        preferences.text.toString().trim(), excluded.text.toString().trim()))
                    dialog.dismiss(); render()
                } catch (error: IllegalArgumentException) { name.error = error.message }
            }
        }
        dialog.show()
    }

    private fun confirmRemove(profile: HouseholdProfile) {
        AlertDialog.Builder(this).setTitle("删除${profile.name}？").setMessage("只删除该成员的偏好档案，不会删除任何饮食计划或记录。")
            .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> store.remove(profile.id); render() }.show()
    }
}
