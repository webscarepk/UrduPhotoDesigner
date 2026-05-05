package com.webscare.urducanvas

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.utils.BlurEngine
import com.webscare.urducanvas.common.views.LiquidGlassNavBar
import com.webscare.urducanvas.databinding.ActivityMainBinding
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.di.UpdateManager
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var updateCheckTriggered = false

    private var _navController: NavController? = null
    private val navController get() = _navController!!

    private val viewModel: CanvasViewModel by viewModels()

    val navOptions: NavOptions = NavOptions.Builder().setLaunchSingleTop(true).build()

    /** Blur engine — internal: exposed so fragments can call forceCapture() on scroll idle. */
    internal var blurEngine: BlurEngine? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                val canvasSize = CanvasSize(id = 0, "From Image", bitmap.width.toFloat(), bitmap.height.toFloat())
                viewModel.clearCanvas()
                viewModel.setCanvasSize(canvasSize)
                viewModel.setCanvasBackgroundImage(bitmap, this@MainActivity)
                val editorNavOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.editorFragment, inclusive = true)
                    .build()
                navController.navigate(R.id.editorFragment, null, editorNavOptions)
            }
        }

    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var updateManager: UpdateManager

    private val mainViewModel: MainViewModel by viewModels()

    // ──────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        installSplashScreen().setKeepOnScreenCondition { false }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        updateManager.registerListener(this)
        forceImmersiveMode()
        billingManager.checkSubscriptionOnLaunch()
        initObservers()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_main) as NavHostFragment
        _navController = navHostFragment.navController

        setupLiquidGlassNav()
        handleIncomingIntent(intent)
    }

    // ──────────────────────────────────────────────────────────
    // Liquid Glass Nav setup
    // ──────────────────────────────────────────────────────────

    private fun setupLiquidGlassNav() {
        val nav = binding.bottomNavigation

        // Build nav items — icons use selector drawables with state_checked
        nav.setItems(
            listOf(
                LiquidGlassNavBar.NavItem(
                    id = R.id.nav_home,
                    iconDrawable = getDrawable(R.drawable.ic_home_selector)
                ),
                LiquidGlassNavBar.NavItem(
                    id = R.id.nav_templates,
                    iconDrawable = getDrawable(R.drawable.ic_templates_selector)
                ),
                LiquidGlassNavBar.NavItem(
                    id = R.id.nav_add_images,
                    iconDrawable = getDrawable(R.drawable.ic_add_image_stroke),
                    isCta = true        // no selection state — slides never land here
                ),
                LiquidGlassNavBar.NavItem(
                    id = R.id.nav_fav,
                    iconDrawable = getDrawable(R.drawable.ic_file_selector)
                ),
                LiquidGlassNavBar.NavItem(
                    id = R.id.nav_settings,
                    iconDrawable = getDrawable(R.drawable.ic_setting_selector)
                )
            )
        )

        nav.setOnItemSelectedListener { slotIndex ->
            when (slotIndex) {
                0 -> { // home
                    if (navController.currentDestination?.id != R.id.homeFragment) {
                        navController.navigate(R.id.homeFragment, null, navOptions)
                    }
                }
                1 -> { // templates
                    if (navController.currentDestination?.id != R.id.templateCategoriesFragment) {
                        navController.navigate(R.id.templateCategoriesFragment, null, navOptions)
                    }
                }
                2 -> { // add (CTA)
                    pickImageLauncher.launch("image/*")
                }
                3 -> { // files/fav
                    if (navController.currentDestination?.id != R.id.filesFragment) {
                        navController.navigate(R.id.filesFragment, null, navOptions)
                    }
                }
                4 -> { // settings
                    if (navController.currentDestination?.id != R.id.settingsFragment) {
                        navController.navigate(R.id.settingsFragment, null, navOptions)
                    }
                }
            }
        }

        // Sync indicator & visibility on destination changes
        navController.addOnDestinationChangedListener { _, destination, _ ->

            if (destination.id == R.id.homeFragment) {
                // Check once per session — UpdateManager should guard against
                // repeated network calls internally, but the flag below is a
                // cheap safety net in case it doesn't.
                if (!updateCheckTriggered) {
                    updateCheckTriggered = true
                    updateManager.checkForUpdate(this)
                }
            }

            val visibleDestinations = setOf(
                R.id.homeFragment,
                R.id.templateCategoriesFragment,
                R.id.filesFragment,
                R.id.settingsFragment
            )

            if (destination.id in visibleDestinations) {
                // Show with slide-up animation — NOTE: we never toggle visibility
                // from a background thread; this is always on main thread here.
                if (!nav.isVisible) {
                    nav.apply {
                        visibility = View.VISIBLE
                        translationY = height.toFloat()
                        animate().translationY(0f).setDuration(400).start()
                    }
                }
                if (blurEngine?.isRunning != true) blurEngine?.startContinuous()
            } else {
                // Hide: animate out then GONE — still main thread only
                if (nav.isVisible) {
                    nav.animate()
                        .translationY(nav.height.toFloat() + 40f)
                        .setDuration(300)
                        .withEndAction {
                            // Safety: only touch visibility on main thread, check not destroyed
                            if (!isDestroyed) nav.visibility = View.GONE
                        }
                        .start()
                }
                blurEngine?.stopContinuous()
            }

            setStatusBarTextColor(darkIcons = true)

            // Re-register a one-shot layout listener so blur captures the
            // new fragment exactly once after it has fully drawn.
            val host = binding.navHostMain
            host.viewTreeObserver.addOnGlobalLayoutListener(object :
                android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    host.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    blurEngine?.forceCapture()
                }
            })

            // Sync indicator — animate=false: no scale burst fires on arrival
            when (destination.id) {
                R.id.homeFragment                -> nav.selectItem(0, animate = false)
                R.id.templateCategoriesFragment  -> nav.selectItem(1, animate = false)
                R.id.filesFragment               -> nav.selectItem(3, animate = false)
                R.id.settingsFragment            -> nav.selectItem(4, animate = false)
            }
        }

        // Wire back-press
        onBackPressedDispatcher.addCallback(this) {
            when (navController.currentDestination?.id) {
                R.id.editorFragment -> { /* editor handles its own back */ }
                R.id.homeFragment -> finish()
                R.id.templateCategoriesFragment,
                R.id.filesFragment,
                R.id.settingsFragment -> navController.navigate(R.id.homeFragment, null, navOptions)
                else -> { /* do nothing */ }
            }
        }

        // Start blur engine once views are laid out.
        //
        // SOURCE must be binding.navHostMain (FragmentContainerView), NOT binding.root.
        //
        // If we use binding.root (ConstraintLayout), source.draw() renders BOTH the
        // fragment content AND the LiquidGlassNavBar (which is a child of root).
        // The nav bar's green icons get blurred and fed back into the bar background,
        // producing the green glow halo around icons seen in the screenshot.
        //
        // FragmentContainerView is a SIBLING of LiquidGlassNavBar in the layout, so
        // drawing it never includes the nav bar. It also fills the full screen
        // (constraints: top/bottom/start/end of parent), so the region behind the bar
        // is fully rendered — the RecyclerView content extends behind the floating bar.
        //
        // Background stripping: remove the background from navHostMain so that if a
        // fragment sets a white/solid background the FragmentContainerView itself stays
        // transparent, allowing content behind it to show in the blur crop.
        nav.post {
            val fragmentHost = binding.navHostMain
            fragmentHost.background = null
            blurEngine = BlurEngine(fragmentHost, nav)
            blurEngine?.updatePositions()
            blurEngine?.startContinuous()

            // OnDrawListener fires AFTER each draw pass completes — including when
            // Glide/Coil posts a bitmap to an ImageView and triggers invalidate().
            // This is the correct hook: blur captures the fully-rendered frame,
            // not a pre-draw snapshot. Throttled by pending flag — safe at 60fps.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                fragmentHost.viewTreeObserver.addOnDrawListener {
                    blurEngine?.scheduleCapture()
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // Status bar / immersive
    // ──────────────────────────────────────────────────────────

    private fun setStatusBarTextColor(darkIcons: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                if (darkIcons) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (darkIcons) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
    }

    private fun forceImmersiveMode() {
        window?.let { w ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.insetsController?.apply {
                    hide(WindowInsets.Type.navigationBars())
                    show(WindowInsets.Type.statusBars())
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // Intent handling
    // ──────────────────────────────────────────────────────────

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null && intent.type?.startsWith("image/") == true) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val canvasSize = CanvasSize(id = 0,"From Image", bitmap.width.toFloat(), bitmap.height.toFloat())
                        viewModel.clearCanvas()
                        viewModel.setCanvasSize(canvasSize)
                        viewModel.setCanvasBackgroundImage(bitmap, this@MainActivity)
                        val opts = NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(R.id.editorFragment, inclusive = true)
                            .build()
                        navController.navigate(R.id.editorFragment, null, opts)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // Observers / Resume / Result / Destroy
    // ──────────────────────────────────────────────────────────

    private fun initObservers() {
        lifecycleScope.launch { mainViewModel.localFonts.collect { } }
        lifecycleScope.launch { mainViewModel.localImages.collect { } }
        lifecycleScope.launch { billingManager.isSubscribed.collect { } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) forceImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        updateManager.onResume(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UpdateManager.REQUEST_CODE_UPDATE && resultCode != RESULT_OK) {
            updateManager.checkForUpdate(this)
        }
    }

    override fun onDestroy(){
        super.onDestroy()
        blurEngine?.destroy()
        blurEngine = null
        updateManager.onDestroy()
        _navController = null
        _binding = null
    }
}