import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    id("org.springframework.boot") version "2.7.1"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.spring") version "1.7.10"
    kotlin("plugin.jpa") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
    id("org.flywaydb.flyway") version "8.3.0"
}

group = "com.mrkirby153"
version = "2.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://repo.mrkirby153.com/repository/maven-public/")
}

buildscript {
    dependencies {
        // Needed so flyway can pick up mysql
        classpath("org.flywaydb:flyway-mysql:8.5.13")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    implementation("org.flywaydb:flyway-mysql")
    implementation("org.flywaydb:flyway-core")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("mysql:mysql-connector-java")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("com.mrkirby153:bot-core:5.0-SNAPSHOT")
    implementation("com.mrkirby153:interaction-menus:1.0-SNAPSHOT")
    implementation("net.dv8tion:JDA:5.0.0-beta.1")

    implementation("me.mrkirby153:KirbyUtils-Common:3.4-SNAPSHOT")


    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
        exclude(module = "mockito-junit-jupiter")
    }
    testImplementation("org.springframework.amqp:spring-rabbit-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val defaultProps by lazy {
    val embeddedPropFile = file("src/main/resources/application.properties")
    if (embeddedPropFile.exists()) {
        Properties().apply { load(embeddedPropFile.inputStream()) }
    } else {
        Properties()
    }
}

val overriddenProps by lazy {
    val propFile = file("config/application.properties")
    if (propFile.exists()) {
        Properties().apply { load(propFile.inputStream()) }
    } else {
        Properties()
    }
}

inline fun <reified T> getProperty(key: String): T? {
    val raw = overriddenProps[key] ?: defaultProps[key]
    if (raw is T) {
        return raw
    } else {
        throw IllegalArgumentException("Could not cast $key to desired type")
    }
}

flyway {
    url = getProperty("spring.datasource.url")
    user = getProperty("spring.datasource.username")
    password = getProperty("spring.datasource.password")
}