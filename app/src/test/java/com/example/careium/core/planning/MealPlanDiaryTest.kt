package com.example.careium.core.planning

import org.junit.Assert.*
import org.junit.Test

class MealPlanDiaryTest {
    private fun plan() = MealPlanCalculator.create("2026-10-17", PlanProfile(30, 175.0, 70.0,
        PlanSex.MALE, PlanActivity.SEDENTARY, PlanGoal.MAINTAIN))

    @Test fun actualPortionsCanExceedThePlanWithoutChangingItsBudget() {
        val original = plan()
        val recorded = original.replaceFoods("lunch", listOf(PlannedFood("米饭", 100.0, 120.0, 150.0)))
        assertEquals(120.0, recorded.total, 0.001)
        assertEquals(180.0, recorded.eaten, 0.001)
        assertEquals(original.target, recorded.target)
        assertEquals(1, recorded.trackedFoods)
    }
    @Test fun copyRetainsFoodButResetsConsumptionAndReminders() {
        val original = plan().let { p -> p.copy(meals = p.meals.map {
            it.copy(foods = listOf(PlannedFood("米饭", 100.0, 120.0, 80.0)), reminderMinute = 480)
        }) }
        val copied = original.copyToDate("2026-10-18")
        assertEquals("2026-10-18", copied.date)
        assertEquals(original.total, copied.total, 0.001)
        assertEquals(0.0, copied.eaten, 0.001)
        assertTrue(copied.meals.all { it.reminderMinute == null })
        assertEquals(4, original.trackedFoods)
        assertEquals(original.profile, copied.profile)
    }
    @Test fun weeklySummaryIgnoresUnrecordedAndFutureDays() {
        val recorded = plan().replaceFoods("lunch", listOf(PlannedFood("米饭", 100.0, 120.0, 100.0)))
        val unrecorded = recorded.copyToDate("2026-10-16")
        val future = recorded.copy(date = "2026-10-18")
        val review = MealPlanReview.thisWeek(listOf(recorded, unrecorded, future), "2026-10-17")
        assertEquals(1, review.loggedDays)
        assertEquals(120.0, review.averageRecordedKcal, 0.001)
    }
    @Test fun copyingToSameOrInvalidDateIsRejected() {
        for (date in listOf("2026-10-17", "2026-02-30", "not-a-date")) {
            try { plan().copyToDate(date); fail("Expected invalid date rejection") } catch (_: IllegalArgumentException) { }
        }
    }
}
