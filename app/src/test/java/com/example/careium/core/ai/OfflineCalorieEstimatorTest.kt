package com.example.careium.core.ai

import org.junit.Assert.*
import org.junit.Test

class OfflineCalorieEstimatorTest {
    @Test fun eachFoodUsesItsOwnPortionAndIsIncludedInTheTotal() {
        val items = OfflineCalorieEstimator.estimate("米饭200克、鸡蛋100克")
        assertEquals(listOf(200.0, 100.0), items.map { it.grams })
        assertEquals(listOf(260.0, 155.0), items.map { it.calories })
        assertEquals(415.0, items.sumOf { it.calories }, 0.001)
    }
    @Test fun supportsEnglishUnitsAndWeightBeforeName() {
        val items = OfflineCalorieEstimator.estimate("0.2KG rice; 50g EGG\n苹果0.5千克，豆腐100克")
        assertEquals(listOf(200.0, 50.0, 500.0, 100.0), items.map { it.grams })
        assertTrue(items.none { it.assumedPortion })
    }
    @Test fun repeatedFoodsAndMissingPortionsAreExplicit() {
        val items = OfflineCalorieEstimator.estimate("苹果50克，苹果，米饭100克")
        assertEquals(3, items.size)
        assertEquals(100.0, items[1].grams, 0.001)
        assertTrue(items[1].assumedPortion)
        assertFalse(items[2].assumedPortion)
    }
    @Test fun neverSilentlyIgnoresUnknownFoodsOrCookingInstructions() {
        for (text in listOf("米饭200克、牛肉100克", "鸡蛋炒饭200克", "米饭100克，少油", "鸡蛋2个", "rice pudding100g", "米饭200克鸡蛋100克")) reject(text)
    }
    @Test fun invalidAndAmbiguousPortionsAreRejected() {
        for (text in listOf("米饭0克", "米饭-10克", "米饭10001克", "米饭1.2.3克", "米饭100克200克", "", "、", "鸡蛋NaN克")) reject(text)
    }
    @Test fun itemCountAndDescriptionLengthAreBounded() {
        reject(List(21) { "苹果1克" }.joinToString("、"))
        reject("米".repeat(1001))
    }
    private fun reject(text: String) {
        try { OfflineCalorieEstimator.estimate(text); fail("Should reject $text") } catch (_: IllegalArgumentException) { }
    }
}
