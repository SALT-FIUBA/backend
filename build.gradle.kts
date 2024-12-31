
plugins {
    application
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "1.9.0"
    id("io.ktor.plugin") version "3.0.0"
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

ktor {
    fatJar {
        archiveFileName.set("salt-server.jar")
    }
    docker {
        localImageName.set("salt-server")
        imageTag.set("0.0.1-preview-rc1")
    }
}

val ktorVersion = "3.0.3"
val logbackVersion = "1.4.14"
val eventStoreClientVersion = "5.2.0"
val serializationVersion = "1.7.3"
val dateTimeVersion = "0.4.1"
val micrometer = "1.12.2"
val mqttClient = "0.4.6"
val exposedVersion = "0.50.1"
val hikaricpVersion = "5.0.1"
val hivemqMQTT = "1.3.3"
val pulsarVersion = "4.0.0"

dependencies {

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("org.apache.pulsar:pulsar-client:$pulsarVersion")

    implementation("com.hivemq:hivemq-mqtt-client:$hivemqMQTT")

    implementation("io.micrometer:micrometer-registry-prometheus:$micrometer")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("com.eventstore:db-client-java:$eventStoreClientVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$dateTimeVersion")

    implementation("com.auth0:java-jwt:4.4.0")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")

    implementation("com.zaxxer:HikariCP:$hikaricpVersion")

    implementation("org.postgresql:postgresql:42.7.2")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from("src/main/resources/META-INF/services/io.grpc.LoadBalancerProvider") {
        into("META-INF/services")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

kotlin {
    jvmToolchain(8)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
