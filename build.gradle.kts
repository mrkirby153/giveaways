import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    id("org.springframework.boot") version "3.1.7"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("plugin.jpa") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    kotlin("plugin.allopen") version "1.9.22"
    id("org.flywaydb.flyway") version "10.6.0"
}

group = "com.mrkirby153"
version = "2.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://repo.mrkirby153.com/repository/maven-public/")
}

buildscript {
    dependencies {
        // Needed so flyway can pick up mysql
        classpath("org.flywaydb:flyway-mysql:10.6.0")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("org.flywaydb:flyway-mysql")
    implementation("org.flywaydb:flyway-core")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("mysql:mysql-connector-java:8.0.33")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

    implementation("com.mrkirby153:bot-core:7.1-SNAPSHOT")
    implementation("com.mrkirby153:interaction-menus:2.0-SNAPSHOT")
    implementation("net.dv8tion:JDA:5.0.0-beta.20")

    implementation("me.mrkirby153:KirbyUtils-Common:7.0-SNAPSHOT")
    implementation("me.mrkirby153:KirbyUtils-Spring:7.0-SNAPSHOT")
    implementation("io.kubernetes:client-java:15.0.1")


    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
        exclude(module = "mockito-junit-jupiter")
    }
    testImplementation("org.springframework.amqp:spring-rabbit-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xcontext-receivers")
        jvmTarget = "21"
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

allOpen {
    annotation("com.mrkirby153.giveaways.jpa.LazyEntity")
}