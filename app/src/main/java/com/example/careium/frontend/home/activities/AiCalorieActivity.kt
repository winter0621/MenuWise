package com.example.careium.frontend.home.activities

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import android.widget.ScrollView
import com.example.careium.core.ai.ServiceMode
import com.example.careium.core.ai.ServiceConfiguration
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.careium.R
import com.example.careium.core.ai.CalorieViewModel
import com.example.careium.core.ai.CalorieResult
import com.example.careium.databinding.ActivityAiCalorieBinding
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiCalorieActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAiCalorieBinding
    private lateinit var model: CalorieViewModel
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) model.loadImage(uri)
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = model.pendingPhotoUri
        model.pendingPhotoUri = null
        if (success && uri != null) model.loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiCalorieBinding.inflate(layoutInflater)
        setContentView(binding.root)
        model = ViewModelProvider(this).get(CalorieViewModel::class.java)
        savedInstanceState?.getString("pending_photo")?.let { model.pendingPhotoUri = Uri.parse(it) }
        binding.aiDescription.setText(model.description)
        binding.aiBack.setOnClickListener { finish() }
        binding.aiSettings.setOnClickListener { settings() }
        binding.aiServiceStatus.setOnClickListener { settings() }
        binding.aiAdjustPortions.setOnClickListener { adjustPortions() }
        renderService()
        binding.aiOpenPlan.setOnClickListener { startActivity(Intent(this, MealPlanActivity::class.java)) }
        binding.aiAddToPlan.setOnClickListener {
            model.state.value?.result?.let {
                startActivity(Intent(this, MealPlanActivity::class.java).putExtra("query_result", it.toString()))
            }
        }
        if (savedInstanceState == null && intent.getBooleanExtra("open_service_settings", false)) settings()
        binding.aiGallery.setOnClickListener {
            try { photoPicker.launch("image/*") } catch (_: Exception) {
                Toast.makeText(this, "没有可用的照片选择器", Toast.LENGTH_SHORT).show()
            }
        }
        binding.aiCamera.setOnClickListener {
            try {
                val directory = File(cacheDir, "ai_camera").apply { mkdirs() }
                val photo = File(directory, "meal.jpg")
                val uri = FileProvider.getUriForFile(this, "$packageName.ai.files", photo)
                model.pendingPhotoUri = uri
                camera.launch(uri)
            } catch (_: Exception) {
                model.pendingPhotoUri = null
                Toast.makeText(this, "无法打开相机，请尝试从相册选择", Toast.LENGTH_SHORT).show()
            }
        }
        binding.aiRemovePhoto.setOnClickListener { model.clearImage() }
        binding.aiQuery.setOnClickListener {
            model.query(binding.aiDescription.text.toString())
        }
        binding.aiClearHistory.setOnClickListener {
            AlertDialog.Builder(this).setTitle("清空查询记录？")
                .setMessage("仅删除本机查询历史，已加入饮食计划的食物会保留。")
                .setNegativeButton("取消", null).setPositiveButton("清空") { _, _ ->
                    model.clearHistory(); renderHistory()
                }.show()
        }
        model.state.observe(this) { state ->
            binding.aiProgress.visibility = if (state.busy) View.VISIBLE else View.GONE
            binding.aiQuery.isEnabled = !state.busy
            binding.aiGallery.isEnabled = !state.busy
            binding.aiCamera.isEnabled = !state.busy
            binding.aiDescription.isEnabled = !state.busy
            binding.aiSettings.isEnabled = !state.busy
            binding.aiRemovePhoto.isEnabled = !state.busy
            binding.aiClearHistory.isEnabled = !state.busy
            binding.aiQuery.text = if (state.busy) "正在处理…" else "估算这一餐的热量"
            binding.aiPhoto.visibility = if (state.image == null) View.GONE else View.VISIBLE
            binding.aiRemovePhoto.visibility = if (state.image == null) View.GONE else View.VISIBLE
            binding.aiPhoto.setImageBitmap(state.image)
            binding.aiError.visibility = if (state.error == null) View.GONE else View.VISIBLE
            binding.aiError.text = state.error
            binding.aiResult.visibility = if (state.result == null) View.GONE else View.VISIBLE
            binding.aiAddToPlan.visibility = binding.aiResult.visibility
            binding.aiAdjustPortions.visibility = binding.aiResult.visibility
            binding.aiAdjustPortions.isEnabled = !state.busy
            binding.aiServiceStatus.isEnabled = !state.busy
            state.result?.let { binding.aiResult.text = formatResult(it) }
            renderHistory()
        }
        if (savedInstanceState == null && intent.getBooleanExtra("open_camera", false)) binding.aiCamera.performClick()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_photo", model.pendingPhotoUri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        model.description = binding.aiDescription.text.toString()
        super.onStop()
    }

    private fun renderService() {
        val config = model.configuration()
        binding.aiServiceStatus.text = when (config.mode) {
            ServiceMode.COMPATIBLE -> if (config.online) "在线估算 · API 已配置 · 点此修改" else "请重新填写 API Key · 点此设置"
            ServiceMode.GATEWAY -> if (config.online) "在线估算 · 自建服务 · 点此修改" else "服务配置不完整 · 点此设置"
            ServiceMode.OFFLINE -> "当前使用本机估算 · 填写 API Key 可识别多菜餐食"
        }
    }

    private fun settings() {
        val current = model.configuration()
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding / 2, padding, 0) }
        val apiKey = EditText(this).apply {
            hint = if (current.online) "API Key 已保存，留空保留" else "粘贴 API Key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            isSaveEnabled = false
        }
        container.addView(apiKey)
        container.addView(TextView(this).apply {
            text = if (current.mode == ServiceMode.OFFLINE) "粘贴 Key 即可。首次默认连接 DeepSeek，其他接口可在高级设置中填写。"
                else "沿用已保存的接口与模型，Key 加密保存在本机。"
            setPadding(0, padding / 2, 0, 0)
        })
        val advancedToggle = android.widget.CheckBox(this).apply { text = "高级设置（可选）" }
        container.addView(advancedToggle)
        val advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        fun field(label: String, hintText: String): EditText {
            advanced.addView(TextView(this).apply { text = label })
            return EditText(this).apply { hint = hintText; inputType = InputType.TYPE_CLASS_TEXT; isSingleLine = true; advanced.addView(this) }
        }
        val apiAddress = field("API 地址", "https://example.com/v1").apply {
            setText(if (current.mode == ServiceMode.OFFLINE) ServiceConfiguration.DEEPSEEK_URL else current.endpoint)
        }
        val textModel = field("文字模型", "模型名称或接入点 ID").apply { setText(current.textModel) }
        val visionModel = field("图片模型（选填）", "支持图片的模型名称；留空关闭图片识别").apply { setText(current.visionModel) }
        advanced.addView(TextView(this).apply {
            text = "支持 OpenAI 兼容接口，地址可填基础地址或完整 /chat/completions 地址。\nDeepSeek： https://api.deepseek.com ，文字和图片模型均可填写 deepseek-flash。\n其他接口：填写对应平台的地址和模型名称。"
        })
        val gateway = android.widget.CheckBox(this).apply { text = "连接 Menuwise 自建后端"; isChecked = current.mode == ServiceMode.GATEWAY }
        advanced.addView(gateway)
        container.addView(advanced)
        advancedToggle.setOnCheckedChangeListener { _, checked -> advanced.visibility = if (checked) View.VISIBLE else View.GONE }
        val dialog = AlertDialog.Builder(this).setTitle("API 设置")
            .setView(ScrollView(this).apply { addView(container) }).setNegativeButton("取消", null)
            .setNeutralButton("使用离线") { _, _ ->
                model.configure(ServiceConfiguration(ServiceMode.OFFLINE)); renderService()
            }.setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val mode = if (gateway.isChecked) ServiceMode.GATEWAY else ServiceMode.COMPATIBLE
                    val address = if (gateway.isChecked) apiAddress.text.toString().trim() else ServiceConfiguration.completionEndpoint(apiAddress.text.toString())
                    // A saved secret must never be silently forwarded to another destination.
                    val key = apiKey.text.toString().ifBlank {
                        if (mode == current.mode && address == current.endpoint) current.credential else ""
                    }
                    val config = if (gateway.isChecked) ServiceConfiguration.gateway(address, key)
                        else ServiceConfiguration.direct(key, address, textModel.text.toString(), visionModel.text.toString())
                    model.configure(config)
                    dialog.dismiss(); renderService()
                    Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(this, if (error is IllegalArgumentException) error.message else "设置保存失败，请重新填写", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun adjustPortions() {
        val result = model.state.value?.result ?: return
        val items = result.getJSONArray("items")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt(); setPadding(padding, 0, padding, 0)
        }
        content.addView(TextView(this).apply { text = "填入各项实际克数，热量和营养素按原估算密度折算。食物种类或用油有误时，请补充文字重新识别。" })
        val fields = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            content.addView(TextView(this).apply { text = item.getString("name") + "（克）" })
            EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(number(item.getDouble("grams"))); content.addView(this)
            }
        }
        val dialog = AlertDialog.Builder(this).setTitle("校准食物份量")
            .setView(ScrollView(this).apply { addView(content) }).setNegativeButton("取消", null).setPositiveButton("重新计算", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    model.adjustPortions(fields.map { it.text.toString().toDoubleOrNull() ?: throw IllegalArgumentException("请填写每项食物的实际克数") })
                    dialog.dismiss()
                } catch (error: IllegalArgumentException) { Toast.makeText(this, error.message, Toast.LENGTH_LONG).show() }
            }
        }
        dialog.show()
    }

    private fun number(value: Double) = String.format(Locale.CHINA, "%.1f", value)

    private fun formatResult(result: JSONObject): String {
        val totals = result.getJSONObject("totals")
        return buildString {
            append("整份约 ${number(totals.getDouble("calories_kcal"))} 大卡（千卡）\n")
            if (!totals.isNull("calories_min_kcal") && !totals.isNull("calories_max_kcal"))
                append("估算范围 ${number(totals.getDouble("calories_min_kcal"))}–${number(totals.getDouble("calories_max_kcal"))} 大卡，受份量与用油影响\n")
            append("估算重量 ${number(totals.getDouble("grams"))} 克\n")
            append("蛋白质 ${CalorieResult.nutrientTotal(result) { it.protein }.display()}\n")
            append("碳水 ${CalorieResult.nutrientTotal(result) { it.carbs }.display()}\n")
            append("脂肪 ${CalorieResult.nutrientTotal(result) { it.fat }.display()}\n\n")
            val items = result.getJSONArray("items")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                append("${item.getString("name")}：${number(item.getDouble("grams"))} 克，约 ${number(item.getDouble("calories_kcal"))} 千卡\n")
            }
            val assumptions = result.getJSONArray("assumptions")
            if (assumptions.length() > 0) append("\n份量与做法假设：\n")
            for (index in 0 until assumptions.length()) append("• ${assumptions.getString(index)}\n")
            append("\n以上为参考估算。缺失的营养素不代表0；请按实际重量和营养标签核对。")
        }
    }

    private fun renderHistory() {
        val entries = model.recentQueries()
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        binding.aiHistory.text = if (entries.length() == 0) "查询后会保存在这里，方便再次查看或加入计划。" else "共 ${entries.length()} 条 · 点击查看详情"
        binding.aiClearHistory.visibility = if (entries.length() == 0) View.GONE else View.VISIBLE
        binding.aiHistoryItems.removeAllViews()
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val result = entry.optJSONObject("result") ?: continue
            val items = result.optJSONArray("items") ?: continue
            val names = (0 until items.length()).joinToString("、") { items.getJSONObject(it).getString("name") }
            val item = (layoutInflater.inflate(R.layout.mw_secondary_button, null) as com.google.android.material.button.MaterialButton).apply {
                text = "${format.format(Date(entry.optLong("time")))}  $names\n约 ${number(result.getJSONObject("totals").getDouble("calories_kcal"))} 千卡"
                isAllCaps = false; gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                textSize = 14f; maxLines = 3; cornerRadius = (12 * resources.displayMetrics.density).toInt()
                setOnClickListener {
                    AlertDialog.Builder(this@AiCalorieActivity).setTitle("查询详情")
                        .setMessage(formatResult(result)).setNegativeButton("关闭", null)
                        .setPositiveButton("加入计划") { _, _ ->
                            startActivity(Intent(this@AiCalorieActivity, MealPlanActivity::class.java).putExtra("query_result", result.toString()))
                        }.show()
                }
            }
            binding.aiHistoryItems.addView(item, LinearLayout.LayoutParams(-1, -2))
        }
    }
}
