package com.example.aiadventchallenge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aiadventchallenge.ui.agent.AgentScreen
import com.example.aiadventchallenge.ui.chat.ChatScreen
import com.example.aiadventchallenge.ui.discussion.DiscussionScreen
import com.example.aiadventchallenge.ui.home.HomeScreen
import com.example.aiadventchallenge.ui.modelcomparison.ModelComparisonScreen
import com.example.aiadventchallenge.ui.temperature.TemperatureScreen
import com.example.aiadventchallenge.ui.theme.AIAdventChallengeTheme

class MainActivity : ComponentActivity() {

  private val requestNotificationPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ -> }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    requestNotificationPermissionIfNeeded()
    setContent {
      AIAdventChallengeTheme {
        val navController = rememberNavController()
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
          ) {
            composable("home") {
              HomeScreen(
                modifier = Modifier.fillMaxSize(),
                onNavigate = { navController.navigate(it) }
              )
            }
            composable("chat") {
              ChatScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() }
              )
            }
            composable("discussion") {
              DiscussionScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() }
              )
            }
            composable("temperature") {
              TemperatureScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() }
              )
            }
            composable("modelcomparison") {
              ModelComparisonScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() }
              )
            }
            composable("agent") {
              AgentScreen(
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() }
              )
            }
          }
        }
      }
    }
  }

  /** Запрос разрешения на уведомления (API 33+) при старте, чтобы напоминания отображались. */
  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }
}