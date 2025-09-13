package com.example.urduphotodesigner

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.databinding.ActivityMainBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
            // Navigation only, NO selection handling here
            when (item.itemId) {
                R.id.nav_home -> {
                    if (navController.currentDestination?.id != R.id.homeFragment) {
                        navController.navigate(R.id.homeFragment, null, navOptions)
                    }
                }

                R.id.nav_templates -> {
                    if (navController.currentDestination?.id != R.id.templatesFragment) {
                        navController.navigate(R.id.templatesFragment, null, navOptions)
                    }
                }

                R.id.nav_add_images -> {
                    pickImageLauncher.launch("image/*")
                }

                R.id.nav_fav -> {
                    if (navController.currentDestination?.id != R.id.filesFragment) {
                        navController.navigate(R.id.filesFragment, null, navOptions)
                    }
                }

                R.id.nav_settings -> {
                    if (navController.currentDestination?.id != R.id.settingsFragment) {
                        navController.navigate(R.id.settingsFragment, null, navOptions)
                    }
                }

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

            // ✅ Selection only, NO navigation here
            when (destination.id) {
                R.id.homeFragment -> binding.bottomNavigation.menu.findItem(R.id.nav_home).isChecked =
                    true

                R.id.templatesFragment -> binding.bottomNavigation.menu.findItem(R.id.nav_templates).isChecked =
                    true

                R.id.filesFragment -> binding.bottomNavigation.menu.findItem(R.id.nav_fav).isChecked =
                    true

                R.id.settingsFragment -> binding.bottomNavigation.menu.findItem(R.id.nav_settings).isChecked =
                    true
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            val currentDest = navController.currentDestination?.id

            when (currentDest) {
                R.id.editorFragment -> {}

                R.id.homeFragment -> {
                    finish()
                }

                R.id.templatesFragment,
                R.id.filesFragment,
                R.id.settingsFragment -> {
                    navController.navigate(R.id.homeFragment, null, navOptions)
                }

                else -> {}
            }
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Handle all possible entry cases:
     * - Normal app start (no data)
     * - Cold start with image
     * - Warm start with image
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }

        if (uri != null && intent.type?.startsWith("image/") == true) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val canvasSize = CanvasSize(
                            "From Image",
                            bitmap.width.toFloat(),
                            bitmap.height.toFloat()
                        )
                        viewModel.clearCanvas()
                        viewModel.setCanvasSize(canvasSize)
                        viewModel.setCanvasBackgroundImage(bitmap)

                        navController.navigate(R.id.editorFragment, null, navOptions)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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