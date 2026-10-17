package com.example.careium.core.ai

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object PhotoEncoder {
    fun dataUrl(image: Bitmap): String {
        var bitmap = image
        try {
            repeat(5) {
                for (quality in listOf(90, 80, 70)) {
                    val output = ByteArrayOutputStream()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "照片处理失败，请重新选择" }
                    if (output.size() <= PhotoSizing.MAX_BYTES) return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                }
                val smaller = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.8).toInt().coerceAtLeast(1), (bitmap.height * 0.8).toInt().coerceAtLeast(1), true)
                if (bitmap !== image) bitmap.recycle()
                bitmap = smaller
            }
            error("照片过大，请裁掉多余背景后重试")
        } finally { if (bitmap !== image) bitmap.recycle() }
    }
}
