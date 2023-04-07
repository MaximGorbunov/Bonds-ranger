import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    id("com.github.johnrengelman.shadow") version "8.1.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.2")
    implementation("de.vandermeer:asciitable:0.3.2")

    implementation("org.codehaus.groovy:groovy-all:3.0.13")
    implementation("org.codehaus.janino:janino:3.1.9")
    compileOnly("ch.qos.logback:logback-core:1.4.5")
    implementation("ch.qos.logback:logback-classic:1.4.5")

    implementation("io.grpc:grpc-kotlin-stub:1.3.0")
    implementation("io.github.maximgorbunov:tinkoff-grpc:0.0.2")

    testImplementation("com.fasterxml.jackson.core:jackson-core:2.6.3")
    testImplementation("com.fasterxml.jackson.core:jackson-annotations:2.6.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.6.3")
    testImplementation("com.googlecode.protobuf-java-format:protobuf-java-format:1.2")
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("cf.mgorbunov.MainKt")
}