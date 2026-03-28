package com.webscare.urducanvas.di

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreAPI
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants.REMIND_LATER_TIMESTAMP
import com.webscare.urducanvas.common.utils.GlobalSnackbar
import com.webscare.urducanvas.common.utils.UpdateDialog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// UpdateManager.kt
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: PreferenceDataStoreAPI
) {

    private var updateDialog: UpdateDialog? = null

    companion object {
        const val REQUEST_CODE_UPDATE = 1001
        const val DEBUG_MODE = false
    }

    private val appUpdateManager = if (DEBUG_MODE) {
        FakeAppUpdateManager(context)
    } else {
        AppUpdateManagerFactory.create(context)
    }

    fun checkForUpdate(activity: AppCompatActivity) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                if (DEBUG_MODE) {
                    (appUpdateManager as FakeAppUpdateManager).setUpdateAvailable(10)
                    fetchAndShowUpdate(activity)
                } else {
                    fetchAndShowUpdate(activity)
                }
            }
        }
    }

    private fun fetchAndShowUpdate(activity: AppCompatActivity) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                when {
                    appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                            && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                        showUpdateDialog(activity, appUpdateInfo, isForced = false)
                    }

                    appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                            && (appUpdateInfo.clientVersionStalenessDays() ?: 0) >= 7
                            && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                        showUpdateDialog(activity, appUpdateInfo, isForced = true)
                    }
                }
            }
            .addOnFailureListener { it.printStackTrace() }
    }

    private fun showUpdateDialog(
        activity: AppCompatActivity,
        appUpdateInfo: AppUpdateInfo,
        isForced: Boolean
    ) {
        updateDialog = UpdateDialog(
            context = activity,
            onUpdateNow = { startUpdate(activity, appUpdateInfo, isForced) },
            onRemindLater = if (isForced) null else ({
                CoroutineScope(Dispatchers.IO).launch {
                    dataStore.putPreference(REMIND_LATER_TIMESTAMP, System.currentTimeMillis())
                }
            }),
            isCancelable = !isForced
        )
        updateDialog?.show()
    }

    private fun startUpdate(
        activity: AppCompatActivity,
        appUpdateInfo: AppUpdateInfo,
        isForced: Boolean
    ) {
        val updateType = if (isForced) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            updateType,
            activity,
            REQUEST_CODE_UPDATE
        )
    }

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> currentActivity?.runOnUiThread { showRestartSnackbar() }
            InstallStatus.FAILED,
            InstallStatus.CANCELED -> currentActivity?.runOnUiThread {
                currentActivity?.let { fetchAndShowUpdate(it) }
            }

            else -> {}
        }
    }

    // Hold a weak reference to avoid leaking the Activity
    private var currentActivity: AppCompatActivity? = null

    private fun showRestartSnackbar() {
        val activity = currentActivity ?: return
        GlobalSnackbar.showSuccess(
            activity,
            message = "Update ready! Restart to apply.",
            actionText = "Restart",
            duration = Snackbar.LENGTH_INDEFINITE,
            anchor = null,
            onAction = { appUpdateManager.completeUpdate() }
        )
    }

    fun onResume(activity: AppCompatActivity) {
        currentActivity = activity
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                showRestartSnackbar()
            }
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    AppUpdateType.IMMEDIATE,
                    activity,
                    REQUEST_CODE_UPDATE
                )
            }
        }
    }

    fun onDestroy() {
        currentActivity = null
        appUpdateManager.unregisterListener(installStateListener)
    }

    fun registerListener(activity: AppCompatActivity) {
        currentActivity = activity
        appUpdateManager.registerListener(installStateListener)
    }
}