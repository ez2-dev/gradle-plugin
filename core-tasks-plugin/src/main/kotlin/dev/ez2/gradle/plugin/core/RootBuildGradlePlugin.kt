package dev.ez2.gradle.plugin.core

import dev.ez2.gradle.plugin.core.tasks.PackageNameReplacementTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete

const val GROUP = "EZ2 Dev Core"

class RootBuildGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val projectCDK = target.project("cdk")
        val projectCommon = target.project("common")
        val projectWebApp = target.project("web-app")
        val projectApi = target.project("api")
        val projectServer = target.project("server")
        val projectLambdas = target.project("lambdas")

        /*
         * Backend Build and Deploy
         */
        val lambdaCdkAssetsPath = "${projectCDK.projectDir}/assets/lambdas"
        val serverCdkAssetsPath = "${projectCDK.projectDir}/assets/server"
        val apiCdkAssetsPath = "${projectCDK.projectDir}/assets/api"
        //clean up lambdas
        target.tasks.register("cleanupLambdasBeforeBuild", Delete::class.java) {
            it.group = GROUP
            it.delete(lambdaCdkAssetsPath)
        }

        //clean up server
        target.tasks.register("cleanupServerBeforeBuild", Delete::class.java) {
            it.group = GROUP
            it.delete(serverCdkAssetsPath)
        }

        //clean up api
        target.tasks.register("cleanupApiBeforeBuild", Delete::class.java) {
            it.group = GROUP
            it.delete(apiCdkAssetsPath)
        }

        target.tasks.register("copyApiDefinition", Copy::class.java) {
            it.group = GROUP
            it.dependsOn(projectApi.tasks.getByName("build"), "cleanupApiBeforeBuild")
            it.from(projectApi.layout.buildDirectory.file("generated/openapi/postprocessed/openapi.json"))
            it.into(apiCdkAssetsPath)
        }

        target.tasks.register("copyServerArtifact", Copy::class.java) {
            it.group = GROUP
            projectServer.tasks.getByName("compileKotlin")
                .dependsOn(projectApi.tasks.getByName("generateApiServerStub"))
            it.dependsOn(projectServer.tasks.getByName("buildZip"), "cleanupServerBeforeBuild")
            it.from(projectServer.layout.buildDirectory.dir("distributions"))
            it.into(serverCdkAssetsPath)
        }

        target.tasks.register("copyLambdasArtifact", Copy::class.java) {
            it.group = GROUP
            it.dependsOn(projectLambdas.tasks.getByName("buildZip"), "cleanupLambdasBeforeBuild")
            it.from(projectLambdas.layout.buildDirectory.dir("distributions"))
            it.into(lambdaCdkAssetsPath)
        }

        target.tasks.register("buildBackendCDK") {
            it.group = GROUP
            val buildBackendCDKTask = projectCDK.tasks.getByName("buildBackendCDK")
            projectCDK.tasks.getByName("compileKotlin").dependsOn(
                target.tasks.getByName("copyApiDefinition"),
                target.tasks.getByName("copyServerArtifact"),
                target.tasks.getByName("copyLambdasArtifact")
            )
            it.dependsOn(buildBackendCDKTask)
        }

        /*
         * Webapp Build and Deploy
         */
        val webappCdkAssetsPath = "${projectCDK.projectDir}/assets/webapp"
        target.tasks.register("cleanupWebappCDKBeforeBuild", Delete::class.java) {
            it.group = GROUP
            it.delete(webappCdkAssetsPath)
        }

        target.tasks.register("cleanupGeneratedFolderBeforeBuild", Delete::class.java) {
            it.group = GROUP
            it.delete("${projectWebApp.projectDir}/generated/")
        }

        target.tasks.register("copyServerApiClient", Copy::class.java) {
            it.group = GROUP
            it.dependsOn(projectApi.tasks.getByName("build"), "cleanupGeneratedFolderBeforeBuild")
            it.from(projectApi.layout.buildDirectory.file("generated/openapi/server-api-client-${projectApi.version}.tgz"))
            it.into("${projectWebApp.projectDir}/generated/")
            it.rename { "server-api-client.tgz" }
        }

        target.tasks.register("copyWebappArtifact", Copy::class.java) {
            val webappBuildTask = projectWebApp.tasks.getByName("build")
            webappBuildTask.dependsOn(target.tasks.getByName("copyServerApiClient"))
            it.dependsOn("cleanupWebappCDKBeforeBuild", webappBuildTask)

            it.from("${projectWebApp.projectDir}/dist/${System.getProperty("common.app.name")}")
            it.into(webappCdkAssetsPath)
        }

        target.tasks.register("buildWebappCDK") {
            it.group = GROUP
            it.dependsOn("copyWebappArtifact", projectCDK.tasks.getByName("buildWebAppCDK"))
        }

        /*
         * DevOps Build and Deploy
         */
        target.tasks.register("buildDevOpsCDK") {
            it.group = GROUP
            it.dependsOn(projectCDK.tasks.getByName("buildDevOpsCDK"))
        }

        /*
         * Build all CDKs
         */
        target.tasks.getByName("build") {
            it.group = GROUP
            it.dependsOn("buildBackendCDK", "buildWebappCDK", "buildDevOpsCDK")
        }

        target.tasks.register("replacePackageName", PackageNameReplacementTask::class.java){
            it.group = GROUP
            it.currentPackage = "dev.ez2"
            it.sourceDirectories = listOf(
                "${projectCDK.projectDir}/src/main/kotlin",
                "${projectServer.projectDir}/src/main/kotlin",
                "${projectCommon.projectDir}/src/main/kotlin",
                "${projectLambdas.projectDir}/src/main/kotlin",
                "${projectCDK.projectDir}/src/test/kotlin",
                "${projectServer.projectDir}/src/test/kotlin",
                "${projectCommon.projectDir}/src/test/kotlin",
                "${projectLambdas.projectDir}/src/test/kotlin"
            )
        }
    }
}
