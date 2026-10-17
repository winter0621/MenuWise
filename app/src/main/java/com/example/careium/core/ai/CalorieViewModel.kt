package com.example.careium.core.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

data class CalorieState(
    val busy: Boolean = false,
    val image: Bitmap? = null,
    val result: JSONObject? = null,
    val error: String? = null
)

class CalorieViewModel(application: Application) : AndroidViewModel(application) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mutableState = MutableLiveData(CalorieState())
    val state: LiveData<CalorieState> = mutableState
    private val settings = AiServiceSettings(application)
    private val history = application.getSharedPreferences("ai_calorie_history", 0)
    var pendingPhotoUri: Uri? = null
    var description: String = ""

    fun configuration(): ServiceConfiguration = settings.current()
    fun configure(config: ServiceConfiguration) { settings.save(config) }
    fun adjustPortions(grams: List<Double>) {
        val previous = mutableState.value ?: return
        if (previous.busy) return
        val result = previous.result ?: return
        val adjusted = CaloriePortions.adjust(result, grams)
        remember(adjusted)
        mutableState.value = previous.copy(result = adjusted, error = null)
    }

    fun clearImage() {
        if (mutableState.value?.busy != true) mutableState.value = CalorieState()
    }

    fun loadImage(uri: Uri) {
        val previous = mutableState.value ?: CalorieState()
        if (previous.busy) return
        mutableState.value = previous.copy(busy = true, error = null, result = null)
        executor.execute {
            try {
                val resolver = getApplication<Application>().contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取这张照片，请重新选择" }
                val sample = PhotoSizing.sampleSize(bounds.outWidth, bounds.outHeight)
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                    ?: throw IllegalArgumentException("无法读取这张照片，请重新选择")
                val orientation = resolver.openInputStream(uri).use { input ->
                    if (input == null) ExifInterface.ORIENTATION_NORMAL else try {
                        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
                }
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
                }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                val size = PhotoSizing.fitted(rotated.width, rotated.height)
                val bitmap = Bitmap.createScaledBitmap(rotated, size.first, size.second, true)
                if (rotated !== bitmap && rotated !== decoded) rotated.recycle()
                if (decoded !== bitmap) decoded.recycle()
                mutableState.postValue(CalorieState(image = bitmap))
            } catch (_: OutOfMemoryError) {
                mutableState.postValue(previous.copy(busy = false, error = "照片过大，请选择较小的照片"))
            } catch (_: Exception) {
                mutableState.postValue(previous.copy(busy = false, error = "无法读取照片，请重新选择"))
            }
        }
    }

    fun query(text: String) {
        val previous = mutableState.value ?: CalorieState()
        if (previous.busy) return
        if (text.isBlank() && previous.image == null) {
            mutableState.value = previous.copy(error = "请输入食物名称或选择照片")
            return
        }
        if (text.length > 1000) {
            mutableState.value = previous.copy(error = "食物描述最多1000个字符")
            return
        }
        val config = configuration()
        if (config.mode != ServiceMode.OFFLINE && !config.online) {
            mutableState.value = previous.copy(error = "服务凭据不可用，请在 API 设置中重新填写，或选择仅离线")
            return
        }
        val offline = config.mode == ServiceMode.OFFLINE
        mutableState.value = previous.copy(busy = true, result = null, error = null)
        executor.execute {
            try {
                if (offline) {
                    val localResult = localEstimate(text.trim(), previous.image)
                    remember(localResult)
                    mutableState.postValue(previous.copy(busy = false, result = localResult, error = null))
                    return@execute
                }
                val imageData = previous.image?.let { PhotoEncoder.dataUrl(it) }
                val result = CalorieClient().query(config, text.trim(), imageData)
                remember(result)
                mutableState.postValue(previous.copy(busy = false, result = result, error = null))
            } catch (error: Exception) {
                val message = when (error) {
                    is SocketTimeoutException -> "查询超时，请稍后重试"
                    is java.io.IOException -> "无法连接热量查询服务，请检查网络和服务地址"
                    else -> error.message ?: "查询失败，请稍后重试"
                }
                mutableState.postValue(previous.copy(busy = false, result = null, error = message))
            }
        }
    }

    private fun localEstimate(text: String, image: Bitmap?): JSONObject {
        if (text.isNotBlank()) {
            val foods = OfflineCalorieEstimator.estimate(text)
            val items = JSONArray()
            foods.forEach { food ->
                items.put(JSONObject().put("name", food.name).put("grams", food.grams)
                    .put("calories_kcal", food.calories).put("protein_g", JSONObject.NULL)
                    .put("carbs_g", JSONObject.NULL).put("fat_g", JSONObject.NULL))
            }
            val totals = JSONObject().put("grams", foods.sumOf { it.grams })
                .put("calories_kcal", foods.sumOf { it.calories }).put("protein_g", JSONObject.NULL)
                .put("carbs_g", JSONObject.NULL).put("fat_g", JSONObject.NULL)
            val notes = JSONArray().put("离线食物库参考热量，按可食部分估算；未提供宏量营养素。米饭按熟重、燕麦按干重，未另计烹调用油。")
            foods.filter { it.assumedPortion }.forEach { notes.put("${it.name}未填写重量，暂按100克估算，请核对。") }
            if (image != null) notes.put("本次按文字逐项查询，照片未参与估算；如需本地图像估算，请清空文字后查询。")
            return JSONObject().put("items", items).put("totals", totals).put("assumptions", notes)
                .put("estimated", true).put("source", "offline_database")
        }
        require(image != null) { "请输入食物名称或选择照片" }
        val app = getApplication<Application>()
        val classifier = com.example.careium.core.models.DishClassification(app)
        val name = classifier.classifyDish(classifier.loadImageBuffer(image, 250, 250))
        val model = com.example.careium.core.models.DishNutritionRegression(app)
        val nutrition = model.predictNutritionModel(model.loadImageBuffer(image, 224, 224))
        require(nutrition.size >= 5 && nutrition.all { it.isFinite() && it >= 0f }) { "本地模型未返回有效结果，请重新选择照片" }
        val grams = nutrition[1].coerceAtLeast(1f).toDouble()
        val item = JSONObject().put("name", name).put("grams", grams)
            .put("calories_kcal", nutrition[0].toDouble()).put("protein_g", nutrition[4].toDouble())
            .put("carbs_g", nutrition[3].toDouble()).put("fat_g", nutrition[2].toDouble())
        val totals = JSONObject(item.toString()).apply { remove("name") }
        return JSONObject().put("items", JSONArray().put(item)).put("totals", totals)
            .put("estimated", true).put("source", "offline_model")
            .put("assumptions", JSONArray().put("本地单菜模型参考估算，无法可靠拆分多道菜；可配置在线视觉模型识别整餐，再按实际克数校准。"))
    }

    private fun remember(result: JSONObject) {
        val old = recentQueries()
        val entries = JSONArray().put(JSONObject().put("time", System.currentTimeMillis()).put("result", result))
        for (index in 0 until minOf(old.length(), 49)) entries.put(old.get(index))
        history.edit().putString("entries", entries.toString()).apply()
    }

    fun recentQueries(): JSONArray = try {
        JSONArray(history.getString("entries", "[]"))
    } catch (_: Exception) { JSONArray() }

    fun clearHistory() { history.edit().clear().apply() }

    override fun onCleared() {
        executor.shutdownNow()
        super.onCleared()
    }
}
