plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.25.0"
}

group = "io.pipecrafts.prototype"
version = "0.0.1-SNAPSHOT"
description = "prototype-integrations"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName = "java"
    library = "restclient"
    inputSpec = layout.projectDirectory.file("src/main/resources/openapi/profile-service.yaml")
    outputDir = generatedOpenApiDir
    apiPackage = "io.pipecrafts.prototype.integration.demo.profile.generated.api"
    modelPackage = "io.pipecrafts.prototype.integration.demo.profile.generated.model"
    invokerPackage = "io.pipecrafts.prototype.integration.demo.profile.generated.invoker"
    generateApiTests = false
    generateModelTests = false
    configOptions = mapOf(
        "useJakartaEe" to "true",
        "dateLibrary" to "java8",
        "openApiNullable" to "false",
    )
}

sourceSets {
    main {
        java.srcDir(generatedOpenApiDir.map { it.dir("src/main/java") })
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    // The generated OpenAPI client targets Jackson 2 (com.fasterxml.jackson); Boot 4 itself runs on Jackson 3.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("jakarta.annotation:jakarta.annotation-api")
    // Explicit version: resilience4j-bom 2.4.0 omits this module (resilience4j#2421).
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.wiremock.integrations:wiremock-spring-boot:4.4.2")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
