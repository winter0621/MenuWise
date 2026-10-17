package com.example.careium.frontend.authentication.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.example.careium.backend.authentication.AuthenticationInterface
import com.example.careium.databinding.ActivitySplashBinding
import com.example.careium.frontend.home.activities.AiCalorieActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity(), AuthenticationInterface {
    private lateinit var binding: ActivitySplashBinding

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: Activity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        instance = this

        buildAnimation()
        handleClickButtons()
        authenticateUser()
    }

    private fun buildAnimation() {
        YoYo.with(Techniques.FlipInX)
            .duration(2000)
            .repeat(0)
            .playOn(binding.welcomeLabel)

        YoYo.with(Techniques.ZoomIn)
            .duration(2000)
            .repeat(0)
            .playOn(binding.appNameLabel)
    }

    private fun handleClickButtons() {
        binding.aiGuestBtn.setOnClickListener {
            startActivity(Intent(this, AiCalorieActivity::class.java))

        }
        binding.planGuestBtn.setOnClickListener {
            startActivity(Intent(this, com.example.careium.frontend.home.activities.MealPlanActivity::class.java))
        }
        binding.offlineModeBtn.setOnClickListener {
            getSharedPreferences("menuwise_local", MODE_PRIVATE).edit().putBoolean("offline_mode", true).apply()
            startActivity(Intent(this, com.example.careium.frontend.home.activities.MainActivity::class.java))
            finish()
        }
        binding.loginBtn.setOnClickListener {
            authLogin()
        }

        binding.registerBtn.setOnClickListener {
            authRegister()
        }
    }

    override fun buttonsVisibilityGone() {
        binding.loginBtn.visibility = View.GONE
        binding.registerBtn.visibility = View.GONE
    }

}
