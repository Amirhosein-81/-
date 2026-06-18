package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.GameViewModel
import com.example.game.GameView
import com.example.game.MenuScreen
import com.example.game.ScreenState
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
          val viewModel: GameViewModel = viewModel()
          
          if (viewModel.currentScreen == ScreenState.GAMEPLAY) {
            GameView(
              viewModel = viewModel,
              modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            )
          } else {
            MenuScreen(
              viewModel = viewModel,
              modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            )
          }
        }
      }
    }
  }
}
