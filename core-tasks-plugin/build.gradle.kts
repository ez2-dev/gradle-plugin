plugins {
    signing
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
    alias(libs.plugins.kotlin.jvm)
    id("com.vanniktech.maven.publish") version "0.29.0"
}

group = "dev.ez2.gradle"
version = "1.0.1"

gradlePlugin {
    plugins {
        website = "https://github.com/ez2-dev/gradle-plugin"
        vcsUrl = "https://github.com/ez2-dev/gradle-plugin.git"
        create("Core") {
            id = "dev.ez2.gradle.plugin.core-tasks"
            implementationClass = "dev.ez2.gradle.plugin.core.RootBuildGradlePlugin"
            displayName = "Core tasks plugin"
            description =
                "a plugin that provides core tasks for building whole project"
            tags = listOf("EZ2Dev")
        }
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
}

kotlin {
    jvmToolchain(17)
}

