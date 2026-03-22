import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
}

val docIndexAssetsDir = layout.buildDirectory.dir("generated/docIndexAssets")

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

    val weatherKey = props.getProperty("APIFY_API_KEY", "").trim()
    buildConfigField("String", "APIFY_API_KEY", "\"$weatherKey\"")

    val mcpCustomUrl = props.getProperty("MCP_CUSTOM_SERVER_URL", "").trim()
    buildConfigField("String", "MCP_CUSTOM_SERVER_URL", "\"$mcpCustomUrl\"")

    val ollamaHost = props.getProperty("OLLAMA_HOST", "").trim()
    buildConfigField("String", "OLLAMA_HOST", "\"$ollamaHost\"")
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
  kotlin {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
    }
  }
  sourceSets {
    getByName("main") {
      assets.srcDir(docIndexAssetsDir)
    }
  }
  packaging {
    resources {
      // Избегаем конфликтов Java-ресурсов (OkHttp logging-interceptor vs jspecify)
      excludes += "META-INF/versions/**"
    }
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
  implementation(libs.androidx.navigation.compose)
  // MCP Kotlin SDK client and Ktor HTTP client (used only for MCP)
  implementation(libs.mcp.kotlin.sdk.client)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.sse)
  implementation(libs.retrofit)
  implementation(libs.retrofit.gson)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.gson)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.datastore.preferences)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

val docIndexSqlite = rootProject.project(":doc-index").layout.buildDirectory.file("doc_index.sqlite")

val prepareDocIndexAssets by tasks.registering(Copy::class) {
  description = "Build doc index via Ollama and copy doc_index.sqlite into generated assets."
  group = "doc index"
  from(docIndexSqlite)
  into(docIndexAssetsDir)
  rename { "doc_index.sqlite" }
  dependsOn(":doc-index:buildDocIndex")
}

afterEvaluate {
  tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareDocIndexAssets)
  }
}