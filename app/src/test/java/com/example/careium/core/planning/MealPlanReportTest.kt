package com.example.careium.core.planning

import org.junit.Assert.*
import org.junit.Test

class MealPlanReportTest {
    private fun plan(date: String, foods: List<PlannedFood>) = MealPlanCalculator.create(date,
        PlanProfile(30, 175.0, 70.0, PlanSex.MALE, PlanActivity.SEDENTARY, PlanGoal.MAINTAIN))
        .replaceFoods("lunch", foods)

    @Test fun dailyAndWeeklyTotalsUseActualPortionsAndCountEachDayOnce() {
        val day = plan("2026-10-17", listOf(PlannedFood("米饭", 100.0, 130.0, 200.0), PlannedFood("鸡蛋", 100.0, 155.0, 50.0)))
        val untracked = day.copyToDate("2026-10-16")
        val review = MealPlanReview.thisWeek(listOf(day, untracked), "2026-10-17")
        assertEquals(337.5, day.eaten, 0.001)
        assertEquals(day.eaten, review.recordedKcal, 0.001)
        assertEquals(1, review.loggedDays)
        assertEquals(day.target, review.loggedTargetKcal)
        assertEquals(day.eaten, review.averageRecordedKcal, 0.001)
    }
    @Test fun nutrientsScaleWithActualGramsAndUnknownValuesStayUnknown() {
        val day = plan("2026-10-17", listOf(
            PlannedFood("鸡蛋", 100.0, 155.0, 150.0, MacroNutrients(13.0, 1.0, 10.0)),
            PlannedFood("手动食物", 100.0, 100.0, 50.0),
            PlannedFood("尚未吃", 100.0, 100.0, nutrition = MacroNutrients(99.0, 99.0, 99.0))))
        val review = MealPlanReview.thisWeek(listOf(day), "2026-10-17")
        val protein = review.nutrient { it.protein }
        assertEquals(19.5, protein.grams, 0.001)
        assertEquals(1, protein.knownItems)
        assertEquals(2, protein.totalItems)
        assertEquals("已知 19.5 克（1/2项）", protein.display())
        assertEquals("未提供", NutrientTotal(0.0, 0, 2).display())
        assertEquals("0.0 克", NutrientTotal(0.0, 2, 2).display())
    }
    @Test fun weeksNavigateAcrossLeapDaysMonthsAndYears() {
        assertEquals("2024-02-25", MealPlanReview.reportDate("2024-03-01", 1))
        assertEquals("2025-12-28", MealPlanReview.reportDate("2026-01-01", 1))
        val week = MealPlanReview.thisWeek(emptyList(), "2026-01-01")
        assertEquals("2025-12-29", week.firstDate)
        assertEquals("2026-01-04", week.lastDate)
        assertEquals("2026-01-01", MealPlanReview.reportDate("2026-01-01", 0))
    }
    @Test fun previousWeeksIncludeSundayAndExcludeAdjacentWeeks() {
        val dates = listOf("2026-10-04", "2026-10-05", "2026-10-11", "2026-10-12")
        val plans = dates.map { plan(it, listOf(PlannedFood("米饭", 100.0, 130.0, 100.0))) }
        val review = MealPlanReview.thisWeek(plans, MealPlanReview.reportDate("2026-10-17", 1))
        assertEquals(listOf("2026-10-05", "2026-10-11"), review.plans.map { it.date })
        assertEquals(260.0, review.recordedKcal, 0.001)
    }
    @Test fun futureRecordsDoNotEnterTotalsOrDayRows() {
        val future = plan("2026-10-18", listOf(PlannedFood("米饭", 100.0, 130.0, 100.0)))
        val review = MealPlanReview.thisWeek(listOf(future), "2026-10-18", "2026-10-17")
        assertEquals(0, review.loggedDays)
        assertTrue(review.plans.isEmpty())
        assertEquals("2026-10-17", review.throughDate)
    }
    @Test fun invalidDatesAndNutrientsAreRejected() {
        for (date in listOf("2026-02-30", "2026-1-01", "broken")) {
            try { MealPlanReview.thisWeek(emptyList(), date); fail("Invalid date") } catch (_: IllegalArgumentException) { }
        }
        for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            try { MacroNutrients(value).validate(); fail("Invalid nutrient") } catch (_: IllegalArgumentException) { }
        }
    }
}
