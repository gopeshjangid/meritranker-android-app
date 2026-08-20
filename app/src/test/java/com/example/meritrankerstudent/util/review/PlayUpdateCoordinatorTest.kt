package com.example.meritrankerstudent.util.review

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*

class PlayUpdateCoordinatorTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockAppUpdateManager: AppUpdateManager
    private lateinit var mockActivity: Activity
    private lateinit var coordinator: PlayUpdateCoordinator
    private lateinit var inMemoryStorage: PlayReviewStorage
    private lateinit var fakeReviewCoordinator: PlayReviewCoordinator
    private val prefData = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        prefData.clear()
        mockContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)
        mockAppUpdateManager = mock(AppUpdateManager::class.java)
        mockActivity = mock(Activity::class.java)

        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockContext.applicationContext).thenReturn(mockContext)

        `when`(mockPrefs.getBoolean(anyString(), anyBoolean())).thenAnswer {
            prefData[it.arguments[0] as String] as? Boolean ?: it.arguments[1] as Boolean
        }
        `when`(mockPrefs.getLong(anyString(), anyLong())).thenAnswer {
            prefData[it.arguments[0] as String] as? Long ?: it.arguments[1] as Long
        }
        `when`(mockPrefs.getInt(anyString(), anyInt())).thenAnswer {
            prefData[it.arguments[0] as String] as? Int ?: it.arguments[1] as Int
        }

        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenAnswer {
            prefData[it.arguments[0] as String] = it.arguments[1] as Boolean
            mockEditor
        }
        `when`(mockEditor.putLong(anyString(), anyLong())).thenAnswer {
            prefData[it.arguments[0] as String] = it.arguments[1] as Long
            mockEditor
        }
        `when`(mockEditor.putInt(anyString(), anyInt())).thenAnswer {
            prefData[it.arguments[0] as String] = it.arguments[1] as Int
            mockEditor
        }
        doNothing().`when`(mockEditor).apply()

        inMemoryStorage = PlayReviewStorage(mockContext)
        fakeReviewCoordinator = PlayReviewCoordinator(inMemoryStorage, FakePlayReviewManager())
        PlayReviewCoordinator.setTestInstance(fakeReviewCoordinator)

        coordinator = PlayUpdateCoordinator(mockContext, mockAppUpdateManager)
    }

    @After
    fun tearDown() {
        PlayReviewCoordinator.setTestInstance(null)
    }

    @Test
    fun testInitialState_IsClear() {
        assertEquals(PlayUpdateState.CLEAR, coordinator.updateState.value)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testCheckForUpdate_NoUpdateAvailable_TransitionsToClear() {
        val mockUpdateInfo = mock(AppUpdateInfo::class.java)
        `when`(mockUpdateInfo.updateAvailability()).thenReturn(UpdateAvailability.UPDATE_NOT_AVAILABLE)

        val mockTask = mock(Task::class.java) as Task<AppUpdateInfo>
        `when`(mockTask.addOnSuccessListener(any())).thenAnswer {
            val listener = it.arguments[0] as OnSuccessListener<AppUpdateInfo>
            listener.onSuccess(mockUpdateInfo)
            mockTask
        }
        `when`(mockTask.addOnFailureListener(any())).thenReturn(mockTask)
        `when`(mockAppUpdateManager.appUpdateInfo).thenReturn(mockTask)

        coordinator.checkForAppUpdate(mockActivity)

        assertEquals(PlayUpdateState.CLEAR, coordinator.updateState.value)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testCheckForUpdate_UpdateAvailable_ImmediateAllowed_TransitionsToUpdateRequired() {
        val mockUpdateInfo = mock(AppUpdateInfo::class.java)
        `when`(mockUpdateInfo.updateAvailability()).thenReturn(UpdateAvailability.UPDATE_AVAILABLE)
        `when`(mockUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)).thenReturn(true)

        val mockTask = mock(Task::class.java) as Task<AppUpdateInfo>
        `when`(mockTask.addOnSuccessListener(any())).thenAnswer {
            val listener = it.arguments[0] as OnSuccessListener<AppUpdateInfo>
            listener.onSuccess(mockUpdateInfo)
            mockTask
        }
        `when`(mockTask.addOnFailureListener(any())).thenReturn(mockTask)
        `when`(mockAppUpdateManager.appUpdateInfo).thenReturn(mockTask)

        coordinator.checkForAppUpdate(mockActivity)

        assertEquals(PlayUpdateState.UPDATE_REQUIRED, coordinator.updateState.value)
        verify(mockAppUpdateManager).startUpdateFlowForResult(
            eq(mockUpdateInfo),
            eq(mockActivity),
            any(AppUpdateOptions::class.java),
            eq(PlayUpdateCoordinator.REQUEST_CODE_MANDATORY_UPDATE)
        )
    }

    @Test
    fun testUserCancelsMandatoryUpdate_EnforcesUpdateRequiredGate() {
        coordinator.onActivityResult(PlayUpdateCoordinator.REQUEST_CODE_MANDATORY_UPDATE, Activity.RESULT_CANCELED)

        assertEquals(PlayUpdateState.UPDATE_REQUIRED, coordinator.updateState.value)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testCheckForUpdate_PlayFailure_NonBlockingFallback() {
        val mockTask = mock(Task::class.java) as Task<AppUpdateInfo>
        `when`(mockTask.addOnSuccessListener(any())).thenReturn(mockTask)
        `when`(mockTask.addOnFailureListener(any())).thenAnswer {
            val listener = it.arguments[0] as OnFailureListener
            listener.onFailure(Exception("Play Store connection timeout"))
            mockTask
        }
        `when`(mockAppUpdateManager.appUpdateInfo).thenReturn(mockTask)

        coordinator.checkForAppUpdate(mockActivity)

        assertEquals(PlayUpdateState.CHECK_FAILED, coordinator.updateState.value)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testRetryUpdateFlow_RequestsFreshAppUpdateInfo() {
        val mockFreshUpdateInfo = mock(AppUpdateInfo::class.java)
        `when`(mockFreshUpdateInfo.updateAvailability()).thenReturn(UpdateAvailability.UPDATE_AVAILABLE)
        `when`(mockFreshUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)).thenReturn(true)

        val mockTask = mock(Task::class.java) as Task<AppUpdateInfo>
        `when`(mockTask.addOnSuccessListener(any())).thenAnswer {
            val listener = it.arguments[0] as OnSuccessListener<AppUpdateInfo>
            listener.onSuccess(mockFreshUpdateInfo)
            mockTask
        }
        `when`(mockTask.addOnFailureListener(any())).thenReturn(mockTask)
        `when`(mockAppUpdateManager.appUpdateInfo).thenReturn(mockTask)

        coordinator.retryUpdateFlow(mockActivity)

        assertEquals(PlayUpdateState.UPDATE_IN_PROGRESS, coordinator.updateState.value)
        verify(mockAppUpdateManager).startUpdateFlowForResult(
            eq(mockFreshUpdateInfo),
            eq(mockActivity),
            any(AppUpdateOptions::class.java),
            eq(PlayUpdateCoordinator.REQUEST_CODE_MANDATORY_UPDATE)
        )
    }
}
