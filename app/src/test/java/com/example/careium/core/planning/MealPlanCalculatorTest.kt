package com.example.careium.core.planning

import org.junit.Assert.*
import org.junit.Test

class MealPlanCalculatorTest {
    private val profile = PlanProfile(30, 175.0, 70.0, PlanSex.MALE, PlanActivity.SEDENTARY, PlanGoal.MAINTAIN)
    private fun plan(p: PlanProfile = profile) = MealPlanCalculator.create("2026-10-17", p)

    @Test fun maintenanceUsesActivityAdjustedExpenditure() {
        val result = plan()
        assertEquals(1649, result.bmr)
        assertEquals(1979, result.tdee)
        assertEquals(1980, result.target)
        assertNotEquals(result.bmr, result.target)
    }
    @Test fun femaleReferenceUsesCorrectOffset() {
        val result = plan(profile.copy(heightCm = 165.0, weightKg = 60.0, sex = PlanSex.FEMALE))
        assertEquals(1320, result.bmr)
        assertEquals(1580, result.target)
    }
    @Test fun activityAndGoalsChangeTarget() {
        assertTrue(plan(profile.copy(activity = PlanActivity.MODERATE)).target > plan().target)
        assertTrue(plan(profile.copy(goal = PlanGoal.LOSE)).target < plan().target)
        assertTrue(plan(profile.copy(goal = PlanGoal.GAIN)).target > plan().target)
    }
    @Test fun weightLossAdjustmentIsModerate() {
        val result = plan(profile.copy(activity = PlanActivity.HIGH, goal = PlanGoal.LOSE))
        assertTrue(result.tdee - result.target in 0..305)
    }
    @Test fun allocationAlwaysConservesDailyTotal() {
        for (target in 1200..3500) {
            val values = MealPlanCalculator.allocate(target, listOf(25, 35, 30, 10))
            assertEquals(target, values.sum())
            assertTrue(values.all { it >= 0 })
        }
    }
    @Test fun disabledSnackGetsNoRoundingRemainder() {
        val values = MealPlanCalculator.allocate(1237, listOf(30, 40, 30, 0))
        assertEquals(0, values[3])
        assertEquals(1237, values.sum())
    }
    @Test fun invalidPercentagesAreRejected() {
        for (shares in listOf(listOf(25, 35, 30, 20), listOf(-1, 51, 40, 10), listOf(100), listOf(0, 0, 0, 0))) {
            rejects { MealPlanCalculator.allocate(2000, shares) }
        }
    }
    @Test fun nonFiniteAndOutOfRangeProfilesAreRejected() {
        for (p in listOf(profile.copy(age = 17), profile.copy(heightCm = Double.NaN),
            profile.copy(weightKg = Double.POSITIVE_INFINITY), profile.copy(weightKg = 0.0))) {
            rejects { plan(p) }
        }
    }
    @Test fun underweightLossIsRejected() {
        rejects { plan(profile.copy(weightKg = 45.0, goal = PlanGoal.LOSE)) }
    }
    @Test fun customTargetRetainedAndExcessiveChangeRejected() {
        val result = MealPlanCalculator.create("2026-10-17", profile, customTarget = 2000)
        assertEquals(2000, result.target)
        assertEquals(2000, result.customTarget)
        rejects { MealPlanCalculator.create("2026-10-17", profile, customTarget = 500) }
        rejects { MealPlanCalculator.create("2026-10-17", profile, customTarget = 5000) }
    }
    @Test fun foodEditsRecalculateTotalsWithoutChangingBudget() {
        val original = plan()
        val edited = original.replaceFoods("lunch", listOf(PlannedFood("米饭", 200.0, 232.0), PlannedFood("鸡蛋", 50.0, 72.0)))
        assertEquals(304.0, edited.total, 0.001)
        assertEquals(original.target, edited.target)
        assertEquals(0.0, original.total, 0.001)
        assertEquals(0.0, edited.replaceFoods("lunch", emptyList()).total, 0.001)
    }
    @Test fun invalidFoodCannotBeSaved() {
        for (food in listOf(PlannedFood("", 100.0, 20.0), PlannedFood("米饭", 0.0, 20.0),
            PlannedFood("米饭", 100.0, -20.0), PlannedFood("米饭", 100.0, Double.NaN))) {
            rejects { plan().replaceFoods("lunch", listOf(food)) }
        }
    }
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected input rejection") } catch (_: IllegalArgumentException) { }
    }
}
