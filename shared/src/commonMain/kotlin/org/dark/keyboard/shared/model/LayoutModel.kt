package org.dark.keyboard.shared.model

import kotlinx.serialization.Serializable

/**
 * Represents a complete keyboard layout with all keys and metadata.
 * 
 * This model is shared between the web editor (Kotlin/JS) and Android app (Kotlin/JVM),
 * ensuring consistent serialization and validation across platforms.
 */
@Serializable
data class LayoutModel(
    /** User-friendly name for this layout (e.g., "My Custom QWERTY") */
    val name: String,
    
    /** Schema version for future compatibility (currently 1) */
    val version: Int = 1,
    
    /** Total keyboard width in pixels */
    val width: Int,
    
    /** Total keyboard height in pixels */
    val height: Int,
    
    /** List of all keys in this layout */
    val keys: List<KeyModel>
) {
    companion object {
        /** Current schema version */
        const val CURRENT_VERSION = 1
        
        /** Maximum allowed keys per layout (performance limit) */
        const val MAX_KEYS = 100
        
        /** Minimum key size in pixels (usability constraint) */
        const val MIN_KEY_SIZE = 60
    }
}
