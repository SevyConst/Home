plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "org.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.kotlinxSerialization)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}