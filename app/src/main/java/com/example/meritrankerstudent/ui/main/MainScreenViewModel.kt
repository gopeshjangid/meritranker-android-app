package com.example.meritrankerstudent.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MainTab {
    DOUBT,    // AI Tutor
    PRACTICE, // Practice
    PROGRESS, // Progress
    PROFILE   // Profile (Rightmost tab)
}

class MainScreenViewModel : ViewModel() {
    private val _currentTab = MutableStateFlow(MainTab.DOUBT)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }
}
