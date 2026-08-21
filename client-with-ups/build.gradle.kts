plugins {
    java
    // Produces a start script and a lib directory via installDist / distZip. Deliberately not a
    // fat jar: log4j2 discovers its plugins through Log4j2Plugins.dat, one per jar, and naive
    // shading keeps only one of them.
    application
}

group = "org.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinxSerialization)
    implementation(libs.log4j.slf4j)
    implementation(libs.log4j.core)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "org.example.ups.Main"
    applicationName = "client-with-ups"
    // Sized for a Raspberry Pi 3 B+ with 1 GB of RAM. The serial collector and one JIT tier
    // are the right trade for a process that does one text command a second.
    applicationDefaultJvmArgs = listOf("-Xmx48m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1")
}

// Mockito mocks classes by instrumenting them through a Java agent. Left to itself it attaches
// that agent to the running test JVM, which the JDK has been warning about since 21 and intends to
// forbid, so the agent is handed to the JVM at startup instead. The jar is the same one on the
// test classpath, taken without its dependencies: an agent is loaded by its own path, not resolved.
val mockitoAgent: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    mockitoAgent(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()

    val agentJar = mockitoAgent.incoming.files
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-javaagent:" + agentJar.singleFile.absolutePath)
    })
}
