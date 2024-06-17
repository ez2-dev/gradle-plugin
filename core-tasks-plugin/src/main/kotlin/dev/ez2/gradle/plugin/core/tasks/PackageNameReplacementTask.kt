package dev.ez2.gradle.plugin.core.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.nio.file.Files


abstract class PackageNameReplacementTask : DefaultTask() {

    @get:Input
    @set:Option(option = "currentPackage", description = "Current package name")
    abstract var currentPackage: String

    @get:Input
    @set:Option(option = "newPackage", description = "New package name")
    abstract var newPackage: String

    @get:InputFiles
    @set:Option(option = "sourceDirectories", description = "Source directories to change package name")
    abstract var sourceDirectories: List<String>

    @TaskAction
    fun process() {
        sourceDirectories.forEach { path ->
            moveFiles(path, currentPackage, newPackage)
        }
    }

    private fun moveFiles(baseFolder: String, fromPackage: String, toPackage: String) {
        val fromFolder = fromPackage.replace(".", "/")
        val toFolder = toPackage.replace(".", "/")
        if (!File("$baseFolder/$fromFolder").exists()) {
            println("Skip $baseFolder/$fromFolder because it does not exist")
            return
        }

        File("$baseFolder/$fromFolder").copyRecursively(File("$baseFolder/$toFolder"), true)

        File("$baseFolder/$toFolder").walk().forEach {
            if (it.isFile) {
                val content = it.readText().replace(fromPackage, toPackage)
                it.writeText(content)
            }
        }

        toFolder.commonPrefixWith(fromFolder).let { commonPrefix ->
            fromFolder.substring(commonPrefix.length).substringBefore("/").let { nextFolder ->
                File("$baseFolder/$commonPrefix$nextFolder").deleteRecursively()
            }
        }
    }
}
