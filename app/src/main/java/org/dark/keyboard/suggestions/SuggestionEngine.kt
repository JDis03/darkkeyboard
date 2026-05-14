package org.dark.keyboard.suggestions

/**
 * Interfaz abstracta del motor de sugerencias.
 *
 * Diseñada para escalar:
 *   Nivel 1 → TFLiteSuggestionEngine (modelo pequeño ~3-5MB)
 *   Nivel 2 → fine-tuned por idioma (~8-15MB)
 *   Nivel 3 → personalización on-device (aprendizaje local)
 *   Nivel 4 → modelo cuantizado grande (MobileBERT, ~50-100MB)
 *
 * El motor se puede swapear en DarkIME2 sin cambiar nada más.
 */
interface SuggestionEngine {

    /**
     * Dado el texto actual antes del cursor, retorna hasta [maxResults] sugerencias.
     * Las sugerencias pueden ser:
     *   - Completado de la palabra actual (ej: "bueno" → "buenos", "buenas")
     *   - Siguiente palabra (ej: "buenos " → "días", "tardes")
     *   - Corrección ortográfica (ej: "buenso" → "buenos")
     */
    fun getSuggestions(textBeforeCursor: String, maxResults: Int = 3): List<String>

    /**
     * Notifica al motor que el usuario aceptó una sugerencia.
     * Usado para aprendizaje on-device (Nivel 3+).
     */
    fun onSuggestionAccepted(suggestion: String, context: String) {}

    /**
     * Inicializa el motor (carga modelo, diccionario, etc).
     * Llamar en background thread.
     */
    fun initialize() {}

    /**
     * Libera recursos (modelo TFLite, etc).
     */
    fun close() {}

    /**
     * Nombre del motor para debugging y settings.
     */
    val engineName: String
}
