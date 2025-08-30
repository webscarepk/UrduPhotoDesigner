package com.example.urduphotodesigner

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.databinding.ActivityMainBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var _navController: NavController? = null
    private val navController get() = _navController!!
    private val viewModel: CanvasViewModel by viewModels()
    val navOptions = NavOptions.Builder()
        .setLaunchSingleTop(true)
        .build()
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val widthVal = bitmap.width.toFloat()
                val heightVal = bitmap.height.toFloat()

                val canvasSize = CanvasSize("From Image", widthVal, heightVal)

                viewModel.clearCanvas()
                viewModel.setCanvasSize(canvasSize)
                viewModel.setCanvasBackgroundImage(bitmap)
                navController.navigate(R.id.editorFragment, null, navOptions)
            }
        }
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = ActivityMainBinding.inflate(layoutInflater)
        installSplashScreen().setKeepOnScreenCondition { false }
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.navigationBars())
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        initObservers()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_main) as NavHostFragment
        _navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> navController.navigate(R.id.homeFragment)
                R.id.nav_templates -> navController.navigate(R.id.templatesFragment)
                R.id.nav_add_images -> pickImageLauncher.launch("image/*")
                R.id.nav_fav -> navController.navigate(R.id.filesFragment)
                R.id.nav_settings -> navController.navigate(R.id.settingsFragment)
                else -> false
            }
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val visibleDestinations = setOf(
                R.id.homeFragment,
                R.id.templatesFragment,
                R.id.filesFragment,
                R.id.settingsFragment
            )
            binding.bottomNavigation.visibility =
                if (destination.id in visibleDestinations) View.VISIBLE else View.GONE
        }
    }

    private fun initObservers() {
        lifecycleScope.launch {
            mainViewModel.localFonts.collect { fonts ->
            }
        }

        lifecycleScope.launch {
            mainViewModel.localImages.collect { images ->
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _navController = null
        _binding = null
    }
}