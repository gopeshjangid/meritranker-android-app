package com.example.meritrankerstudent

import android.app.Application
import android.util.Log
import com.amplifyframework.api.aws.AWSApiPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.configuration.AmplifyOutputs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeritRankerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.meritrankerstudent.observability.AppObservability.init(this)
        initAmplify()
    }

    private fun initAmplify() {
        synchronized(lock) {
            val current = _initStatus.value
            if (current is InitStatus.Success) {
                Log.d(TAG, "Amplify is already initialized.")
                return
            }
            try {
                _initStatus.value = InitStatus.Loading
                Amplify.addPlugin(AWSCognitoAuthPlugin())
                Amplify.addPlugin(AWSApiPlugin())
                Amplify.configure(AmplifyOutputs(R.raw.amplify_outputs), applicationContext)
                _initStatus.value = InitStatus.Success
                Log.i(TAG, "Amplify initialized successfully.")
            } catch (error: Exception) {
                _initStatus.value = InitStatus.Failure(error)
                Log.e(TAG, "Could not initialize Amplify", error)
            }
        }
    }

    companion object {
        private const val TAG = "MeritRankerApp"
        private val lock = Any()

        var instance: MeritRankerApplication? = null
            private set

        sealed interface InitStatus {
            data object Idle : InitStatus
            data object Loading : InitStatus
            data object Success : InitStatus
            data class Failure(val exception: Throwable) : InitStatus
        }

        private val _initStatus = MutableStateFlow<InitStatus>(InitStatus.Idle)
        val initStatus: StateFlow<InitStatus> = _initStatus.asStateFlow()

        fun setInitStatusForTesting(status: InitStatus) {
            _initStatus.value = status
        }
    }
}
