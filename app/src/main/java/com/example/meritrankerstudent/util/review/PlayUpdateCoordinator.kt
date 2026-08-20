package com.example.meritrankerstudent.util.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlayUpdateState {
    CHECKING,
    CLEAR,
    UPDATE_REQUIRED,
    UPDATE_IN_PROGRESS,
    CHECK_FAILED
}

/**
 * Production coordinator for Google Play Mandatory In-App Updates.
 * 
 * Rules:
 * - Update State Machine: CHECKING -> (CLEAR | UPDATE_REQUIRED | CHECK_FAILED)
 * - Immediate Update: Any confirmed newer version requires immediate update before using the app.
 * - Cancelled Update: If user cancels, state is set to UPDATE_REQUIRED, showing an unskippable barrier.
 * - Retry: "Update now" requests fresh AppUpdateInfo from Google Play.
 * - Review Precedence: In-App Reviews are strictly suppressed unless update state is CLEAR.
 */
class PlayUpdateCoordinator(
    private val context: Context,
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
) {
    companion object {
        private const val TAG = "PlayUpdateCoordinator"
        const val REQUEST_CODE_MANDATORY_UPDATE = 8801

        @Volatile
        private var instance: PlayUpdateCoordinator? = null

        fun getInstance(context: Context): PlayUpdateCoordinator {
            return instance ?: synchronized(this) {
                instance ?: PlayUpdateCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _updateState = MutableStateFlow<PlayUpdateState>(PlayUpdateState.CLEAR)
    val updateState: StateFlow<PlayUpdateState> = _updateState.asStateFlow()

    private var cachedAppUpdateInfo: AppUpdateInfo? = null

    /**
     * Checks if a mandatory update is available on Google Play.
     */
    fun checkForAppUpdate(
        activity: Activity,
        requestCode: Int = REQUEST_CODE_MANDATORY_UPDATE
    ) {
        _updateState.value = PlayUpdateState.CHECKING
        PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(true)

        try {
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                cachedAppUpdateInfo = appUpdateInfo
                val isUpdateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                val isImmediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

                if (isUpdateAvailable && isImmediateAllowed) {
                    Log.i(TAG, "Mandatory Google Play Update confirmed. Launching immediate update flow.")
                    _updateState.value = PlayUpdateState.UPDATE_REQUIRED
                    PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(true)

                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            activity,
                            AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                            requestCode
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start Google Play update flow", e)
                        _updateState.value = PlayUpdateState.UPDATE_REQUIRED
                    }
                } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    Log.i(TAG, "Developer-triggered update already in progress.")
                    _updateState.value = PlayUpdateState.UPDATE_IN_PROGRESS
                    PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(true)
                    resumeInProgressUpdate(activity, requestCode)
                } else {
                    Log.d(TAG, "No mandatory update required. App is CLEAR.")
                    _updateState.value = PlayUpdateState.CLEAR
                    PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(false)
                }
            }.addOnFailureListener { exception ->
                Log.w(TAG, "Google Play App Update check failed (non-blocking fallback): ${exception.message}")
                _updateState.value = PlayUpdateState.CHECK_FAILED
                PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(false)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error during Google Play app update check", e)
            _updateState.value = PlayUpdateState.CHECK_FAILED
            PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(false)
        }
    }

    /**
     * Re-requests a FRESH AppUpdateInfo from Google Play and restarts the update flow.
     */
    fun retryUpdateFlow(activity: Activity, requestCode: Int = REQUEST_CODE_MANDATORY_UPDATE) {
        _updateState.value = PlayUpdateState.CHECKING
        PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(true)

        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { freshAppUpdateInfo ->
                cachedAppUpdateInfo = freshAppUpdateInfo
                if (freshAppUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    freshAppUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    _updateState.value = PlayUpdateState.UPDATE_IN_PROGRESS
                    appUpdateManager.startUpdateFlowForResult(
                        freshAppUpdateInfo,
                        activity,
                        AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                        requestCode
                    )
                } else if (freshAppUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    _updateState.value = PlayUpdateState.UPDATE_IN_PROGRESS
                    resumeInProgressUpdate(activity, requestCode)
                } else {
                    _updateState.value = PlayUpdateState.CLEAR
                    PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(false)
                }
            }.addOnFailureListener {
                _updateState.value = PlayUpdateState.UPDATE_REQUIRED
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed retrying update flow", e)
            _updateState.value = PlayUpdateState.UPDATE_REQUIRED
        }
    }

    /**
     * Resumes an update already initiated by Google Play.
     */
    fun resumeInProgressUpdate(activity: Activity, requestCode: Int = REQUEST_CODE_MANDATORY_UPDATE) {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    Log.i(TAG, "Resuming developer-triggered update in progress.")
                    _updateState.value = PlayUpdateState.UPDATE_IN_PROGRESS
                    PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(true)
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                        requestCode
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed resuming in-progress update", e)
        }
    }

    /**
     * Handles activity result from Google Play update prompt.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQUEST_CODE_MANDATORY_UPDATE) {
            if (resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "Mandatory update flow cancelled or failed by user (resultCode: $resultCode). Enforcing gate.")
                _updateState.value = PlayUpdateState.UPDATE_REQUIRED
                PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(true)
            } else {
                Log.i(TAG, "Mandatory update accepted by user.")
                _updateState.value = PlayUpdateState.UPDATE_IN_PROGRESS
            }
        }
    }

    /**
     * Exposes testing hook to set state during unit testing.
     */
    internal fun setUpdateStateForTest(state: PlayUpdateState) {
        _updateState.value = state
        PlayReviewCoordinator.getInstance(context).setMandatoryUpdatePending(state != PlayUpdateState.CLEAR && state != PlayUpdateState.CHECK_FAILED)
    }
}
