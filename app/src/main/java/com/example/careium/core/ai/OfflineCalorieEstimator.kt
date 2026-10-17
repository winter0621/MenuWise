package com.example.careium.core.ai

/** Deliberately small dictionary: an unsupported dish must never become a partial estimate. */
object OfflineCalorieEstimator {
    data class Item(val name: String, val grams: Double, val calories: Double, val assumedPortion: Boolean)
    private data class Food(val name: String, val kcalPer100g: Double, val aliases: List<String>)
    private val foods = listOf(
        Food("熟米饭", 130.0, listOf("熟米饭", "米饭", "cooked rice", "rice")),
        Food("鸡蛋", 155.0, listOf("水煮鸡蛋", "水煮蛋", "鸡蛋", "eggs", "egg")),
        Food("鸡胸肉", 165.0, listOf("鸡胸肉", "鸡胸", "chicken breast")),
        Food("苹果", 52.0, listOf("苹果", "apple")),
        Food("香蕉", 89.0, listOf("香蕉", "banana")),
        Food("西兰花", 34.0, listOf("西兰花", "broccoli")),
        Food("干燕麦", 389.0, listOf("干燕麦", "燕麦", "oats", "oat")),
        Food("豆腐", 76.0, listOf("豆腐", "tofu")),
        Food("三文鱼", 208.0, listOf("三文鱼", "salmon"))
    )
    private val portion = Regex("([+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+))\\s*(千克|公斤|kg|克|g)", RegexOption.IGNORE_CASE)

    fun estimate(text: String): List<Item> {
        require(text.isNotBlank() && text.length <= 1000) { "请输入1–1000个字符的食物描述" }
        val parts = text.split(Regex("[,，、;；\\n+＋]+")).map { it.trim() }.filter { it.isNotEmpty() }
        require(parts.size in 1..20) { "每次最多查询20项食物，请用逗号或顿号分隔" }
        return parts.map { part ->
            val amounts = portion.findAll(part).toList()
            require(amounts.size <= 1) { "“$part”包含多个份量，请将每项食物和重量分开，例如米饭200克、鸡蛋100克" }
            val amount = amounts.singleOrNull()
            val name = if (amount == null) part else part.removeRange(amount.range).trim()
            val food = foods.firstOrNull { food -> food.aliases.any { it.equals(name, ignoreCase = true) } }
                ?: throw IllegalArgumentException("离线库无法完整识别“$part”。支持米饭、鸡蛋、鸡胸肉、苹果、香蕉、西兰花、燕麦、豆腐和三文鱼；请逐项填写食物名称与克数，复合菜品请使用在线查询。")
            val value = amount?.groupValues?.get(1)?.toDoubleOrNull() ?: if (amount == null) 100.0 else Double.NaN
            val unit = amount?.groupValues?.get(2).orEmpty()
            val grams = value * if (unit.equals("kg", true) || unit == "千克" || unit == "公斤") 1000 else 1
            require(grams.isFinite() && grams > 0 && grams <= 10000) { "“${food.name}”的重量需大于0且不超过10000克" }
            val calories = food.kcalPer100g * grams / 100
            require(calories <= 20000) { "“${food.name}”的份量过大，请分次查询" }
            Item(food.name, grams, calories, amount == null)
        }
    }
}
