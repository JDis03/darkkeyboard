package org.dark.keyboard.shared.serialization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dark.keyboard.shared.model.LayoutModel

/**
 * Handles JSON serialization and deserialization of keyboard layouts.
 * 
 * This serializer is shared between the web editor (Kotlin/JS) and Android app (Kotlin/JVM),
 * ensuring identical JSON output on both platforms.
 */
object LayoutSerializer {
    
    /**
     * JSON configuration:
     * - prettyPrint: Make JSON human-readable (easier to debug)
     * - ignoreUnknownKeys: Forward compatibility (future versions can add fields)
     * - encodeDefaults: Include default values explicitly (ensures completeness)
     */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Serialize a LayoutModel to JSON string.
     * 
     * @param layout The layout to serialize
     * @return JSON string representation
     * 
     * Example output:
     * ```json
     * {
     *   "name": "My Layout",
     *   "version": 1,
     *   "width": 1080,
     *   "height": 720,
     *   "keys": [...]
     * }
     * ```
     */
    fun toJson(layout: LayoutModel): String {
        return json.encodeToString(layout)
    }
    
    /**
     * Deserialize a JSON string to LayoutModel.
     * 
     * @param jsonString The JSON string to parse
     * @return LayoutModel instance
     * @throws kotlinx.serialization.SerializationException if JSON is invalid
     * 
     * Example input:
     * ```json
     * {
     *   "name": "My Layout",
     *   "version": 1,
     *   "width": 1080,
     *   "height": 720,
     *   "keys": []
     * }
     * ```
     */
    fun fromJson(jsonString: String): LayoutModel {
        return json.decodeFromString(jsonString)
    }
}
