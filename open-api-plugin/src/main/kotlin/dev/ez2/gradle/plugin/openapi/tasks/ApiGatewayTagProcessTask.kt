package dev.ez2.gradle.plugin.openapi.tasks

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


abstract class ApiGatewayTagProcessTask : DefaultTask() {

    companion object {
        private val OM: ObjectMapper = jacksonObjectMapper()
        private val PATTERN = "\\{(\\w+)}".toRegex()
    }

    @get:InputFile
    abstract val source: RegularFileProperty

    @get:InputFile
    abstract val gatewayTemplate: RegularFileProperty

    @get:InputFile
    abstract val securityTemplate: RegularFileProperty

    @get:InputFile
    abstract val xHandlerTemplate: RegularFileProperty

    @get:InputFile
    abstract val optionMethodTemplate: RegularFileProperty

    @get:OutputFile
    abstract val target: RegularFileProperty


    @TaskAction
    fun process() {
        val openApiJson = OM.readTree(source.get().asFile)
        val apiGatewayTemplateJson = OM.readTree(gatewayTemplate.get().asFile)
        val securityTemplateJson = OM.readTree(securityTemplate.get().asFile)
        val xHanlderJson = OM.readTree(xHandlerTemplate.get().asFile)
        val pathsJson = openApiJson.get("paths")
        pathsJson.flatMap { it.asIterable() }
            .forEach {
                val methodObjectNode = (it as ObjectNode)
                val ez2devApiGatewayTagJsonNode = methodObjectNode.get("x-ez2dev-apigateway")
                if (ez2devApiGatewayTagJsonNode != null) {
                    methodObjectNode.remove("x-ez2dev-apigateway")
                    methodObjectNode.set<JsonNode>("x-amazon-apigateway-integration", apiGatewayTemplateJson)
                    if (ez2devApiGatewayTagJsonNode.asBoolean()) {
                        methodObjectNode.set<JsonNode>("security", securityTemplateJson)
                        methodObjectNode.set<JsonNode>("x-handler-args", xHanlderJson)
                    }
                }
            }


        val optionMethodTemplateJson = OM.readTree(optionMethodTemplate.get().asFile)
        pathsJson.fieldNames().forEach { pathName ->
            val pathObjectNode = pathsJson.get(pathName) as ObjectNode
            val originalOptionMethodObjectNode = (pathObjectNode.get("options") as? ObjectNode)
            val optionMethodObjectNode = optionMethodTemplateJson.deepCopy<ObjectNode>()
            pathObjectNode.set<JsonNode>("options", optionMethodObjectNode)

            val pathParameterNames = PATTERN.findAll(pathName).map { it.groups[1]?.value }


            //get existing parameters field from get post put delete patch method
            val parameterArrayNodeFromOtherMethods = if (pathObjectNode.get("get") != null) {
                (pathObjectNode.get("get") as ObjectNode).get("parameters") as? ArrayNode
            } else if (pathObjectNode.get("post") != null) {
                (pathObjectNode.get("post") as ObjectNode).get("parameters") as? ArrayNode
            } else if (pathObjectNode.get("put") != null) {
                (pathObjectNode.get("put") as ObjectNode).get("parameters") as? ArrayNode
            } else if (pathObjectNode.get("delete") != null) {
                (pathObjectNode.get("delete") as ObjectNode).get("parameters") as? ArrayNode
            } else if (pathObjectNode.get("patch") != null) {
                (pathObjectNode.get("patch") as ObjectNode).get("parameters") as? ArrayNode
            } else {
                null
            }

            val parametersArrayNode = OM.createArrayNode()
            optionMethodObjectNode.set<ObjectNode>("parameters", parametersArrayNode)
            if (parameterArrayNodeFromOtherMethods != null) {
                parameterArrayNodeFromOtherMethods.filter {
                    pathParameterNames.contains(it.get("name").asText())
                }.forEach {
                    parametersArrayNode.add(it)
                }
            } else {
                pathParameterNames.map {
                    val pathParamObjectNode = OM.createObjectNode()
                    pathParamObjectNode.put("name", it)
                    pathParamObjectNode.put("in", "path")
                    pathParamObjectNode.put("required", true)
                    pathParamObjectNode.put("style", "simple")
                    pathParamObjectNode.put("explode", false)
                    val schemaObjectNode = OM.createObjectNode()
                    schemaObjectNode.put("type", "string")
                    pathParamObjectNode.set<ObjectNode>("schema", schemaObjectNode)
                    pathParamObjectNode
                }.forEach {
                    parametersArrayNode.add(it)
                }
            }


            //override original option field to new option object
            originalOptionMethodObjectNode?.fieldNames()?.forEach {
                optionMethodObjectNode.set<JsonNode>(it, originalOptionMethodObjectNode.get(it))
            }
        }

        target.get().asFile.writeText(openApiJson.toPrettyString())

    }
}
