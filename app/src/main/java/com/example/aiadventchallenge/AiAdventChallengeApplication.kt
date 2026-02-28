package com.example.aiadventchallenge

import android.app.Application
import androidx.room.Room
import com.example.aiadventchallenge.data.agent.AppDatabase
import com.example.aiadventchallenge.data.agent.MIGRATION_1_2

class AiAdventChallengeApplication : Application() {

  lateinit var database: AppDatabase
    private set

  override fun onCreate() {
    super.onCreate()
    database = Room.databaseBuilder(this, AppDatabase::class.java, "agent_db")
      .addMigrations(MIGRATION_1_2)
      .build()
  }
}
