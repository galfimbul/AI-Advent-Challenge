import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example.aiadventchallenge"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.example.aiadventchallenge"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val keyFile = rootProject.file("secret.properties")
    val fallbackFile = rootProject.file("local.properties")
    val props = Properties()
    when {
      keyFile.exists() -> keyFile.inputStream().use { props.load(it) }
      fallbackFile.exists() -> fallbackFile.inputStream().use { props.load(it) }
    }
    var apiKey = props.getProperty("OPENAI_API_KEY", "").trim()
    if (apiKey == "ваш ключ" || apiKey == "your_key" || apiKey.isEmpty()) apiKey = ""
    buildConfigField("String", "OPENAI_API_KEY", "\"$apiKey\"")
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
}

dependencies {

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.retrofit)
  implementation(libs.retrofit.gson)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.gson)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}