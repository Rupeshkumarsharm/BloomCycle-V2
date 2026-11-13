package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
  private val mainViewModel: MainViewModel by viewModels {
    MainViewModelFactory(applicationContext)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Protect initial loading frames and recent-app snapshots until non-sensitive screen is confirmed.
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    enableEdgeToEdge()

    lifecycle.addObserver(
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !isChangingConfigurations) {
          mainViewModel.lockAppNow(fromLifecycleStop = true)
        }
      }
    )

    setContent {
      BloomCycleApp(viewModel = mainViewModel)
    }
  }
}
