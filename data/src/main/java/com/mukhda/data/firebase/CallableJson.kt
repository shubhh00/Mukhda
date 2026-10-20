package com.mukhda.data.firebase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** Reads callable results into DTOs; fields the server adds later are ignored. */
val CallableJson = Json { ignoreUnknownKeys = true }

/**
 * A callable result (maps, lists, strings, numbers, booleans and nulls, as the Firebase SDK
 * hands them over) as JSON, so it can be decoded into typed DTOs.
 */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is List<*> -> JsonArray(map { it.toJsonElement() })
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> throw IllegalArgumentException("Unexpected value in a callable result: ${this::class.simpleName}")
}

inline fun <reified T> decodeCallable(data: Any?): T = CallableJson.decodeFromJsonElement(data.toJsonElement())
