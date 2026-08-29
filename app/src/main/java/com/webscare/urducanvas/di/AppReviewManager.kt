package com.webscare.urducanvas.di

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreAPI
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.KEY_EXPORT_COUNT_FOR_REVIEW
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.KEY_LAST_REVIEW_REQUEST_TIMESTAMP
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppReviewManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: PreferenceDataStoreAPI
) {

    companion object {
        private const val TAG = "AppReviewManager"
        const val DEBUG_MODE = false
        private const val COOLDOWN_DAYS = 7L
        private const val COOLDOWN_MS = COOLDOWN_DAYS * 24 * 60 * 60 * 1000L
    }

    private val reviewManager: ReviewManager = if (DEBUG_MODE) {
        FakeReviewManager(context)
    } else {
        ReviewManagerFactory.create(context)
    }

    /**
     * Checks eligibility and triggers Google Play In-App Review if conditions are met:
     * - First trigger: on the 1st successful design export.
     * - Subsequent triggers: on every 2nd export thereafter (3rd, 5th, etc.) provided at least 7 days have elapsed.
     */
    fun requestReviewIfEligible(activity: Activity, onComplete: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentCount = dataStore.getPreference(KEY_EXPORT_COUNT_FOR_REVIEW, 0).first()
                val newCount = currentCount + 1
                dataStore.putPreference(KEY_EXPORT_COUNT_FOR_REVIEW, newCount)

                val lastPromptTime = dataStore.getPreference(KEY_LAST_REVIEW_REQUEST_TIMESTAMP, 0L).first()
                val now = System.currentTimeMillis()
                val isCooldownPassed = (now - lastPromptTime) >= COOLDOWN_MS

                val isEligible = (newCount == 1) || (newCount > 1 && (newCount % 2 != 0) && isCooldownPassed)

                if (!isEligible) {
                    Log.d(TAG, "Not eligible for in-app review. Count: $newCount, CooldownPassed: $isCooldownPassed")
                    withContext(Dispatchers.Main) { onComplete?.invoke() }
                    return@launch
                }

                Log.d(TAG, "Requesting in-app review flow. Export count: $newCount")
                withContext(Dispatchers.Main) {
                    reviewManager.requestReviewFlow().addOnCompleteListener { requestTask ->
                        if (requestTask.isSuccessful) {
                            val reviewInfo = requestTask.result
                            reviewManager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                                CoroutineScope(Dispatchers.IO).launch {
                                    dataStore.putPreference(KEY_LAST_REVIEW_REQUEST_TIMESTAMP, System.currentTimeMillis())
                                }
                                onComplete?.invoke()
                            }
                        } else {
                            Log.w(TAG, "In-app review requestFlow failed", requestTask.exception)
                            onComplete?.invoke()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in requestReviewIfEligible", e)
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
        }
    }

    /**
     * Explicit trigger for the "Rate The App" action (e.g. from Settings).
     * Attempts the in-app review dialog first; invokes [onFallback] if the flow cannot be started.
     */
    fun launchExplicitReview(activity: Activity, onFallback: () -> Unit) {
        reviewManager.requestReviewFlow().addOnCompleteListener { requestTask ->
            if (requestTask.isSuccessful) {
                val reviewInfo = requestTask.result
                reviewManager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener { flowTask ->
                    if (!flowTask.isSuccessful) {
                        onFallback()
                    }
                }
            } else {
                onFallback()
            }
        }
    }
}
