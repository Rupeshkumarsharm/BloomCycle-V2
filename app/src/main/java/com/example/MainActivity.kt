package com.example

import android.os.Bundle
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
    enableEdgeToEdge()

    lifecycle.addObserver(
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !isChangingConfigurations) {
          mainViewModel.lockAppNow()
        }
      }
    )

    setContent {
      BloomCycleApp(viewModel = mainViewModel)
    }
  }
}
