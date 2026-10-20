package com.example.careium.frontend.home.fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.careium.R
import com.example.careium.core.adapters.RecommendationAdapter
import com.example.careium.core.database.authentication.InternetConnection
import com.example.careium.core.database.realtime.UserData
import com.example.careium.core.models.FoodCalories
import com.example.careium.core.models.User
import com.example.careium.core.planning.DailyMealPlan
import com.example.careium.core.planning.MealPlanStore
import com.example.careium.core.planning.PlannedFood
import com.example.careium.databinding.FragmentRecommendationBinding
import com.example.careium.frontend.factory.FutureGoal
import com.example.careium.frontend.factory.Gender
import com.example.careium.frontend.factory.UserDataViewModel
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt


class RecommendationFragment : Fragment(R.layout.fragment_recommendation) {
    private lateinit var binding: FragmentRecommendationBinding
    private lateinit var userDataViewModel: UserDataViewModel
    private lateinit var foodAdapter: RecommendationAdapter
    private lateinit var foodArrayList: ArrayList<FoodCalories>

    companion object {
        fun newInstance() = RecommendationFragment().apply {}
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentRecommendationBinding.bind(view)
        binding.waitContainer.visibility = View.VISIBLE

        foodArrayList = ArrayList()
        userDataViewModel = ViewModelProviders.of(this).get(UserDataViewModel::class.java)
        observeUserDataCallBackChange()

        if (InternetConnection.isConnected(this.requireContext()) && !requireContext().getSharedPreferences("menuwise_local", 0).getBoolean("offline_mode", false)) {
            val userData = UserData()
            userData.getUserData(userDataViewModel)
        } else {
            val prefs = requireContext().getSharedPreferences("menuwise_local", 0)
            val localUser = User().apply {
                name = prefs.getString("name", "本地用户") ?: "本地用户"
                height = prefs.getFloat("height", 0f)
                weight = prefs.getFloat("weight", 0f)
                age = prefs.getInt("age", 0)
                gender = runCatching { Gender.valueOf(prefs.getString("gender", "Male")!!) }.getOrDefault(Gender.Male)
            }
            renderRecommendations(localUser)
        }
    }


