plugins {
    kotlin("jvm")
}

group = "org.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":simple-client"))
    implementation(project(":core"))

    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlin.logging)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j)

    testImplementation(kotlin("test"))
    testImplementation(libs.jimfs)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}