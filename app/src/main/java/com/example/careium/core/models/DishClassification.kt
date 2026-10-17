package com.example.careium.core.models

import android.content.Context
import com.example.careium.R
import com.example.careium.ml.ClassificationModel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class DishClassification() : DeepLearning() {
    var context: Context? = null
    constructor(context: Context) : this() { this.context = context }
    fun classifyDish(inputFeature: TensorBuffer): String {
        val model = ClassificationModel.newInstance(context!!)
        val outputs = try { model.process(inputFeature).outputFeature0AsTensorBuffer.floatArray } finally { model.close() }
        val classes = context!!.resources.openRawResource(R.raw.c1_classes).bufferedReader().use { it.readLines() }
        require(outputs.isNotEmpty() && outputs.size <= classes.size && outputs.all { it.isFinite() }) { "本地图像模型输出异常" }
        val index = outputs.indices.maxByOrNull { outputs[it] } ?: error("未能识别食物")
        return classes[index].replace('_', ' ')
    }
}
