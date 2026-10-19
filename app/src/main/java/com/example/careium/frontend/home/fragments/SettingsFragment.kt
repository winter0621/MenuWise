package com.example.careium.frontend.home.fragments

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.careium.BuildConfig
import com.example.careium.R
import com.example.careium.databinding.FragmentSettingsBinding
import com.example.careium.frontend.home.activities.AiCalorieActivity
import com.example.careium.frontend.home.activities.HouseholdProfilesActivity
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance() = SettingsFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        val localPrefs = requireContext().getSharedPreferences("menuwise_local", 0)
        val signedIn = FirebaseAuth.getInstance().currentUser != null
        binding.switchOfflineMode.isChecked = localPrefs.getBoolean("offline_mode", !signedIn)
        binding.switchOfflineMode.isEnabled = signedIn
        binding.textModeSummary.text = if (signedIn) {
            getString(R.string.settings_mode_online_available)
        } else {
            getString(R.string.settings_mode_local_only)
        }
        binding.switchOfflineMode.setOnCheckedChangeListener { _, checked ->
            localPrefs.edit().putBoolean("offline_mode", checked).apply()
            Toast.makeText(
                requireContext(),
                if (checked) R.string.settings_offline_enabled else R.string.settings_online_enabled,
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.rowAiService.setOnClickListener {
            startActivity(Intent(requireContext(), AiCalorieActivity::class.java)
                .putExtra("open_service_settings", true))
        }
        binding.rowHouseholdProfiles.setOnClickListener {
            startActivity(Intent(requireContext(), HouseholdProfilesActivity::class.java))
        }
        binding.rowReminder.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.layout_main_frame, ReminderFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
        binding.rowLanguage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
        }
        binding.rowPrivacy.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_privacy_title)
                .setMessage(R.string.settings_privacy_message)
                .setPositiveButton(R.string.confirm, null)
                .show()
        }
        binding.textVersion.text = getString(R.string.settings_version_value, BuildConfig.VERSION_NAME)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
