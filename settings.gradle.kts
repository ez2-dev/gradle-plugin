plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "ez2-dev-gradle-plugin"

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
include("core-tasks-plugin")
include("open-api-plugin")
