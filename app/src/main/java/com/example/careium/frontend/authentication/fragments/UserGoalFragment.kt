package com.example.careium.frontend.authentication.fragments

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProviders
import com.example.careium.R
import com.example.careium.core.authentication.Authenticator
import com.example.careium.core.database.authentication.AuthViewModel
import com.example.careium.core.database.authentication.LocalAccountStore
import com.example.careium.core.database.authentication.SharedPreferences
import com.example.careium.databinding.LayoutErrorCustomViewBinding
import com.example.careium.databinding.FragmentUserGoalBinding
import com.example.careium.frontend.authentication.activities.SplashActivity
import com.example.careium.frontend.factory.ErrorAlertDialog
import com.example.careium.frontend.factory.FutureGoal
import com.example.careium.frontend.home.activities.MainActivity

class UserGoalFragment : Fragment(R.layout.fragment_user_goal) {
    private lateinit var binding: FragmentUserGoalBinding
    private lateinit var authViewModel: AuthViewModel
    private val user = Authenticator.user

    companion object {
        fun newInstance() = UserGoalFragment().apply {}
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentUserGoalBinding.bind(view)
        authViewModel = ViewModelProviders.of(this).get(AuthViewModel::class.java)
        observeAuthCallBackChange()

        updateUserDataUI()
        handleClickButtons()
    }

    private fun handleClickButtons() {
        binding.finalRegister.setOnClickListener {
            val desiredWeight = binding.goalDesiredWeight.text.toString()
            val desiredWeightValue = desiredWeight.toFloatOrNull()
            var futureGoal: FutureGoal? = null
            when {
                binding.goalLoseWeight.isChecked -> futureGoal = FutureGoal.LoseWeight
                binding.goalGainWeight.isChecked -> futureGoal = FutureGoal.GainWeight
                binding.goalFitnessTracker.isChecked -> futureGoal = FutureGoal.FitnessTracker
                binding.goalPatientTreatment.isChecked -> futureGoal = FutureGoal.PatientTreatment
            }

            when {
                desiredWeightValue == null || desiredWeightValue <= 0f -> alert(
                    getString(R.string.error_title),
                    getString(R.string.error_weight_message)
                )
                futureGoal == null -> alert(
                    getString(R.string.error_title),
                    getString(R.string.error_future_goal_message)
                )
                else -> {
                    saveUserData(desiredWeightValue, futureGoal)
                    createNewAccount()
                }

            }

        }

        binding.backIcon.setOnClickListener {
            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(R.id.auth_frame, UserInfoFragment.newInstance())
                ?.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                ?.commit()
        }

    }

    private fun createNewAccount() {
        setSubmitting(true)
        val error = LocalAccountStore(requireContext()).register(user)
        if (error == null) {
            authViewModel.mutableIsAuthComplete.value = true
        } else {
            authViewModel.mutableAuthError.value = error
        }
    }

    private fun observeAuthCallBackChange() {
        authViewModel.mutableIsAuthComplete.observe(viewLifecycleOwner) { isLogged ->
            if (isLogged) {
                commitEmailOnSharedPreference()
                openMainActivity()
            }
        }
        authViewModel.mutableAuthError.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                setSubmitting(false)
                alert(getString(R.string.error_title), message)
            }
        }
    }

    private fun commitEmailOnSharedPreference() {
        val preference = this.context?.let { SharedPreferences(it) }
        preference?.write(user.email)
    }

    private fun alert(title: String, message: String) {
        val view: LayoutErrorCustomViewBinding = binding.goalErrorView
        ErrorAlertDialog.alert(view, title, message)
    }

    private fun setSubmitting(isSubmitting: Boolean) {
        binding.goalProgress.visibility = if (isSubmitting) View.VISIBLE else View.GONE
        binding.finalRegister.isEnabled = !isSubmitting
        binding.backIcon.isEnabled = !isSubmitting
    }

    private fun openMainActivity() {
        Toast.makeText(activity, getString(R.string.confirmation_register_msg), Toast.LENGTH_SHORT)
            .show()
        startActivity(Intent(activity, MainActivity::class.java))
        SplashActivity.instance.finish()
        activity?.finish()
    }

    private fun saveUserData(desiredWeight: Float, futureGoal: FutureGoal) {
        user.desiredWeight = desiredWeight
        user.futureGoal = futureGoal
    }

    private fun updateUserDataUI() {
        if (user.desiredWeight != 0f && user.futureGoal != null) {
            binding.goalDesiredWeight.setText(user.desiredWeight.toString())
            when (user.futureGoal) {
                FutureGoal.LoseWeight -> binding.goalLoseWeight.isChecked = true
                FutureGoal.GainWeight -> binding.goalGainWeight.isChecked = true
                FutureGoal.FitnessTracker -> binding.goalFitnessTracker.isChecked = true
                FutureGoal.PatientTreatment -> binding.goalPatientTreatment.isChecked = true
                else -> {}
            }
        }
    }

}
