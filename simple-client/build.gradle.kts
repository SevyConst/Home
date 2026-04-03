plugins {
    kotlin("jvm")
}

group = "org.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.kotlin.logging)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}