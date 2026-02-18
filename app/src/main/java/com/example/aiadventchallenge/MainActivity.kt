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
import com.example.aiadventchallenge.ui.chat.ChatScreen
import com.example.aiadventchallenge.ui.discussion.DiscussionScreen
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
            startDestination = "chat",
            modifier = Modifier.padding(innerPadding)
          ) {
            composable("chat") {
              ChatScreen(
                modifier = Modifier.fillMaxSize(),
                onNavigateToDiscussion = { navController.navigate("discussion") }
              )
            }
            composable("discussion") {
              DiscussionScreen(
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