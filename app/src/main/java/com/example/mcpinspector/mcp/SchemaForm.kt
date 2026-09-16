package com.example.mcpinspector.mcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** A single form field derived from a tool's `inputSchema`. */
sealed class FormField {
    abstract val key: String
    abstract val required: Boolean
    abstract val description: String?

    data class StringField(
        override val key: String,
        override val required: Boolean,
        override val description: String?,
        val default: String? = null,
    ) : FormField()

    data class EnumField(
        override val key: String,
        override val required: Boolean,
        override val description: String?,
        val options: List<String>,
        val default: String? = null,
    ) : FormField()

    data class NumberField(
        override val key: String,
        override val required: Boolean,
        override val description: String?,
        val isInteger: Boolean,
        val default: String? = null,
    ) : FormField()

    data class BooleanField(
        override val key: String,
        override val required: Boolean,
        override val description: String?,
        val default: Boolean? = null,
    ) : FormField()

    /** Fallback for `object`/`array` (and any unrecognized) schema types: raw JSON entry. */
    data class RawJsonField(
        override val key: String,
        override val required: Boolean,
        override val description: String?,
        val default: String? = null,
    ) : FormField()
}

/** Turns a tool's JSON Schema `inputSchema` into the ordered list of fields to render. */
fun buildFormFields(inputSchema: JsonObject?): List<FormField> {
    val properties = inputSchema?.get("properties") as? JsonObject ?: return emptyList()
    val requiredKeys = (inputSchema["required"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.toSet()
        ?: emptySet()

    return properties.entries.map { (key, element) ->
        val schema = element as? JsonObject ?: JsonObject(emptyMap())
        toFormField(key, schema, key in requiredKeys)
    }
}

private fun toFormField(key: String, schema: JsonObject, required: Boolean): FormField {
    val description = (schema["description"] as? JsonPrimitive)?.contentOrNull
    val type = (schema["type"] as? JsonPrimitive)?.contentOrNull
    val enumValues = (schema["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    val defaultPrimitive = schema["default"] as? JsonPrimitive

    return when {
        type == "string" && !enumValues.isNullOrEmpty() -> FormField.EnumField(
            key = key,
            required = required,
            description = description,
            options = enumValues,
            default = defaultPrimitive?.contentOrNull,
        )

        type == "string" -> FormField.StringField(
            key = key,
            required = required,
            description = description,
            default = defaultPrimitive?.contentOrNull,
        )

        type == "number" || type == "integer" -> FormField.NumberField(
            key = key,
            required = required,
            description = description,
            isInteger = type == "integer",
            default = defaultPrimitive?.contentOrNull,
        )

        type == "boolean" -> FormField.BooleanField(
            key = key,
            required = required,
            description = description,
            default = defaultPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
        )

        else -> FormField.RawJsonField(
            key = key,
            required = required,
            description = description,
            default = schema["default"]?.let { Json.encodeToString(JsonElement.serializer(), it) },
        )
    }
}

sealed interface FormValidationResult {
    data class Valid(val arguments: JsonObject) : FormValidationResult
    data class Invalid(val invalidKeys: List<String>) : FormValidationResult
}

/**
 * Converts the raw (string) values a user typed into the form back into a JSON-typed
 * `arguments` object for `tools/call`. Empty required fields, unparseable numbers/booleans
 * and invalid raw-JSON fallback entries are all reported as invalid rather than silently
 * dropped or sent wrong.
 */
fun buildToolArguments(fields: List<FormField>, rawValues: Map<String, String>): FormValidationResult {
    val invalidKeys = mutableListOf<String>()

    val arguments = buildJsonObject {
        for (field in fields) {
            val raw = rawValues[field.key]?.trim().orEmpty()
            if (raw.isEmpty()) {
                if (field.required) invalidKeys += field.key
                continue
            }

            when (field) {
                is FormField.StringField, is FormField.EnumField -> put(field.key, raw)

                is FormField.NumberField -> {
                    val number = raw.toDoubleOrNull()
                    if (number == null) {
                        invalidKeys += field.key
                    } else if (field.isInteger) {
                        put(field.key, raw.toLongOrNull() ?: number.toLong())
                    } else {
                        put(field.key, number)
                    }
                }

                is FormField.BooleanField -> {
                    val bool = raw.toBooleanStrictOrNull()
                    if (bool == null) invalidKeys += field.key else put(field.key, bool)
                }

                is FormField.RawJsonField -> {
                    val parsed = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
                    if (parsed == null) invalidKeys += field.key else put(field.key, parsed)
                }
            }
        }
    }

    return if (invalidKeys.isEmpty()) FormValidationResult.Valid(arguments) else FormValidationResult.Invalid(invalidKeys)
}
