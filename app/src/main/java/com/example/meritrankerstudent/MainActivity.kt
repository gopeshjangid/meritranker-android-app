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
import androidx.compose.ui.Modifier
import com.example.meritrankerstudent.theme.MeritRankerStudentTheme
import com.example.meritrankerstudent.ui.update.MandatoryUpdateScreen
import com.example.meritrankerstudent.util.review.PlayReviewCoordinator
import com.example.meritrankerstudent.util.review.PlayUpdateCoordinator
import com.example.meritrankerstudent.util.review.PlayUpdateState

class MainActivity : ComponentActivity() {

    private lateinit var updateCoordinator: PlayUpdateCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateCoordinator = PlayUpdateCoordinator.getInstance(this)

        // 1. Record meaningful active app session locally (deduplicated)
        PlayReviewCoordinator.getInstance(this).recordAppSession()

        // 2. Check for mandatory Google Play updates (suppresses reviews if update is pending)
        updateCoordinator.checkForAppUpdate(this)

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
                        MainNavigation()
                    }
                }
            }
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
