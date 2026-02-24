package com.example.aiadventchallenge

import android.app.Application
import androidx.room.Room
import com.example.aiadventchallenge.data.agent.AppDatabase

class AiAdventChallengeApplication : Application() {

  lateinit var database: AppDatabase
    private set

  override fun onCreate() {
    super.onCreate()
    database = Room.databaseBuilder(this, AppDatabase::class.java, "agent_db").build()
  }
}
