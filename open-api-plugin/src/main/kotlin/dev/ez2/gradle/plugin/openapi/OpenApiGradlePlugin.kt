package dev.ez2.gradle.plugin.openapi

import dev.ez2.gradle.plugin.openapi.tasks.ApiGatewayTagProcessTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory

const val GROUP = "EZ2 Dev OpenAPI"

class OpenApiGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply("org.openapi.generator")

        val customTagsToCopy = listOf(
            "customtag/x-ez2dev-apigateway.json",
            "customtag/x-ez2dev-security.json",
            "customtag/x-ez2dev-xhandler.json",
            "customtag/x-ez2dev-apigateway-option.json"
        )

        val templatesToCopy = listOf(
            "kotlin-spring/apiInterface.mustache",
            "kotlin-spring/buildGradle-sb3-Kts.mustache",
            "kotlin-spring/dataClass.mustache",
            "kotlin-spring/model.mustache",
            "kotlin-spring/oneof_model.mustache",
            "kotlin-spring/sealedClass.mustache",
            "kotlin-spring/settingsGradle.mustache"
        )

        val buildDirectory = target.layout.buildDirectory
        val combinedDirRelativePath = "generated/openapi/combined"
        val combinedRelativePath = "${combinedDirRelativePath}/openapi.json"
        val preProcessedDirRelativePath = "generated/openapi/preprocessed"
        val preProcessedApiFileRelativePath = "$preProcessedDirRelativePath/openapi.json"
        val postProcessedDirRelativePath = "generated/openapi/postprocessed"
        val postProcessedApiFileRelativePath = "$postProcessedDirRelativePath/openapi.json"
        val apiTemplateDirName = "api-template"
        val apiTemplateDirPath = buildDirectory.dir(apiTemplateDirName).get().asFile.toPath()
        val customTagDirPath = apiTemplateDirPath.resolve("customtag")
        val kotlinSpringTemplateDirPath = apiTemplateDirPath.resolve("kotlin-spring")

        target.tasks.register("generateCombinedApi", GenerateTask::class.java) {
            it.group = GROUP
            it.dependsOn("clean")
            it.generatorName.set("openapi")
            it.inputSpec.set("${target.projectDir}/src/main/resources/api/api.json")
            val combinedApiJson = buildDirectory.dir(combinedDirRelativePath).get().asFile.path
            it.outputDir.set(combinedApiJson)
        }

        target.tasks.register("copyApiTemplateFiles") {
            it.group = GROUP
            it.dependsOn("generateCombinedApi")
            it.doFirst {
                apiTemplateDirPath.createDirectory()
                copyFiles(
                    customTagsToCopy,
                    customTagDirPath
                )
                copyFiles(
                    templatesToCopy,
                    kotlinSpringTemplateDirPath
                )
            }
        }

        target.tasks.register("processApiGatewayTags", ApiGatewayTagProcessTask::class.java) {
            it.group = GROUP
            it.dependsOn("copyApiTemplateFiles")
            it.target.set(buildDirectory.file(preProcessedApiFileRelativePath).get())
            it.source.set(buildDirectory.file(combinedRelativePath).get())
            it.gatewayTemplate.set(File("$customTagDirPath/x-ez2dev-apigateway.json"))
            it.securityTemplate.set(File("$customTagDirPath/x-ez2dev-security.json"))
            it.xHandlerTemplate.set(File("$customTagDirPath/x-ez2dev-xhandler.json"))
            it.optionMethodTemplate.set(File("$customTagDirPath/x-ez2dev-apigateway-option.json"))
        }

        target.tasks.register("postProcessApiGatewayTags", Copy::class.java) { task ->
            task.group = GROUP
            task.dependsOn("processApiGatewayTags")
            task.from(buildDirectory.file(preProcessedApiFileRelativePath).get().asFile)
            task.into(buildDirectory.dir(postProcessedDirRelativePath).get().asFile)
            task.filter {
                it.replace("\${AppName}", System.getProperty("common.app.name"))
                    .replace("\${ApiVersion}", target.version.toString())
            }
        }

        val commonJavaPackagePrefix: String = System.getProperty("common.java.package.prefix")
        val generateApiServerStubTask = target.tasks.register("generateApiServerStub", GenerateTask::class.java) {
            it.group = GROUP
            it.dependsOn("postProcessApiGatewayTags")
            val outputDirPath = target.project.projectDir.resolve("api-server-stub")
            it.generatorName.set("kotlin-spring")
            it.inputSpec.set(buildDirectory.file(postProcessedApiFileRelativePath).get().asFile.path)
            it.outputDir.set(outputDirPath.toString())
            it.templateDir.set(kotlinSpringTemplateDirPath.toString())
            it.apiPackage.set("${commonJavaPackagePrefix}.server.web.api")
            it.modelPackage.set("${commonJavaPackagePrefix}.server.web.model")
            it.configOptions.set(
                mapOf(
                    "serializationLibrary" to "jackson",
                    "interfaceOnly" to "true",
                    "documentationProvider" to "none",
                    "useBeanValidation" to "true",
                    "enumPropertyNaming" to "UPPERCASE",
                    "annotationLibrary" to "none",
                    "skipDefaultInterface" to "true",
                    "useSpringBoot3" to "true",
                    "groupId" to commonJavaPackagePrefix,
                    "artifactId" to "api",
                    "artifactVersion" to target.version.toString()
                )
            )
        }

        val openApiClientDirectory = buildDirectory.dir("generated/openapi/client")
        target.tasks.register("generateAngularClient", GenerateTask::class.java) {
            it.group = GROUP
            it.dependsOn("postProcessApiGatewayTags")
            val outputDirPath = openApiClientDirectory.get().asFile.path
            it.generatorName.set("typescript-angular")
            it.inputSpec.set(buildDirectory.file(postProcessedApiFileRelativePath).get().asFile.path)
            it.outputDir.set(outputDirPath)
            it.configOptions.set(
                mapOf(
                    "npmName" to "server-api-client",
                    "npmVersion" to target.version.toString()
                )
            )
        }

        target.tasks.register("npmInstall", Exec::class.java) {
            it.group = GROUP
            it.dependsOn("buildApi", "generateAngularClient")
            it.workingDir(openApiClientDirectory)
            it.commandLine("npm", "install")
        }

        target.tasks.register("npmRunBuild", Exec::class.java) {
            it.group = GROUP
            it.dependsOn("npmInstall")
            it.workingDir(openApiClientDirectory)
            it.commandLine("npm", "run", "build")
        }

        target.tasks.register("buildClient", Exec::class.java) {
            it.group = GROUP
            it.dependsOn("npmRunBuild")
            it.workingDir(buildDirectory.dir("generated/openapi/client/dist"))
            it.commandLine(
                "npm",
                "pack",
                "--pack-destination",
                buildDirectory.dir("generated/openapi").get().asFile.path
            )
        }

        target.tasks.getByName("build") {
            it.group = GROUP
            it.dependsOn(generateApiServerStubTask, "buildClient")
        }

        target.tasks.register("cleanApiServerStub") {
            it.group = GROUP
            it.doLast {
                target.projectDir.resolve("api-server-stub/src").deleteRecursively()
                target.projectDir.resolve("api-server-stub/.openapi-generator").deleteRecursively()
            }
        }

        target.tasks.getByName("clean") {
            it.group = GROUP
            it.dependsOn("cleanApiServerStub")
        }

        target.project("api-server-stub").afterEvaluate {
            target.project("api-server-stub").tasks.getByName("compileKotlin") {
                it.dependsOn(generateApiServerStubTask)
            }
        }
    }

    /**
     * Copy files to build directory for further processing
     */
    private fun copyFiles(sourceFiles: List<String>, targetDirectory: Path) {
        targetDirectory.createDirectory()
        sourceFiles.forEach {
            val resourceAsStream = javaClass.classLoader.getResourceAsStream(it)
            if (resourceAsStream != null) {
                Files.copy(resourceAsStream, targetDirectory.resolve(it.substringAfter("/")))
            }
        }
    }


}
