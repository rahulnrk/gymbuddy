plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    sourceSets.test {
        resources.srcDir(rootProject.projectDir.resolve("tests/golden"))
        resources.srcDir(rootProject.projectDir.resolve("docs/living-spec/schemas"))
    }
}
