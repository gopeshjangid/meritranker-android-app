package com.example.meritrankerstudent.ui.main

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun currentTab_initiallyDoubt() = runTest {
    val viewModel = MainScreenViewModel()
    assertEquals(MainTab.DOUBT, viewModel.currentTab.value)
  }

  @Test
  fun selectTab_updatesCurrentTab() = runTest {
    val viewModel = MainScreenViewModel()
    viewModel.selectTab(MainTab.PRACTICE)
    assertEquals(MainTab.PRACTICE, viewModel.currentTab.value)
  }
}
