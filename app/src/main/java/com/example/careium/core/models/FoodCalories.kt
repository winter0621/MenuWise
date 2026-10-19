package com.example.careium.core.models

data class FoodCalories(
    private var foodName: String,
    private var foodCalories: Int,
    val explanation: String = "",
    val matchPercent: Int = 0,
    val difference: Int = 0
) {

    fun getFoodName(): String {
        return this.foodName
    }

    fun getFoodCalories(): Int {
        return this.foodCalories
    }
}
