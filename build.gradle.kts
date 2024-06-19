
plugins {
    application
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("io.ktor.plugin") version "2.3.11"
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
        imageTag.set("0.0.1-preview")
    }
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

    implementation("com.hivemq:hivemq-mqtt-client:1.2.1")

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

tasks.jar {
    from("src/main/resources/META-INF/services/io.grpc.LoadBalancerProvider") {
        into("META-INF/services")
    }
}

kotlin {
    jvmToolchain(8)
    compilerOptions {
        //jvmTarget.set(JvmTarget.JVM_17)fdddd
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
