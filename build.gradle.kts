plugins {
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.serialization") version "1.9.24" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.android.application") version "8.4.2" apply false
}

allprojects {
    group = "com.gymbuddy"
    version = "0.0.0-pr0"
}

tasks.register("pr0Check") {
    dependsOn(":domain:test", ":app:test")
    doLast {
        exec {
            commandLine("python", "scripts/check_living_spec.py")
        }
    }
}
