package org.dark.keyboard.suggestions

/**
 * Resultado de evaluación de autocorrección para una palabra tecleada.
 *
 * @param suggestion La palabra sugerida (puede ser igual a la tecleada si no hay corrección)
 * @param shouldAutoCorrect Si true, la corrección debe aplicarse automáticamente al presionar espacio
 * @param confidence Nivel de confianza de la corrección (0.0 = sin confianza, 1.0 = máxima confianza)
 */
data class AutoCorrectionCandidate(
    val suggestion: String,
    val shouldAutoCorrect: Boolean,
    val confidence: Float
)