    private fun observeUserDataCallBackChange() {
        userDataViewModel.mutableUserData.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                renderRecommendations(user)
            }
        }
    }

    private fun renderRecommendations(user: User) {
        if (!isAdded || view == null) return
        try {
            val plan = MealPlanStore(requireContext()).load(MealPlanStore.today())
            val target = recommendationTarget(user, plan)
            val goal = user.futureGoal ?: localGoal() ?: planGoal(plan)
            binding.recommendationContext.text = recommendationContext(target, plan, goal)
            foodArrayList.clear()
            foodArrayList.addAll(readFoodCaloriesFile()
                .map { explain(it, target, goal, plan) }
                .sortedWith(compareByDescending<FoodCalories> { it.matchPercent }.thenBy { abs(it.difference) })
                .take(12))
            binding.recommendRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            foodAdapter = RecommendationAdapter(foodArrayList, requireContext()) { food -> showRecommendationActions(food) }
            binding.recommendRecyclerView.adapter = foodAdapter
        } catch (error: Exception) {
            Log.e("Recommendation", "Unable to render recommendations", error)
            foodArrayList.clear()
            binding.recommendationContext.text = "暂时无法读取完整资料，已为你保留饮食计划和热量查询功能。"
            Toast.makeText(requireContext(), "推荐暂不可用，请稍后重试", Toast.LENGTH_LONG).show()
        } finally {
            binding.waitContainer.visibility = View.GONE
        }
    }

    private fun recommendationTarget(user: User, plan: DailyMealPlan?): Int {
        if (plan != null) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val mealId = when {
                hour < 10 -> "breakfast"
                hour < 15 -> "lunch"
                hour < 20 -> "dinner"
                else -> "snack"
            }
            plan.meals.firstOrNull { it.id == mealId }?.let {
                return (it.target - it.eaten).roundToInt().coerceAtLeast(120)
            }
            val remainingMeals = plan.meals.count { it.eaten <= 0 }.coerceAtLeast(1)
            return ((plan.target - plan.eaten) / remainingMeals).roundToInt().coerceAtLeast(120)
        }
        return if (user.height > 0 && user.weight > 0 && user.age > 0) calBMR(user) else 600
    }

    private fun explain(food: FoodCalories, target: Int, goal: FutureGoal?, plan: DailyMealPlan?): FoodCalories {
        val difference = food.getFoodCalories() - target
        val match = (100 - abs(difference) * 100 / target.coerceAtLeast(1)).coerceIn(0, 100)
        val calorieReason = when {
            abs(difference) <= 50 -> getString(R.string.recommendation_reason_close, target)
            difference < 0 -> getString(R.string.recommendation_reason_room, -difference)
            else -> getString(R.string.recommendation_reason_over, difference)
        }
        val goalReason = when (goal) {
            FutureGoal.LoseWeight -> if (difference <= 50) getString(R.string.recommendation_reason_lose) else getString(R.string.recommendation_reason_portion)
            FutureGoal.GainWeight -> if (difference >= -80) getString(R.string.recommendation_reason_gain) else getString(R.string.recommendation_reason_addition)
            FutureGoal.PatientTreatment -> getString(R.string.recommendation_reason_patient)
            else -> getString(R.string.recommendation_reason_balance)
        }
        val sourceReason = if (plan != null) getString(R.string.recommendation_reason_plan) else getString(R.string.recommendation_reason_profile)
        return FoodCalories(food.getFoodName(), food.getFoodCalories(), "$calorieReason $goalReason $sourceReason", match, difference)
    }

    private fun recommendationContext(target: Int, plan: DailyMealPlan?, goal: FutureGoal?): String {
        val goalLabel = when (goal) {
            FutureGoal.LoseWeight -> getString(R.string.recommendation_goal_lose)
            FutureGoal.GainWeight -> getString(R.string.recommendation_goal_gain)
            FutureGoal.PatientTreatment -> getString(R.string.recommendation_goal_patient)
            else -> getString(R.string.recommendation_goal_balance)
        }
        return if (plan != null) getString(R.string.recommendation_context_plan, target, goalLabel, plan.eaten.roundToInt(), plan.target)
        else getString(R.string.recommendation_context_profile, target, goalLabel)
    }

    private fun localGoal(): FutureGoal? = requireContext().getSharedPreferences("menuwise_local", 0)
        .getString("goal", null)?.let { runCatching { FutureGoal.valueOf(it) }.getOrNull() }

    private fun planGoal(plan: DailyMealPlan?): FutureGoal? = when (plan?.profile?.goal?.name) {
        "LOSE" -> FutureGoal.LoseWeight
        "GAIN" -> FutureGoal.GainWeight
        "MAINTAIN" -> FutureGoal.FitnessTracker
        else -> null
    }

    private fun calBMR(user: User): Int {
        var bmr = (10 * user.weight) + (6.25 * user.height) - (5 * user.age)
        if (user.gender == Gender.Male) bmr += 5
        else bmr -= 161
        bmr /= 3   // divided by 3, because we have 3 meals per day

        return bmr.toInt()
    }

    private fun showRecommendationActions(food: FoodCalories) {
        val store = MealPlanStore(requireContext())
        val plan = store.load(MealPlanStore.today())
        val detail = buildString {
            append("约 ${food.getFoodCalories()} 千卡 / 份\n\n")
            append(food.explanation)
            append("\n\n热量为参考估算；加入计划后可再按实际食用量记录。")
        }
        if (plan == null) {
            AlertDialog.Builder(requireContext()).setTitle(food.getFoodName()).setMessage(detail)
                .setNegativeButton("关闭", null).setPositiveButton("去创建计划") { _, _ ->
                    startActivity(android.content.Intent(requireContext(), com.example.careium.frontend.home.activities.MealPlanActivity::class.java))
                }.show()
            return
        }
        AlertDialog.Builder(requireContext()).setTitle(food.getFoodName()).setMessage(detail)
            .setItems(plan.meals.map { "加入${it.label}" }.toTypedArray()) { _, index ->
                try {
                    store.addFood(plan.date, plan.meals[index].id, PlannedFood(food.getFoodName(), 300.0, food.getFoodCalories().toDouble()))
                    Toast.makeText(requireContext(), "已加入${plan.meals[index].label}计划", Toast.LENGTH_LONG).show()
                } catch (error: IllegalArgumentException) {
                    Toast.makeText(requireContext(), error.message ?: "加入计划失败", Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton("关闭", null).show()
    }

    private fun readFoodCaloriesFile(): ArrayList<FoodCalories> {
        val foodCalories: ArrayList<FoodCalories> = ArrayList()
        val textFile: InputStream = resources.openRawResource(R.raw.food_recommendation)
        BufferedReader(InputStreamReader(textFile)).useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.forEach { record ->
                val parts = record.split(",", limit = 2)
                val calories = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (parts.firstOrNull()?.isNotBlank() == true && calories != null) foodCalories.add(FoodCalories(parts[0].trim(), calories))
                else Log.w("Recommendation", "Ignored malformed food recommendation entry")
            }
        }
        return foodCalories
    }


}
