package org.dark.keyboard.suggestions

/**
 * Interfaz de re-ranking para la Fase 3.
 *
 * El re-ranker toma candidatos del trie (ya con Frequency Overlay)
 * y los re-ordena usando contexto profundo (GPT-2 / neural).
 *
 * Patrón GBoard/SwiftKey:
 *   Trie → top-20 candidatos → ReRanker → top-3 final
 *
 * Implementaciones:
 *   NoOpReRanker   — Fase 1+2, no hace nada (0 overhead)
 *   TFLiteReRanker — Fase 3, usa GPT-2 para scoring contextual
 */
interface ReRanker {

    /**
     * Re-rankea una lista de candidatos dado el contexto.
     *
     * @param candidates Lista de palabras candidatas (ya ordenadas por Fase 1+2)
     * @param context    Texto antes del cursor (para contexto)
     * @return Lista re-ordenada — el primero es el más probable
     */
    fun rerank(candidates: List<String>, context: String): List<String>

    /**
     * Inicializa el re-ranker (carga modelo, etc.)
     */
    fun initialize() {}

    /**
     * Libera recursos.
     */
    fun close() {}

    val name: String
    val isAvailable: Boolean
}

/**
 * No-op: devuelve los candidatos sin modificar.
 * Activo cuando TFLite no está disponible (APK liviano).
 */
class NoOpReRanker : ReRanker {
    override val name = "NoOp (Phase 1+2 only)"
    override val isAvailable = true
    override fun rerank(candidates: List<String>, context: String) = candidates
}
