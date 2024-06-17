plugins {
    signing
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
    alias(libs.plugins.kotlin.jvm)
}

group = "dev.ez2.gradle"
version = "1.0.0"

gradlePlugin {
    plugins {
        website = "https://github.com/ez2-dev/gradle-plugin"
        vcsUrl = "https://github.com/ez2-dev/gradle-plugin.git"
        create("OpenAPI") {
            id = "dev.ez2.gradle.plugin.openapi"
            implementationClass = "dev.ez2.gradle.plugin.openapi.OpenApiGradlePlugin"
            displayName = "Plugin to process OpenAPI definitions for AWS API Gateway"
            description =
                "a plugin that generates a single OpenAPI definition from multiple files and customizations"
            tags = listOf("OpenAPI", "AWS", "API Gateway", "EZ2Dev")
        }
    }
}

dependencies {
    implementation(libs.bundles.jackson.kotlin)
    compileOnly("org.openapitools:openapi-generator-gradle-plugin:7.5.0")
}

kotlin {
    jvmToolchain(17)
}

