plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.familyfinance"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.9.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.14.11")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

ktlint {
    version.set("1.8.0")
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

tasks.test {
    useJUnitPlatform()
}

// Prints a BCrypt hash for the one-off OWNER bootstrap insert (see
// db/bootstrap-owner.sql). Uses the test runtime classpath so the helper
// never ships in the application jar.
tasks.register<JavaExec>("printPasswordHash") {
    group = "application"
    description = "Prints a BCrypt hash: ./gradlew printPasswordHash -Ppassword='...'"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.familyfinance.crm.tools.PasswordHashPrinterKt")
    args = listOfNotNull(project.findProperty("password") as String?)
}
