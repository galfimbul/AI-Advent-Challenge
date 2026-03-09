package com.example.aiadventchallenge

import android.app.Application
import androidx.room.Room
import com.example.aiadventchallenge.data.agent.AppDatabase
import com.example.aiadventchallenge.data.agent.MIGRATION_1_2
import com.example.aiadventchallenge.data.agent.MIGRATION_2_3
import com.example.aiadventchallenge.data.agent.MIGRATION_3_4
import com.example.aiadventchallenge.data.agent.MIGRATION_4_5
import com.example.aiadventchallenge.data.agent.MIGRATION_5_6
import com.example.aiadventchallenge.data.agent.MIGRATION_6_7
import com.example.aiadventchallenge.data.agent.MIGRATION_7_8

class AiAdventChallengeApplication : Application() {

  lateinit var database: AppDatabase
    private set

  override fun onCreate() {
    super.onCreate()
    database = Room.databaseBuilder(this, AppDatabase::class.java, "agent_db")
      .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
      .build()
  }
}
