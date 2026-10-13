plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.familyfinance"
// The VERSION file is the one place the version is written; it is also the app
// image's tag. Bump it for every deploy: rebuilding a version that already ran
// overwrites its image, and with it the rollback target.
version = file("VERSION").readText().trim()

val appImage = "family-finance-app"
val keptAppImages = 4

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

    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.9.1")

    // Writes the transaction export; see "XLSX, not CSV or PDF" in the Phase 6 doc.
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    // The declarative client for the market-data API; versioned by the BOM below.
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.14.11")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        // 2025.0.x is the Spring Cloud train that pairs with Spring Boot 3.5.
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.3")
    }
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

// A fixed name, so the Dockerfile copies exactly this file rather than
// guessing between it and the `-plain` jar `build` also writes.
tasks.bootJar {
    archiveFileName.set("app.jar")
    // The manifest carries the version, but with a fixed file name nothing
    // else tells Gradle a bumped version needs a new jar.
    inputs.property("version", version)
    finalizedBy(stampComposeVersion)
}

// Compose tags the app image from VERSION in .env, the only file it reads
// variables from. Copying it there whenever the jar is built means no compose
// command needs a `VERSION=$(cat VERSION)` in front of it.
val stampComposeVersion by tasks.registering {
    val envFile = layout.projectDirectory.file(".env").asFile
    val stamp = "VERSION=$version"
    doLast {
        val lines = if (envFile.exists()) envFile.readLines() else emptyList()
        val others = lines.filterNot { it.startsWith("VERSION=") }
        if (lines != others + stamp) {
            envFile.writeText((others + stamp).joinToString(separator = "\n", postfix = "\n"))
        }
    }
}

// Keeps the newest four app images — the one running and three to roll back
// to — and removes the rest. Docker refuses to remove one a container uses.
val pruneAppImages by tasks.registering {
    doLast {
        val listing =
            ProcessBuilder("docker", "images", appImage, "--format", "{{.Tag}}")
                .redirectErrorStream(true)
                .start()
        val tags =
            listing.inputStream
                .bufferedReader()
                .readLines()
                .filter(String::isNotBlank)
        check(listing.waitFor() == 0) { "docker images failed: $tags" }
        tags.drop(keptAppImages).forEach { tag ->
            logger.lifecycle("Removing $appImage:$tag")
            ProcessBuilder("docker", "rmi", "$appImage:$tag").inheritIO().start().waitFor()
        }
    }
}
