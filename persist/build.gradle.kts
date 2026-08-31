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
    implementation(project(":domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.room:room-common:2.6.1")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    testImplementation(kotlin("test"))
}

sourceSets {
    main {
        resources.srcDir("schemas")
    }
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Room annotations + SQLite event log.
 * :persist depends on :domain. :domain does not depend on :persist.
 * DAO SQL is executed via SQLite JDBC in tests (no Android SDK).
 * The @Entity/@Dao/@Database types are the Room contract for the Android builder later.
 */
