package com.example.aiadventchallenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
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
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
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
}