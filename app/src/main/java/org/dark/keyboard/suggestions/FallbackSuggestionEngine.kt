package org.dark.keyboard.suggestions

/**
 * Motor de sugerencias de fallback — funciona sin modelo TFLite.
 *
 * Estrategia:
 * 1. Frecuencia de palabras usadas por el usuario (aprendizaje local)
 * 2. Diccionario de palabras comunes en español/inglés (hardcoded pequeño)
 * 3. N-gram bigram simple basado en historial
 *
 * Se usa cuando:
 * - El modelo TFLite no está disponible
 * - El dispositivo no tiene suficiente RAM
 * - Primera instalación (sin modelo descargado aún)
 */
class FallbackSuggestionEngine : SuggestionEngine {

    override val engineName = "Fallback (n-gram local)"

    // Frecuencia de palabras del usuario — persiste entre sesiones via prefs
    private val wordFrequency = mutableMapOf<String, Int>()

    // Bigrams: "palabra_anterior" → lista de siguientes palabras con frecuencia
    private val bigrams = mutableMapOf<String, MutableMap<String, Int>>()

    // Diccionario base pequeño (palabras más comunes ES + EN)
    private val baseDict = listOf(
        // Español
        "que", "de", "no", "a", "la", "el", "es", "en", "lo", "un",
        "por", "con", "una", "su", "para", "como", "más", "pero", "sus",
        "le", "ya", "o", "este", "sí", "porque", "esta", "entre", "cuando",
        "muy", "sin", "sobre", "también", "me", "hasta", "hay", "donde",
        "quien", "desde", "todo", "nos", "durante", "todos", "uno", "les",
        "ni", "contra", "otros", "ese", "eso", "ante", "ellos", "e", "esto",
        "hola", "gracias", "buenos", "días", "tardes", "noches", "favor",
        "bien", "mal", "claro", "ok", "si", "no", "quiero", "puedo",
        "tengo", "voy", "hace", "está", "estoy", "tiene", "puede",
        // Inglés
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "it",
        "for", "not", "on", "with", "he", "as", "you", "do", "at", "this",
        "but", "his", "by", "from", "they", "we", "say", "her", "she", "or",
        "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know",
        "take", "people", "into", "year", "your", "good", "some", "could",
        "hello", "thanks", "please", "yes", "ok", "sure", "great", "sorry"
    )

    override fun getSuggestions(textBeforeCursor: String, maxResults: Int): List<String> {
        if (textBeforeCursor.isBlank()) return emptyList()

        val words = textBeforeCursor.trimEnd().split(Regex("\\s+"))
        val currentWord = words.lastOrNull() ?: return emptyList()
        val previousWord = if (words.size >= 2) words[words.size - 2].lowercase() else null

        val suggestions = mutableMapOf<String, Float>()

        // 1. Si hay espacio al final → predecir siguiente palabra
        val endsWithSpace = textBeforeCursor.endsWith(" ")
        if (endsWithSpace && previousWord != null) {
            // Bigrams del usuario
            bigrams[previousWord]?.forEach { (word, freq) ->
                suggestions[word] = (suggestions[word] ?: 0f) + freq * 3f
            }
            // Frecuencia global
            wordFrequency.forEach { (word, freq) ->
                suggestions[word] = (suggestions[word] ?: 0f) + freq * 0.5f
            }
        }

        // 2. Completar la palabra actual
        if (!endsWithSpace && currentWord.length >= 1) {
            val prefix = currentWord.lowercase()

            // Palabras del usuario que empiezan con el prefijo
            wordFrequency.forEach { (word, freq) ->
                if (word.startsWith(prefix) && word != prefix) {
                    suggestions[word] = (suggestions[word] ?: 0f) + freq * 2f
                }
            }

            // Diccionario base
            baseDict.forEach { word ->
                if (word.startsWith(prefix) && word != prefix) {
                    suggestions[word] = (suggestions[word] ?: 0f) + 1f
                }
            }
        }

        return suggestions.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    override fun onSuggestionAccepted(suggestion: String, context: String) {
        learnFromText("$context $suggestion")
    }

    /**
     * Aprende de texto escrito por el usuario.
     * Llamar con el texto antes del cursor al hacer commit.
     */
    fun learnFromText(text: String) {
        val words = text.lowercase()
            .split(Regex("[\\s.,!?;:]+"))
            .filter { it.length >= 2 && it.all { c -> c.isLetter() } }

        words.forEach { word ->
            wordFrequency[word] = (wordFrequency[word] ?: 0) + 1
        }

        // Aprender bigrams
        for (i in 0 until words.size - 1) {
            val prev = words[i]
            val next = words[i + 1]
            val bigramMap = bigrams.getOrPut(prev) { mutableMapOf() }
            bigramMap[next] = (bigramMap[next] ?: 0) + 1
        }
    }

    /**
     * Serializa el estado para persistir en SharedPreferences.
     * Formato: "palabra:frecuencia" separados por coma
     */
    fun serializeFrequency(): String =
        wordFrequency.entries
            .sortedByDescending { it.value }
            .take(500)  // máximo 500 palabras
            .joinToString(",") { "${it.key}:${it.value}" }

    /**
     * Carga estado serializado desde SharedPreferences.
     */
    fun deserializeFrequency(data: String) {
        if (data.isBlank()) return
        wordFrequency.clear()
        data.split(",").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val word = parts[0]
                val freq = parts[1].toIntOrNull() ?: 0
                if (word.isNotBlank() && freq > 0) wordFrequency[word] = freq
            }
        }
    }
}
