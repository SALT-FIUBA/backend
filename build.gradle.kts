import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    application
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("io.kauth.MainKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

val ktorVersion = "2.3.6"
val logbackVersion = "1.4.14"
val eventStoreClientVersion = "5.2.0"
val serializationVersion = "1.6.0"
val dateTimeVersion = "0.4.1"
val micrometer = "1.12.2"
val mqttClient = "0.4.6"
val exposedVersion = "0.50.1"
val hikaricpVersion = "5.0.1"

dependencies {

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("io.ktor:ktor-server-metrics:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometer")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("com.eventstore:db-client-java:$eventStoreClientVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$dateTimeVersion")

    implementation("com.auth0:java-jwt:4.4.0")

    implementation("io.github.davidepianca98:kmqtt-common-jvm:$mqttClient")
    implementation("io.github.davidepianca98:kmqtt-client-jvm:$mqttClient")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")

    implementation("com.zaxxer:HikariCP:$hikaricpVersion")
    //implementation("org.jooq:jooq:3.19.6")
    implementation("org.postgresql:postgresql:42.7.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
    compilerOptions {
        //jvmTarget.set(JvmTarget.JVM_17)fdddd
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
