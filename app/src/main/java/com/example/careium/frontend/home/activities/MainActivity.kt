package com.example.careium.frontend.home.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProviders
// 移除了 import com.etebarian.meowbottomnavigation.MeowBottomNavigation
import com.example.careium.R
import com.example.careium.core.database.authentication.InternetConnection
import com.example.careium.core.database.authentication.LocalAccountStore
import com.example.careium.core.database.authentication.Logout
import com.example.careium.core.database.authentication.SharedPreferences
import com.example.careium.core.database.realtime.DishImage
import com.example.careium.core.database.realtime.FoodData
import com.example.careium.core.database.realtime.UserData
import com.example.careium.core.database.local.OfflineFoodStore
import com.example.careium.core.models.DishClassification
import com.example.careium.core.models.DishNutritionRegression
import com.example.careium.databinding.ActivityMainBinding
import com.example.careium.frontend.authentication.activities.SplashActivity
import com.example.careium.frontend.factory.*
import com.example.careium.frontend.home.fragments.*

lateinit var nutritionViewModel: NutritionViewModel
lateinit var classifierViewModel: ClassifierViewModel

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var swipeListener: SwipeListener
    private lateinit var dishImage: Bitmap
    private lateinit var userDataViewModel: UserDataViewModel
    private val permission = Permissions(this, this)

    companion object {
        const val REQUEST_IMAGE_CAPTURE = 1
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userDataViewModel = ViewModelProviders.of(this).get(UserDataViewModel::class.java)
        observeUserDataCallBackChange()

        if (InternetConnection.isConnected(this) && !getSharedPreferences("menuwise_local", MODE_PRIVATE).getBoolean("offline_mode", false)) {
            val userData = UserData()
            userData.getUserData(userDataViewModel)
        } else {
            val name = getSharedPreferences("menuwise_local", MODE_PRIVATE).getString("name", "").orEmpty()
            if (name.isNotEmpty()) {
                binding.navigationView.getHeaderView(0).findViewById<TextView>(R.id.text_user_name).text = name
            }
        }

        nutritionViewModel = ViewModelProviders.of(this).get(NutritionViewModel::class.java)
        classifierViewModel = ViewModelProviders.of(this).get(ClassifierViewModel::class.java)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        initializeBottomNavigation(savedInstanceState)
        initializeQuickActions()
        makeNavigationViewItemsHooks()
        swipeListener = SwipeListener(this)

        binding.layoutToolbar.imageMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.layoutToolbar.imageNotifications.setOnClickListener {
            loadFragment(ReminderFragment.newInstance())
        }
    }

    /**
     * 重构后的方法，使用官方的 BottomNavigationView
     */
    private fun initializeBottomNavigation(savedState: Bundle?) {
        // 为新的导航栏设置点击监听器
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val fragment: Fragment? = when (item.itemId) {
                // 使用我们之前创建的 bottom_nav_menu.xml 文件中的ID
                R.id.navigation_home -> HomeFragment.newInstance()
                R.id.navigation_recipes -> RecipesFragment.newInstance()
                R.id.navigation_reports -> ReportsFragment.newInstance()
                R.id.navigation_recommend -> RecommendationFragment.newInstance()
                R.id.navigation_profile -> ProfileFragment.newInstance()
                else -> null
            }

            if (fragment != null) {
                loadFragment(fragment)
            }
            true // 返回 true 表示我们已经处理了这次点击事件
        }

        // 默认选中首页
        if (savedState == null) binding.bottomNavigationView.selectedItemId = R.id.navigation_home
        else binding.bottomNavigationView.menu.findItem(savedState.getInt("selected_nav", R.id.navigation_home)).isChecked = true
        binding.bottomNavigationView.setOnItemReselectedListener { }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_nav", binding.bottomNavigationView.selectedItemId)
        super.onSaveInstanceState(outState)
    }

    private fun makeNavigationViewItemsHooks() {
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.menu_item_home -> {
                    loadFragment(HomeFragment.newInstance())
                    binding.drawerLayout.closeDrawers()
                    // 联动更新底部导航栏的选中状态
                    binding.bottomNavigationView.selectedItemId = R.id.navigation_home
                    true
                }
                R.id.menu_item_recipes -> {
                    loadFragment(RecipesFragment.newInstance())
                    binding.drawerLayout.closeDrawers()
                    // 联动更新底部导航栏的选中状态
                    binding.bottomNavigationView.selectedItemId = R.id.navigation_recipes
                    true
                }
                R.id.menu_item_diary -> {
                    loadFragment(DiaryFragment.newInstance())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.menu_item_diet_calender -> {
                    loadFragment(CalenderFragment.newInstance())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.menu_item_photo_album -> {
                    loadFragment(AlbumFragment.newInstance())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.menu_item_reminder -> {
                    loadFragment(ReminderFragment.newInstance())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.menu_item_settings -> {
                    loadFragment(SettingsFragment.newInstance())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.menu_item_help -> {
                    loadFragment(HelpFragment.newInstance())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.menu_item_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.layout_main_frame, fragment)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    // ----- 从这里开始，下面的所有代码都保持原样，无需修改 -----

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Keep content below the status bar and above system navigation.
    }

    private fun hideNavigationAndStatusBars() {
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility == 0) {
                hideSystemBars()
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }

    private fun capturePlate() {
        startActivity(Intent(this, AiCalorieActivity::class.java).putExtra("open_camera", true))
    }

    fun startFoodCapture() {
        capturePlate()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMAGE_CAPTURE || resultCode != Activity.RESULT_OK || data == null) {
            return
        }

        val capturedBitmap = data.extras?.get("data") as? Bitmap ?: run {
            Toast.makeText(this, getString(R.string.capture_failed), Toast.LENGTH_SHORT).show()
            return
        }
        dishImage = capturedBitmap

        val offlineMode = getSharedPreferences("menuwise_local", MODE_PRIVATE).getBoolean("offline_mode", false)
        val dishImageUri = data.data
        if (offlineMode) {
            DishImage.saveLocalImage(this, dishImage)
        } else if (dishImageUri != null) {
            DishImage.uploadImage(dishImage, dishImageUri)
        }

        try {
            val nutritionList = makeRegressionPredictions(dishImage)
            val className = makeClassificationPrediction(dishImage)

            nutritionViewModel.mutableNutrition.value = nutritionList
            classifierViewModel.mutableDishName.value = className
            classifierViewModel.mutableDishImage.value = BitmapDrawable(resources, dishImage)

            if (offlineMode) OfflineFoodStore(this).add(className, nutritionList)
            else saveFoodDataInDatabase(className, nutritionList)
        } catch (e: Exception) {
            Log.d("DLModels", "Exception Occurred in DL Models")
        }
    }

    private fun makeRegressionPredictions(dishImage: Bitmap): ArrayList<Float> {
        val nutrition = DishNutritionRegression(this)
        val tensorBuffer = nutrition.loadImageBuffer(dishImage, 224, 224)
        return (nutrition.predictNutritionModel(tensorBuffer))
    }

    private fun makeClassificationPrediction(dishImage: Bitmap): String {
        val classifier = DishClassification(this)
        val tensorBuffer2 = classifier.loadImageBuffer(dishImage, 250, 250)
        return classifier.classifyDish(tensorBuffer2)
    }

    private fun saveFoodDataInDatabase(foodName: String, nutritionList: ArrayList<Float>) {
        val foodData = FoodData(foodName, nutritionList)
        foodData.saveFoodData()
    }

    private fun initializeQuickActions() {
        binding.layoutToolbar.imageQuickAdd.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("快捷记录")
                .setItems(arrayOf("早餐", "午餐", "晚餐", "加餐", "记录饮水", "拍照估算")) { _, index ->
                    when (index) {
                        4 -> showWaterDialog()
                        5 -> capturePlate()
                        else -> startActivity(Intent(this, MealPlanActivity::class.java)
                            .putExtra("focus_meal", listOf("breakfast", "lunch", "dinner", "snack")[index]))
                    }
                }.setNegativeButton("关闭", null).show()
        }
    }

    private fun showWaterDialog() {
        val prefs = getSharedPreferences("water_intake", MODE_PRIVATE)
        val account = LocalAccountStore.currentAccountId(this)
        val key = account + ":" + com.example.careium.core.planning.MealPlanStore.today()
        val total = prefs.getInt(key, 0)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("今日饮水 · $total 毫升")
            .setItems(arrayOf("记录 150 毫升", "记录 250 毫升", "记录 500 毫升")) { _, index ->
                val updated = total + listOf(150, 250, 500)[index]
                prefs.edit().putInt(key, updated).apply()
                Toast.makeText(this, "今日已记录 $updated 毫升", Toast.LENGTH_SHORT).show()
            }.setNeutralButton("清空今日") { _, _ ->
                androidx.appcompat.app.AlertDialog.Builder(this).setTitle("清空今日饮水记录？")
                    .setNegativeButton("取消", null).setPositiveButton("清空") { _, _ -> prefs.edit().remove(key).apply() }.show()
            }.setNegativeButton("关闭", null).show()
    }

    private fun deleteEmailFromSharedPreference() {
        val preference = SharedPreferences(this)
        preference.delete()
    }

    
    private fun observeUserDataCallBackChange() {
        userDataViewModel.mutableUserData.observe(this) { user ->
            if (user != null) {
                binding.navigationView.getHeaderView(0)
                    .findViewById<TextView>(R.id.text_user_name).text = user.name
            }
        }
    }

    private fun logout() {
        getSharedPreferences("menuwise_local", MODE_PRIVATE).edit().putBoolean("offline_mode", false).apply()
        LocalAccountStore(this).logout()
        deleteEmailFromSharedPreference()
        signOut()
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }

    private fun signOut() {
        val logout = Logout()
        logout.signOut()
    }
}
