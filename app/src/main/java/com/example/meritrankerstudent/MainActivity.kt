package com.example.meritrankerstudent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation3.runtime.NavKey
import com.example.meritrankerstudent.data.coordinator.PracticeGenerationCoordinator
import com.example.meritrankerstudent.theme.MeritRankerStudentTheme
import com.example.meritrankerstudent.ui.update.MandatoryUpdateScreen
import com.example.meritrankerstudent.util.notification.PracticeNotificationManager
import com.example.meritrankerstudent.util.review.PlayReviewCoordinator
import com.example.meritrankerstudent.util.review.PlayUpdateCoordinator
import com.example.meritrankerstudent.util.review.PlayUpdateState

class MainActivity : ComponentActivity() {

    private lateinit var updateCoordinator: PlayUpdateCoordinator
    private var pendingNavKey by mutableStateOf<NavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateCoordinator = PlayUpdateCoordinator.getInstance(this)

        // 1. Record meaningful active app session locally (deduplicated)
        PlayReviewCoordinator.getInstance(this).recordAppSession()

        // 2. Check for mandatory Google Play updates (suppresses reviews if update is pending)
        updateCoordinator.checkForAppUpdate(this)

        // 3. Restore any in-flight active practice generation tasks across process restarts
        lifecycleScope.launch {
            val authRepo = com.example.meritrankerstudent.data.repository.DefaultAuthRepository()
            val currentUserId = authRepo.getCurrentUserId() ?: "authenticated_student_user"
            PracticeGenerationCoordinator.getInstance(this@MainActivity).restoreActiveGenerationsOnStartup(currentUserId)
        }

        // 4. Handle notification deep link
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            MeritRankerStudentTheme {
                val updateState by updateCoordinator.updateState.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (updateState == PlayUpdateState.UPDATE_REQUIRED) {
                        MandatoryUpdateScreen(
                            onUpdateNow = { updateCoordinator.retryUpdateFlow(this@MainActivity) },
                            onCloseApp = { finishAffinity() }
                        )
                    } else {
                        MainNavigation(initialNavKey = pendingNavKey)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val navTarget = intent.getStringExtra(PracticeNotificationManager.EXTRA_NAV_TARGET)
        val testId = intent.getStringExtra(PracticeNotificationManager.EXTRA_TEST_ID)
        val mode = intent.getStringExtra(PracticeNotificationManager.EXTRA_PRACTICE_MODE) ?: "QUIZ"

        if (navTarget == PracticeNotificationManager.NAV_TARGET_QUESTION_PLAYER && !testId.isNullOrEmpty()) {
            pendingNavKey = QuestionPlayer(mode = mode, id = testId)
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume any in-progress developer-triggered update
        updateCoordinator.resumeInProgressUpdate(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateCoordinator.onActivityResult(requestCode, resultCode)
    }
}
