package com.example.careium.frontend.home.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
import com.example.careium.core.database.authentication.Logout
import com.example.careium.core.database.authentication.SharedPreferences
import com.example.careium.core.database.realtime.DishImage
import com.example.careium.core.database.realtime.FoodData
import com.example.careium.core.database.realtime.UserData
import com.example.careium.core.models.DishClassification
import com.example.careium.core.models.DishNutritionRegression
import com.example.careium.databinding.ActivityMainBinding
import com.example.careium.databinding.LayoutFloatingMenuItemBinding
import com.example.careium.frontend.authentication.activities.SplashActivity
import com.example.careium.frontend.factory.*
import com.example.careium.frontend.home.fragments.*
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionButton
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu

lateinit var nutritionViewModel: NutritionViewModel
lateinit var classifierViewModel: ClassifierViewModel

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var swipeListener: SwipeListener
    private lateinit var actionMenu: FloatingActionMenu
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

        if (InternetConnection.isConnected(this)) {
            val userData = UserData()
            userData.getUserData(userDataViewModel)
        } else {
            Toast.makeText(this, getString(R.string.no_internet_connection), Toast.LENGTH_LONG).show()
        }

        hideNavigationAndStatusBars()
        initializeBottomNavigation() // 设置新的官方底部导航栏
        buildFloatingActionMenu()
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
    private fun initializeBottomNavigation() {
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
        binding.bottomNavigationView.selectedItemId = R.id.navigation_home
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
        if (hasFocus) {
            hideSystemBars()
        }
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

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (actionMenu.isOpen) {
            binding.layoutMainFrameOverlay.visibility = View.INVISIBLE
            actionMenu.close(true)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun capturePlate() {
        permission.checkPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE, 2)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
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

        val dishImageUri = data.data
        if (dishImageUri != null) {
            DishImage.uploadImage(dishImage, dishImageUri)
        }

        try {
            val nutritionList = makeRegressionPredictions(dishImage)
            val className = makeClassificationPrediction(dishImage)

            nutritionViewModel.mutableNutrition.value = nutritionList
            classifierViewModel.mutableDishName.value = className
            classifierViewModel.mutableDishImage.value = BitmapDrawable(resources, dishImage)

            saveFoodDataInDatabase(className, nutritionList)
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

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun buildFloatingActionMenu() {
        val primaryBtnIcon = ImageView(this)
        primaryBtnIcon.setImageDrawable(getDrawable(R.drawable.ic_round_add_24))
        val actionButton = FloatingActionButton.Builder(this).setContentView(primaryBtnIcon).build()
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorSecondary, typedValue, true)
        val colorSecondary: Int = typedValue.data
        actionButton.background.setTint(colorSecondary)

        val param = actionButton.layoutParams as ViewGroup.MarginLayoutParams
        param.setMargins(10, 10, 10, 240)
        actionButton.layoutParams = param

        val item1 = inflateFABMenuItem(FABItem.WATER, R.drawable.ic_menu_water_48, getString(R.string.quick_add_water))
        val item2 = inflateFABMenuItem(FABItem.DINNER, R.drawable.ic_menu_dinner_64, getString(R.string.dinner))
        val item3 = inflateFABMenuItem(FABItem.SNACK, R.drawable.ic_menu_snack_64, getString(R.string.snack))
        val item4 = inflateFABMenuItem(FABItem.LUNCH, R.drawable.ic_menu_lunch_64, getString(R.string.lunch))
        val item5 = inflateFABMenuItem(FABItem.BREAKFAST, R.drawable.ic_menu_breakfast_64, getString(R.string.breakfast))
        val item6 = inflateFABMenuItem(FABItem.TRACKER, R.drawable.ic_menu_camera_48, getString(R.string.quick_capture_food))

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val width: Float = displayMetrics.widthPixels.toFloat()
        val radius: Int = (width * 0.60f).toInt()

        actionMenu = FloatingActionMenu.Builder(this)
            .setRadius(radius)
            .addSubActionView(item1)
            .addSubActionView(item2)
            .addSubActionView(item3)
            .addSubActionView(item4)
            .addSubActionView(item5)
            .addSubActionView(item6)
            .attachTo(actionButton)
            .build()

        actionButton.setOnClickListener {
            actionMenu.toggle(true)
            if (actionMenu.isOpen) {
                binding.layoutMainFrameOverlay.visibility = View.VISIBLE
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawers()
                }
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun inflateFABMenuItem(id: FABItem, drawableID: Int, label: String): View {
        val itemBinding = LayoutFloatingMenuItemBinding.inflate(layoutInflater)
        val params = FrameLayout.LayoutParams(-2, -2, 51)
        itemBinding.root.layoutParams = params
        itemBinding.fabItem.setImageDrawable(getDrawable(drawableID))
        itemBinding.textFabLabel.text = label

        itemBinding.fabItem.setOnClickListener {
            when (id) {
                FABItem.TRACKER -> capturePlate()
                FABItem.BREAKFAST -> Toast.makeText(this, "Breakfast Button Clicked !!", Toast.LENGTH_SHORT).show()
                FABItem.LUNCH -> Toast.makeText(this, "Lunch Button Clicked !!", Toast.LENGTH_SHORT).show()
                FABItem.SNACK -> Toast.makeText(this, "Snack Button Clicked !!", Toast.LENGTH_SHORT).show()
                FABItem.DINNER -> Toast.makeText(this, "Dinner Button Clicked !!", Toast.LENGTH_SHORT).show()
                FABItem.WATER -> Toast.makeText(this, "Water Button Clicked !!", Toast.LENGTH_SHORT).show()
            }
        }
        return itemBinding.root
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
        if (InternetConnection.isConnected(this)) {
            deleteEmailFromSharedPreference()
            signOut()
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
        } else {
            Toast.makeText(this, getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
        }
    }

    private fun signOut() {
        val logout = Logout()
        logout.signOut()
    }
}
