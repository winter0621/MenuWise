package com.example.careium.core.ai

import kotlin.math.roundToInt

object PhotoSizing {
    const val MAX_EDGE = 1536
    const val MAX_BYTES = 1_900_000
    fun sampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0)
        var sample = 1
        while (maxOf(width, height) / sample / 2 >= MAX_EDGE) sample *= 2
        return sample
    }
    fun fitted(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height))
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }
}
