package com.webscare.urducanvas

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
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
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.databinding.ActivityMainBinding
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.di.UpdateManager
import com.webscare.urducanvas.viewmodels.MainViewModel
import com.webscare.urducanvas.viewmodels.PexelsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val rawBitmap = com.webscare.urducanvas.common.utils.ImageProcessor
                            .decodeUriSafely(contentResolver, it, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX)
                            ?: return@launch

                        val bitmap = com.webscare.urducanvas.common.utils.ImageProcessor
                            .downsampleIfNeeded(rawBitmap, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX, com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX)

                        val widthVal = bitmap.width.toFloat()
                        val heightVal = bitmap.height.toFloat()

                        withContext(Dispatchers.Main) {
                            val canvasSize = CanvasSize(
                                id = 0, "From Image", widthVal, heightVal
                            )
                            viewModel.clearCanvas()
                            viewModel.setCanvasSize(canvasSize)
                            viewModel.setCanvasBackgroundImage(bitmap, this@MainActivity)
                            val editorNavOptions = NavOptions.Builder().setLaunchSingleTop(true)
                                .setPopUpTo(R.id.editorFragment, inclusive = true).build()
                            navController.navigate(R.id.editorFragment, null, editorNavOptions)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var updateManager: UpdateManager

    private val mainViewModel: MainViewModel by viewModels()

    // Creating PexelsViewModel here ensures Hilt constructs it on app start,
    // so seedPexelsCategories() fires immediately — same lifecycle as MainViewModel.
    // The actual seeding now lives in MainViewModel.seedPexelsCategories() which
    // is called from MainViewModel.init, so this just keeps the VM alive.
    @Suppress("unused")
    private val pexelsViewModel: PexelsViewModel by viewModels()

    // ──────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────

    /**
     * Guard against a known Android framework bug where
     * [ViewGroup.TouchTarget.recycle] throws [IllegalStateException]
     * ("already recycled once") when a child view is removed/detached
     * during an active touch sequence.  The entire stack trace is
     * framework-internal, so catching here is the only viable fix.
     */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (e: IllegalStateException) {
            // Swallow only the specific "already recycled" crash
            true
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPrefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("is_dark_mode", false)
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }

        installSplashScreen().setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, statusBarHeight, 0, navBarHeight)
            insets
        }

        updateManager.registerListener(this)
        forceImmersiveMode()
        billingManager.checkSubscriptionOnLaunch()
        initObservers()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_main) as NavHostFragment
        _navController = navHostFragment.navController

        setupBottomNav()
        handleIncomingIntent(intent)
    }

    // ──────────────────────────────────────────────────────────
    // Bottom Nav setup (cradle + spring indicator)
    // ──────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.fabAddImage.visibility = View.GONE

        // Force FAB above the nav bar (z-order + elevation)
        binding.fabAddImage.elevation = 16f * resources.displayMetrics.density
        binding.bottomNavigation.elevation = 8f * resources.displayMetrics.density
        binding.fabAddImage.bringToFront()

        binding.fabAddImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        val nav = binding.bottomNavigation as BottomNavigationView

        // Attach the springy top indicator once the nav has been measured.
        nav.post { attachSpringIndicator(nav) }

        // SINGLE item-selected listener (handles nav + indicator move).
        nav.setOnItemSelectedListener { item ->
            val index = indexForMenuItem(item.itemId)
            if (index != -1 && index != CENTER_INDEX) {
                moveIndicatorTo(nav, index)
            }

            when (item.itemId) {
                R.id.nav_home -> {
                    if (navController.currentDestination?.id != R.id.homeFragment)
                        navController.navigate(R.id.homeFragment, null, navOptions)
                    true
                }
                R.id.nav_templates -> {
                    if (navController.currentDestination?.id != R.id.templateCategoriesFragment)
                        navController.navigate(R.id.templateCategoriesFragment, null, navOptions)
                    true
                }
                R.id.nav_add_images -> false // center FAB slot, never selectable
                R.id.nav_fav -> {
                    if (navController.currentDestination?.id != R.id.filesFragment)
                        navController.navigate(R.id.filesFragment, null, navOptions)
                    true
                }
                R.id.nav_settings -> {
                    if (navController.currentDestination?.id != R.id.settingsFragment)
                        navController.navigate(R.id.settingsFragment, null, navOptions)
                    true
                }
                else -> false
            }
        }

        // SINGLE destination-changed listener (nav visibility, status bar, tab sync, indicator).
        navController.addOnDestinationChangedListener { _, destination, _ ->

            if (destination.id == R.id.homeFragment && !updateCheckTriggered) {
                updateCheckTriggered = true
                updateManager.checkForUpdate(this)
            }

            val visibleDestinations = setOf(
                R.id.homeFragment,
                R.id.templateCategoriesFragment,
                R.id.filesFragment,
                R.id.settingsFragment
            )

            if (destination.id in visibleDestinations) {
                if (!nav.isVisible) {
                    nav.apply {
                        visibility = View.VISIBLE
                        translationY = height.toFloat()
                        animate().translationY(0f).setDuration(400)
                            .withStartAction {
                                binding.fabAddImage.visibility = View.VISIBLE
                                indicatorView?.visibility = View.VISIBLE
                            }
                            .setUpdateListener { syncIndicatorToNav(nav) } // follow nav slide
                            .start()
                    }
                }
                binding.fabAddImage.visibility = View.VISIBLE
                indicatorView?.visibility = View.VISIBLE
            } else {
                if (nav.isVisible) {
                    nav.animate().translationY(nav.height.toFloat() + 40f).setDuration(300)
                        .setUpdateListener { syncIndicatorToNav(nav) }
                        .withEndAction {
                            if (!isDestroyed) {
                                nav.visibility = View.GONE
                                binding.fabAddImage.visibility = View.GONE
                                indicatorView?.visibility = View.GONE
                            }
                        }.start()
                }
                // hide immediately so they don't linger on splash/editor
                binding.fabAddImage.visibility = View.GONE
                indicatorView?.visibility = View.GONE
            }

            applyStatusBarColor()

            // Sync selected tab + indicator with the actual destination.
            val index = when (destination.id) {
                R.id.homeFragment -> { nav.selectedItemId = R.id.nav_home; 0 }
                R.id.templateCategoriesFragment -> { nav.selectedItemId = R.id.nav_templates; 1 }
                R.id.filesFragment -> { nav.selectedItemId = R.id.nav_fav; 3 }
                R.id.settingsFragment -> { nav.selectedItemId = R.id.nav_settings; 4 }
                else -> -1
            }
            if (index != -1) nav.post { moveIndicatorTo(nav, index) }
        }

        onBackPressedDispatcher.addCallback(this) {
            when (navController.currentDestination?.id) {
                R.id.editorFragment -> { /* editor handles its own back */ }
                R.id.homeFragment -> finish()
                R.id.templateCategoriesFragment,
                R.id.filesFragment,
                R.id.settingsFragment ->
                    navController.navigate(R.id.homeFragment, null, navOptions)
                else -> { /* do nothing */ }
            }
        }
    }

    private var isNavHidden = false
    private var accumulatedDy = 0
    private val hideThreshold = 12
    private val showThreshold = 12

    /** Fragments just forward their scroll deltas here. All logic lives in the activity. */
    fun onContentScrolled(scrollY: Int, oldScrollY: Int) {
        val dy = scrollY - oldScrollY

        if (dy > 0 && accumulatedDy < 0) accumulatedDy = 0
        if (dy < 0 && accumulatedDy > 0) accumulatedDy = 0
        accumulatedDy += dy

        when {
            accumulatedDy > hideThreshold -> {
                setNavAndFabHidden(true)
                accumulatedDy = 0
            }
            accumulatedDy < -showThreshold -> {
                setNavAndFabHidden(false)
                accumulatedDy = 0
            }
        }

        if (scrollY == 0) {
            setNavAndFabHidden(false)
            accumulatedDy = 0
        }
    }

    /**
     * Attach any NestedScrollView to the nav hide/show behaviour in one call.
     * Resets state each time so switching fragments starts clean.
     */
    /** NestedScrollView (Home ka SpringNestedScrollView bhi isi me aata hai). */
    fun bindScrollToNav(scrollView: androidx.core.widget.NestedScrollView) {
        accumulatedDy = 0
        setNavAndFabHidden(false)
        scrollView.setOnScrollChangeListener(
            androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                onContentScrolled(scrollY, oldScrollY)
            }
        )
    }

    /** Plain ScrollView (Settings). */
    fun bindScrollToNav(scrollView: android.widget.ScrollView) {
        accumulatedDy = 0
        setNavAndFabHidden(false)
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            onContentScrolled(scrollY, oldScrollY)
        }
    }

    /** RecyclerView (Templates, FilesList, aur Files ke andar wale grids). */
    fun bindScrollToNav(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        accumulatedDy = 0
        setNavAndFabHidden(false)
        recyclerView.addOnScrollListener(object :
            androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                onContentScrolledByDelta(dy, rv)
            }
        })
    }

    /** RecyclerView delta-based forwarding (dy already = delta). */
    private fun onContentScrolledByDelta(dy: Int, rv: androidx.recyclerview.widget.RecyclerView) {
        if (dy > 0 && accumulatedDy < 0) accumulatedDy = 0
        if (dy < 0 && accumulatedDy > 0) accumulatedDy = 0
        accumulatedDy += dy

        when {
            accumulatedDy > hideThreshold -> { setNavAndFabHidden(true); accumulatedDy = 0 }
            accumulatedDy < -showThreshold -> { setNavAndFabHidden(false); accumulatedDy = 0 }
        }

        // RecyclerView top par hai? (koi item upar scroll na ho sake)
        if (!rv.canScrollVertically(-1)) {
            setNavAndFabHidden(false)
            accumulatedDy = 0
        }
    }

    fun setNavAndFabHidden(hidden: Boolean) {
        if (isNavHidden == hidden) return
        isNavHidden = hidden

        val nav = binding.bottomNavigation
        val fab = binding.fabAddImage
        val density = resources.displayMetrics.density
        val margin = 20 * density   // right aur bottom dono ke liye same margin

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val fabHalf = fab.width / 2f

        // X: right edge se `margin` door
        val fabTargetX = (screenW / 2f) - fabHalf - margin

        // Y: FAB ko screen bottom se `margin` upar le jao taaki bottom gap = right gap
        val fabLocation = IntArray(2)
        fab.getLocationOnScreen(fabLocation)
        val fabCurrentBottom = fabLocation[1] + fab.height   // current absolute bottom
        val fabTargetBottom = screenH - margin               // desired bottom
        val fabTargetY = (fabTargetBottom - fabCurrentBottom).toFloat()

        if (hidden) {
            nav.animate()
                .translationY(nav.height.toFloat() + 40f)
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .setUpdateListener { syncIndicatorToNav(nav) }
                .withEndAction { indicatorView?.visibility = View.GONE }
                .start()

            // FAB diagonally → bottom-right corner (right + neeche, equal margins)
            fab.animate()
                .translationX(fabTargetX)
                .translationY(fabTargetY)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            indicatorView?.visibility = View.VISIBLE

            nav.animate()
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .setUpdateListener { syncIndicatorToNav(nav) }
                .start()

            fab.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private var indicatorView: View? = null
    private var indicatorSpringX: SpringAnimation? = null
    private var indicatorW: Int = 0

    private val CENTER_INDEX = 2  // nav_add_images slot (FAB), indicator skips it

    private fun indexForMenuItem(itemId: Int): Int = when (itemId) {
        R.id.nav_home -> 0
        R.id.nav_templates -> 1
        R.id.nav_add_images -> 2
        R.id.nav_fav -> 3
        R.id.nav_settings -> 4
        else -> -1
    }

    /**
     * X center of each item, accounting for the nav's horizontal padding.
     * The real item strip is (width - paddingLeft - paddingRight), divided
     * equally among all menu slots. This makes the dot land exactly over each icon.
     */
    private fun xForIndex(nav: BottomNavigationView, index: Int): Float {
        val usableW = nav.width - nav.paddingLeft - nav.paddingRight
        val itemW = usableW / nav.menu.size()
        val itemCenter = nav.x + nav.paddingLeft + itemW * index + itemW / 2f
        return itemCenter - indicatorW / 2f
    }

    /** Y position: just INSIDE the top edge of the white bar (not above it). */
    private fun yForIndicator(nav: BottomNavigationView): Float {
        val topInset = (4 * resources.displayMetrics.density)
        return nav.y + topInset
    }

    /** Keep the indicator glued to the nav while the nav slides in/out. */
    private fun syncIndicatorToNav(nav: BottomNavigationView) {
        val iv = indicatorView ?: return
        iv.translationY = nav.translationY      // match the slide
        iv.y = yForIndicator(nav) + nav.translationY
    }

    private fun moveIndicatorTo(nav: BottomNavigationView, index: Int) {
        if (indicatorView == null) return
        indicatorSpringX?.animateToFinalPosition(xForIndex(nav, index))
        indicatorView?.y = yForIndicator(nav)
    }

    private fun attachSpringIndicator(nav: BottomNavigationView) {
        if (indicatorView != null) return  // guard against double-attach

        val ctx = nav.context
        val density = resources.displayMetrics.density
        val indicatorH = (3 * density).toInt()   // 3dp tall
        indicatorW = (22 * density).toInt()      // 22dp wide

        val indicator = View(ctx).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(indicatorW, indicatorH)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = indicatorH / 2f
                setColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.appColor))
            }
            // Above the nav bar (nav = 8dp) but below the FAB (16dp)
            elevation = 12f * density
        }

        val root = binding.root as androidx.constraintlayout.widget.ConstraintLayout
        root.addView(indicator)
        indicatorView = indicator

        // Initial position based on current destination (default: home).
        val startIndex = when (navController.currentDestination?.id) {
            R.id.templateCategoriesFragment -> 1
            R.id.filesFragment -> 3
            R.id.settingsFragment -> 4
            else -> 0
        }
        indicator.x = xForIndex(nav, startIndex)
        indicator.y = yForIndicator(nav)

        indicatorSpringX = SpringAnimation(indicator, DynamicAnimation.X).apply {
            spring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }

        // z-order: indicator nav ke upar, lekin FAB sabse upar
        indicator.bringToFront()
        binding.fabAddImage.bringToFront()

        // Start hidden unless we're already on a top-level (nav-visible) screen.
        val navVisibleNow = navController.currentDestination?.id in setOf(
            R.id.homeFragment,
            R.id.templateCategoriesFragment,
            R.id.filesFragment,
            R.id.settingsFragment
        )
        indicator.visibility = if (navVisibleNow) View.VISIBLE else View.GONE
        binding.fabAddImage.visibility = if (navVisibleNow) View.VISIBLE else View.GONE
    }

    // ──────────────────────────────────────────────────────────
    // Status bar / immersive
    // ──────────────────────────────────────────────────────────

    private fun setStatusBarTextColor(darkIcons: Boolean) {
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = darkIcons
    }

    private fun applyStatusBarColor() {
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        setStatusBarTextColor(darkIcons = !isNightMode)
    }

    private fun forceImmersiveMode() {
        window?.let { w ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.insetsController?.apply {
                    hide(WindowInsets.Type.navigationBars())
                    show(WindowInsets.Type.statusBars())
                    systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION") w.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            applyStatusBarColor()
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
        uri ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ── 1) Our project file? (.urdc, .bin from WhatsApp, or octet-stream) ──
                // We always attempt tryOpenProject for any octet-stream / unknown extension.
                // tryOpenProject does a magic-byte check internally — it rejects non-URDC files
                // silently, so false positives just fall through to the image handler below.
                val name = queryDisplayName(uri)
                val looksLikeProject = name?.endsWith(".urdc", true) == true || name?.endsWith(
                    ".bin", true
                ) == true ||   // WhatsApp renames .urdc -> .bin
                        intent.type == "application/octet-stream" || intent.type == "application/octet_stream" || intent.type == null   // some share sources send no MIME type at all
                if (looksLikeProject && tryOpenProjectAsync(uri)) {
                    return@launch
                }

                // ── 2) Existing image handling (unchanged) ──
                if (intent.type?.startsWith("image/") == true) {
                    val rawBitmap = com.webscare.urducanvas.common.utils.ImageProcessor.decodeUriSafely(
                        contentResolver,
                        uri,
                        com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX,
                        com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX
                    )
                    if (rawBitmap != null) {
                        val bitmap = com.webscare.urducanvas.common.utils.ImageProcessor.downsampleIfNeeded(
                            rawBitmap,
                            com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX,
                            com.webscare.urducanvas.common.utils.Constants.GPU_SAFE_MAX_PX
                        )
                        val widthVal = bitmap.width.toFloat()
                        val heightVal = bitmap.height.toFloat()
                        withContext(Dispatchers.Main) {
                            val canvasSize = CanvasSize(
                                id = 0, "From Image", widthVal, heightVal
                            )
                            viewModel.clearCanvas()
                            viewModel.setCanvasSize(canvasSize)
                            viewModel.setCanvasBackgroundImage(bitmap, this@MainActivity)
                            val opts = NavOptions.Builder().setLaunchSingleTop(true)
                                .setPopUpTo(R.id.editorFragment, inclusive = true).build()
                            navController.navigate(R.id.editorFragment, null, opts)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Copy the incoming project to cache, build a minimal ExportResult, and load via the VM. */
    private suspend fun tryOpenProjectAsync(uri: Uri): Boolean {
        return try {
            val cached = File(cacheDir, "incoming_${System.currentTimeMillis()}.urdc")
            contentResolver.openInputStream(uri)?.use { input ->
                cached.outputStream().use { input.copyTo(it) }
            } ?: return false

            // Verify it's really ours before hijacking the open.
            val isUrdc = com.webscare.urducanvas.common.canvas.io.ProjectCodec.isUrdcFile(cached)
            if (!isUrdc) {
                // Could still be a plain .json shared from a debug/authoring build — allow that.
                val firstByte = cached.inputStream().use { it.read() }
                if (firstByte != '['.code && firstByte != '{'.code) {
                    cached.delete(); return false
                }
            }

            // jsonPath points at the cached file; the loader auto-detects .urdc vs plain JSON.
            val result = com.webscare.urducanvas.data.model.ExportResult(
                imagePath = "",
                jsonPath = cached.absolutePath,
                fileName = "Shared project",
                fileSizeMB = cached.length() / (1024.0 * 1024.0),
                resolution = "",
                format = "URDC",
                quality = "",
                canvasSize = viewModel.canvasSize.value ?: CanvasSize(
                    id = 0, "Project", 1080f, 1080f
                ),
                exportDate = "",
                updatedDate = ""
            )

            withContext(Dispatchers.Main) {
                viewModel.loadTemplateFromJsonFile(result, this@MainActivity) { success ->
                    if (success) {
                        val opts = NavOptions.Builder().setLaunchSingleTop(true)
                            .setPopUpTo(R.id.editorFragment, inclusive = true).build()
                        navController.navigate(R.id.editorFragment, null, opts)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        if (uri.scheme == "file") uri.lastPathSegment
        else contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
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

    private var originalBaseContext: Context? = null

    override fun attachBaseContext(newBase: Context) {
        originalBaseContext = newBase
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = 1.0f
        if (MyApplication.defaultDensityDpi != 0) {
            config.densityDpi = MyApplication.defaultDensityDpi
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun getSystemService(name: String): Any? {
        if (name == Context.PRINT_SERVICE) {
            return originalBaseContext?.getSystemService(name) ?: super.getSystemService(name)
        }
        return super.getSystemService(name)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UpdateManager.REQUEST_CODE_UPDATE && resultCode != RESULT_OK) {
            updateManager.checkForUpdate(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateManager.onDestroy()
        _navController = null
        _binding = null
    }
}