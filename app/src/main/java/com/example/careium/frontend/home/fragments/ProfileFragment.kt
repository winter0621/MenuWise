package com.example.careium.frontend.home.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProviders
import com.example.careium.R
import com.example.careium.core.database.authentication.InternetConnection
import com.example.careium.core.database.realtime.UserData
import com.example.careium.core.models.User
import com.example.careium.databinding.FragmentProfileBinding
import com.example.careium.frontend.factory.UserDataViewModel
import com.example.careium.frontend.factory.Gender
import com.example.careium.frontend.factory.FutureGoal

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private lateinit var binding: FragmentProfileBinding
    private lateinit var userDataViewModel: UserDataViewModel
    private var currentUser: User? = null

    companion object {
        @JvmStatic
        fun newInstance() =
            ProfileFragment().apply {}
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentProfileBinding.bind(view)

        getUserData()

        binding.editProfileBtn.setOnClickListener { editLocalProfile() }
    }

    private fun getUserData() {
        userDataViewModel = ViewModelProviders.of(this).get(UserDataViewModel::class.java)

        binding.waitContainer.visibility = View.VISIBLE

        if (requireContext().getSharedPreferences("menuwise_local", 0).getBoolean("offline_mode", false)) {
            val prefs = requireContext().getSharedPreferences("menuwise_local", 0)
            val user = User().apply {
                name = prefs.getString("name", "本地用户") ?: "本地用户"
                height = prefs.getFloat("height", 0f); weight = prefs.getFloat("weight", 0f)
                age = prefs.getInt("age", 0); desiredWeight = prefs.getFloat("desired_weight", 0f)
                gender = runCatching { com.example.careium.frontend.factory.Gender.valueOf(prefs.getString("gender", "Male")!!) }.getOrDefault(com.example.careium.frontend.factory.Gender.Male)
                futureGoal = runCatching { com.example.careium.frontend.factory.FutureGoal.valueOf(prefs.getString("goal", "FitnessTracker")!!) }.getOrDefault(com.example.careium.frontend.factory.FutureGoal.FitnessTracker)
            }
            updateTextView(user); binding.waitContainer.visibility = View.GONE
            return
        }

        if (InternetConnection.isConnected(this.requireContext())) {
            val userData = UserData()
            userData.getUserData(userDataViewModel)

            userDataViewModel.mutableUserData.observe(viewLifecycleOwner) { user ->
                if (user != null) {
                    binding.waitContainer.visibility = View.GONE
                    updateTextView(userDataViewModel.mutableUserData.value!!)
                }
            }
        } else {
            Toast.makeText(activity, getString(R.string.no_internet_connection), Toast.LENGTH_SHORT)
                .show()
        }


    }

    private fun updateTextView(user: User) {
        currentUser = user

        binding.userName.text = getString(R.string.user_name_profile, user.name)
        binding.userHeightVal.text = getString(R.string.height_val, user.height)
        binding.userWeightVal.text = getString(R.string.weight_val, user.weight)
        binding.userAgeVal.text = getString(R.string.age_val, user.age)
        binding.userDesiredWeight.text = getString(R.string.desired_weight_val, user.desiredWeight)
        binding.userGenderVal.text = getString(R.string.gender_val,
            getString(if (user.gender == com.example.careium.frontend.factory.Gender.Male) R.string.male else R.string.female))
        val goal = when (user.futureGoal) {
            com.example.careium.frontend.factory.FutureGoal.LoseWeight -> R.string.lose_weight
            com.example.careium.frontend.factory.FutureGoal.GainWeight -> R.string.gain_weight
            com.example.careium.frontend.factory.FutureGoal.FitnessTracker -> R.string.fitness_tracker
            else -> R.string.patient_treatment
        }
        binding.userTypeVal.text = getString(R.string.type_val, getString(goal))
    }

    private fun editLocalProfile() {
        val prefs = requireContext().getSharedPreferences("menuwise_local", 0)
        val existing = currentUser
        val fields = listOf(
            EditText(requireContext()).apply { hint = "姓名"; setText(existing?.name?.takeIf { it.isNotBlank() } ?: prefs.getString("name", "本地用户")) },
            EditText(requireContext()).apply { hint = "身高（厘米）"; inputType = 8194; setText((existing?.height?.takeIf { it > 0 } ?: prefs.getFloat("height", 0f)).takeIf { it > 0 }?.toString()) },
            EditText(requireContext()).apply { hint = "体重（千克）"; inputType = 8194; setText((existing?.weight?.takeIf { it > 0 } ?: prefs.getFloat("weight", 0f)).takeIf { it > 0 }?.toString()) },
            EditText(requireContext()).apply { hint = "年龄"; inputType = 2; setText((existing?.age?.takeIf { it > 0 } ?: prefs.getInt("age", 0)).takeIf { it > 0 }?.toString()) },
            EditText(requireContext()).apply { hint = "目标体重（千克）"; inputType = 8194; setText((existing?.desiredWeight?.takeIf { it > 0 } ?: prefs.getFloat("desired_weight", 0f)).takeIf { it > 0 }?.toString()) }
        )
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0); fields.forEach { addView(it) }
        }
        val gender = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.male), getString(R.string.female)))
            val selected = existing?.gender?.let { if (it == Gender.Female) "Female" else "Male" } ?: prefs.getString("gender", "Male")
            setSelection(if (selected == "Female") 1 else 0)
        }
        content.addView(TextView(requireContext()).apply {
            text = getString(R.string.gender); setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
        })
        content.addView(gender)
        val goals = listOf(FutureGoal.LoseWeight, FutureGoal.GainWeight, FutureGoal.FitnessTracker, FutureGoal.PatientTreatment)
        val goalSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf(
                getString(R.string.lose_weight), getString(R.string.gain_weight),
                getString(R.string.fitness_tracker), getString(R.string.patient_treatment)))
            val selected = existing?.futureGoal ?: runCatching { FutureGoal.valueOf(prefs.getString("goal", "FitnessTracker")!!) }.getOrDefault(FutureGoal.FitnessTracker)
            setSelection(goals.indexOf(selected).coerceAtLeast(0))
        }
        content.addView(TextView(requireContext()).apply {
            text = getString(R.string.future_goal); setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
        })
        content.addView(goalSpinner)
        AlertDialog.Builder(requireContext()).setTitle("编辑个人资料").setView(content)
            .setNegativeButton("取消", null).setPositiveButton("保存") { _, _ ->
                val height = fields[1].text.toString().toFloatOrNull() ?: 0f
                val weight = fields[2].text.toString().toFloatOrNull() ?: 0f
                val age = fields[3].text.toString().toIntOrNull() ?: 0
                if (height <= 0 || weight <= 0 || age <= 0) {
                    Toast.makeText(requireContext(), "请填写有效的身高、体重和年龄", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                val updated = existing ?: User()
                updated.name = fields[0].text.toString().ifBlank { "本地用户" }
                updated.height = height; updated.weight = weight; updated.age = age
                updated.desiredWeight = fields[4].text.toString().toFloatOrNull() ?: weight
                updated.gender = if (gender.selectedItemPosition == 1) Gender.Female else Gender.Male
                updated.futureGoal = goals[goalSpinner.selectedItemPosition.coerceIn(goals.indices)]
                prefs.edit().putString("name", updated.name)
                    .putFloat("height", height).putFloat("weight", weight).putInt("age", age)
                    .putFloat("desired_weight", updated.desiredWeight)
                    .putString("gender", if (updated.gender == Gender.Female) "Female" else "Male")
                    .putString("goal", updated.futureGoal?.name).apply()
                val offline = prefs.getBoolean("offline_mode", false)
                if (!offline && InternetConnection.isConnected(requireContext())) UserData().saveUserData(updated)
                else if (!offline) Toast.makeText(requireContext(), "网络不可用，资料已保存在本机", Toast.LENGTH_SHORT).show()
                updateTextView(updated)
            }.show()
    }

}
