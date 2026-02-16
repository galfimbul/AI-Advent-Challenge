package com.example.aiadventchallenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.aiadventchallenge.ui.chat.ChatScreen
import com.example.aiadventchallenge.ui.theme.AIAdventChallengeTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AIAdventChallengeTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          ChatScreen(modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }
}