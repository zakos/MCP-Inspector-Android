package com.example.mcpinspector.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun schema(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

class SchemaFormTest {

    @Test
    fun `string property becomes a string field`() {
        val fields = buildFormFields(schema("""{"type":"object","properties":{"query":{"type":"string","description":"search text"}}}"""))

        assertEquals(1, fields.size)
        val field = fields[0] as FormField.StringField
        assertEquals("query", field.key)
        assertEquals("search text", field.description)
        assertEquals(false, field.required)
    }

    @Test
    fun `required list marks fields as required`() {
        val fields = buildFormFields(
            schema("""{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""")
        )

        assertTrue(fields[0].required)
    }

    @Test
    fun `string with enum becomes an enum field`() {
        val fields = buildFormFields(
            schema("""{"type":"object","properties":{"mode":{"type":"string","enum":["a","b","c"]}}}""")
        )

        val field = fields[0] as FormField.EnumField
        assertEquals(listOf("a", "b", "c"), field.options)
    }

    @Test
    fun `number and integer become number fields`() {
        val fields = buildFormFields(
            schema(
                """{"type":"object","properties":{
                    "count":{"type":"integer"},
                    "ratio":{"type":"number"}
                }}"""
            )
        )

        val count = fields.first { it.key == "count" } as FormField.NumberField
        val ratio = fields.first { it.key == "ratio" } as FormField.NumberField
        assertTrue(count.isInteger)
        assertTrue(!ratio.isInteger)
    }

    @Test
    fun `boolean becomes a boolean field with default`() {
        val fields = buildFormFields(
            schema("""{"type":"object","properties":{"enabled":{"type":"boolean","default":true}}}""")
        )

        val field = fields[0] as FormField.BooleanField
        assertEquals(true, field.default)
    }

    @Test
    fun `object and array types fall back to raw json`() {
        val fields = buildFormFields(
            schema(
                """{"type":"object","properties":{
                    "tags":{"type":"array"},
                    "meta":{"type":"object"}
                }}"""
            )
        )

        assertTrue(fields.all { it is FormField.RawJsonField })
    }

    @Test
    fun `missing schema yields no fields`() {
        assertTrue(buildFormFields(null).isEmpty())
    }

    @Test
    fun `valid values build a typed arguments object`() {
        val fields = buildFormFields(
            schema(
                """{"type":"object","properties":{
                    "name":{"type":"string"},
                    "count":{"type":"integer"},
                    "enabled":{"type":"boolean"}
                },"required":["name"]}"""
            )
        )

        val result = buildToolArguments(fields, mapOf("name" to "hello", "count" to "3", "enabled" to "true"))

        val valid = result as FormValidationResult.Valid
        assertEquals("hello", valid.arguments["name"]!!.jsonPrimitive.content)
        assertEquals("3", valid.arguments["count"]!!.jsonPrimitive.content)
        assertEquals("true", valid.arguments["enabled"]!!.jsonPrimitive.content)
    }

    @Test
    fun `missing required value is invalid`() {
        val fields = buildFormFields(schema("""{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}"""))

        val result = buildToolArguments(fields, emptyMap())

        val invalid = result as FormValidationResult.Invalid
        assertEquals(listOf("name"), invalid.invalidKeys)
    }

    @Test
    fun `unparseable number is invalid`() {
        val fields = buildFormFields(schema("""{"type":"object","properties":{"count":{"type":"integer"}}}"""))

        val result = buildToolArguments(fields, mapOf("count" to "not-a-number"))

        assertTrue(result is FormValidationResult.Invalid)
    }

    @Test
    fun `malformed raw json fallback value is invalid`() {
        val fields = buildFormFields(schema("""{"type":"object","properties":{"meta":{"type":"object"}}}"""))

        val result = buildToolArguments(fields, mapOf("meta" to "{not json"))

        assertTrue(result is FormValidationResult.Invalid)
    }

    @Test
    fun `optional empty field is skipped without error`() {
        val fields = buildFormFields(schema("""{"type":"object","properties":{"note":{"type":"string"}}}"""))

        val result = buildToolArguments(fields, mapOf("note" to ""))

        val valid = result as FormValidationResult.Valid
        assertTrue(valid.arguments.isEmpty())
    }
}
