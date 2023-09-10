import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.serialization") version "1.7.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

group = "io.github.holo314"
version = "2.0.2"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation("dev.kord:kord-core:0.8.0-M17")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.6.4")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("io.github.holo314.MainKt")
}