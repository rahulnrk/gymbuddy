import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization")
}

val localProps = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localFile.inputStream().use { localProps.load(it) }
}
fun localProp(name: String): String = localProps.getProperty(name).orEmpty()
fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.gymbuddy.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gymbuddy.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.0-pr0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "ELEVENLABS_API_KEY", "\"\"")
            buildConfigField("String", "VOICE_BACKEND_URL", quotedBuildConfig(localProp("voice.backend.url")))
        }
        getByName("debug") {
            isMinifyEnabled = false
            buildConfigField("String", "ELEVENLABS_API_KEY", quotedBuildConfig(localProp("elevenlabs.api.key")))
            buildConfigField("String", "VOICE_BACKEND_URL", quotedBuildConfig(localProp("voice.backend.url")))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    sourceSets {
        getByName("test") {
            resources.srcDir(rootProject.projectDir.resolve("tests/golden"))
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "org/sqlite/native/**"
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":persist"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
}

configurations {
    implementation {
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
}

fun assertReleaseVoiceGuard() {
    if (project.hasProperty("voiceGuardForceFail")) {
        throw GradleException(
            "Release build must not see elevenlabs.api.key in local.properties",
        )
    }
    val assemblingRelease = gradle.startParameter.taskNames.any {
        it.contains("Release", ignoreCase = true)
    }
    if (!assemblingRelease) return
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        val text = propsFile.readText()
        val hasKey = text.lineSequence().any { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("#") &&
                (trimmed.startsWith("elevenlabs.api.key=") || trimmed.startsWith("ELEVENLABS_API_KEY=")) &&
                trimmed.substringAfter("=").isNotBlank()
        }
        if (hasKey) {
            throw GradleException(
                "Release build must not see elevenlabs.api.key in local.properties",
            )
        }
    }
    val leaked = file("src/main").walkTopDown().any { it.name == "DebugVoiceCredentials.kt" }
    if (leaked) {
        throw GradleException("DebugVoiceCredentials must not be on the release classpath")
    }
}

tasks.register("assertReleaseVoiceGuard") {
    doLast { assertReleaseVoiceGuard() }
}

tasks.configureEach {
    if (name.startsWith("assembleRelease") || name.startsWith("bundleRelease")) {
        dependsOn("assertReleaseVoiceGuard")
    }
}
