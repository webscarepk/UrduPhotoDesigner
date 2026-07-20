package com.webscare.urducanvas

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.webscare.urducanvas.di.BillingManager
import com.webscare.ads.WebsCareAds
import javax.inject.Inject
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {

    @Inject
    lateinit var billingManager: BillingManager

    companion object {
        var defaultDensityDpi: Int = 0
    }

    override fun onCreate() {
        super.onCreate()
        
        // Hilt field injection completes before this, so billingManager is ready
        WebsCareAds.init(this) {
            testMode = BuildConfig.DEBUG
            premiumCheck = { billingManager.isSubscribed.value }
            
            // Connect remote config / local overrides
            homeNativeEnabled = true
            categoriesNativeEnabled = true
            templatesNativeEnabled = true
            emptyStateNativeEnabled = true
            exportInterstitialEnabled = true
            exportSuccessNativeEnabled = true
            settingsBannerEnabled = true
            rewardedBgRemovalEnabled = true
            appOpenSplashEnabled = true
        }

        // Enable process-lifecycle warm-starts for App Open ads
        WebsCareAds.enableAutoAppOpen(BuildConfig.AD_APP_OPEN_SPLASH, minBackgroundSeconds = 30)
        WebsCareAds.preloadAppOpen(this, BuildConfig.AD_APP_OPEN_SPLASH)

        com.webscare.urducanvas.common.utils.SvgLoader.init(this)
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        defaultDensityDpi = resources.displayMetrics.densityDpi
        suppressOemTouchBugs()
    }

    private fun suppressOemTouchBugs() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isOemTouchRecycleBug(throwable)) {
                FirebaseCrashlytics.getInstance().recordException(throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Returns true if this is an OEM framework bug in touch target recycling —
     * identifiable by the fact that none of our own code appears in the stack.
     * Covers Xiaomi/MIUI, Huawei, Honor, OPPO/ColorOS, and any future OEM variant.
     */
    private fun isOemTouchRecycleBug(t: Throwable): Boolean {
        if (t !is IllegalStateException) return false
        if (t.message != "already recycled once") return false
        return t.stackTrace.none { frame ->
            frame.className.startsWith("com.webscare.urducanvas")
        }
    }
}