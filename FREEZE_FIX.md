# Fix: Teclado se Cuelga al Escribir

## Causa Identificada

**Problema:** `getAutoCorrectionCandidate()` llama a `trie.findByEditDistance()` en **cada SPACE**, iterando sobre 8000 palabras del diccionario.

**Ubicación:** `DictSuggestionEngine.kt` línea 231

```kotlin
// PROBLEMA: Esto itera 8000 palabras en cada SPACE
val candidates = trie.findByEditDistance(typed, maxDist = autocorrectMaxEdit, maxResults = 5)
```

---

## Solución 1: Caché de Autocorrect (RECOMENDADO)

Añadir caché LRU para evitar recalcular correcciones:

```kotlin
// En DictSuggestionEngine.kt, añadir:
private val autocorrectCache = object : LinkedHashMap<String, AutoCorrectionCandidate>(100, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AutoCorrectionCandidate>?): Boolean {
        return size > 100
    }
}

override fun getAutoCorrectionCandidate(typedWord: String, textBefore: String): AutoCorrectionCandidate {
    val typed = typedWord.lowercase()
    
    // Check cache first
    autocorrectCache[typed]?.let { return it }
    
    // ... resto del código existente ...
    
    // Cache result before returning
    val result = AutoCorrectionCandidate(...)
    autocorrectCache[typed] = result
    return result
}
```

**Beneficio:** Reduce cálculos de 8000 iteraciones a lookup O(1) para palabras repetidas.

---

## Solución 2: Limitar Edit Distance Search

Reducir el número de palabras evaluadas:

```kotlin
// En CompactTrie.kt, línea 114, añadir early exit:
fun findByEditDistance(typed: String, maxDist: Int = 2, maxResults: Int = 10): List<Entry> {
    // ...
    var evaluated = 0
    val maxEvaluations = 2000  // Solo evaluar primeras 2000 palabras
    
    for (entry in words) {
        if (evaluated++ > maxEvaluations) break  // ← EARLY EXIT
        
        // ... resto del código ...
    }
}
```

**Beneficio:** Reduce de 8000 a 2000 evaluaciones (4x más rápido).

---

## Solución 3: Async Autocorrect (MEJOR PERO MÁS COMPLEJO)

Mover autocorrect a coroutine para no bloquear UI:

```kotlin
// En DarkIME2.kt, línea 533:
' '.code -> {
    if (!autocorrect.isEnabled) {
        ic.commitText(" ", 1)
        updateSuggestions()
        return
    }
    
    val composing = autocorrect.getComposing()
    
    if (composing.isNotEmpty() && ::suggestionEngine.isInitialized) {
        // Commit space immediately (no blocking)
        autocorrect.onFinishComposing()
        ic.finishComposingText()
        ic.commitText(" ", 1)
        
        // Calculate autocorrect async
        imeScope.launch {
            val candidate = withContext(inferDispatcher) {
                suggestionEngine.getAutoCorrectionCandidate(composing, textBefore)
            }
            
            if (candidate.shouldAutoCorrect) {
                // Apply correction retroactively
                // (más complejo, requiere tracking de posición)
            }
        }
    }
}
```

**Beneficio:** UI nunca se bloquea, pero corrección aparece con delay.

---

## Solución 4: Deshabilitar Autocorrect Temporalmente

Mientras se implementa fix permanente:

```kotlin
// En DictSuggestionEngine.kt, línea 200:
override fun getAutoCorrectionCandidate(typedWord: String, textBefore: String): AutoCorrectionCandidate {
    // TEMPORAL: Deshabilitar autocorrect
    return AutoCorrectionCandidate(typedWord, false, 0f)
}
```

**Beneficio:** Fix inmediato, pero pierde funcionalidad.

---

## Implementación Recomendada

**Paso 1:** Implementar Solución 1 (caché) - 10 minutos  
**Paso 2:** Implementar Solución 2 (limit search) - 5 minutos  
**Paso 3:** Probar y medir performance  
**Paso 4:** Si sigue lento, implementar Solución 3 (async)  

---

## Código Completo: Solución 1 + 2

```kotlin
// DictSuggestionEngine.kt

class DictSuggestionEngine(
    private val context: Context,
    private val personalDict: PersonalDictionary? = null
) : SuggestionEngine {
    
    // ... código existente ...
    
    // NUEVO: Caché LRU para autocorrect
    private val autocorrectCache = object : LinkedHashMap<String, AutoCorrectionCandidate>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AutoCorrectionCandidate>?): Boolean {
            return size > 100
        }
    }
    
    override fun getAutoCorrectionCandidate(typedWord: String, textBefore: String): AutoCorrectionCandidate {
        if (!isReady || typedWord.length < AUTOCORRECT_MIN_WORD_LEN) {
            return AutoCorrectionCandidate(typedWord, false, 0f)
        }

        val typed = typedWord.lowercase()
        
        // NUEVO: Check cache first
        autocorrectCache[typed]?.let { 
            Timber.d("Autocorrect: cache hit for '$typed'")
            return it 
        }
        
        // Get typed word frequency (0 if not in dict)
        val typedFreq = trie.getFreq(typed)
        
        if (typedFreq > 0) {
            val result = AutoCorrectionCandidate(typedWord, false, 0f)
            autocorrectCache[typed] = result  // NUEVO: Cache result
            return result
        }

        // Safety checks
        personalDict?.let { pd ->
            val context = AutocorrectGuards.AutocorrectContext(
                isTerminalApp = false,
                shouldAutocorrect = true
            )
            if (AutocorrectGuards.shouldSkip(typedWord, context, pd)) {
                val result = AutoCorrectionCandidate(typedWord, false, 0f)
                autocorrectCache[typed] = result  // NUEVO: Cache result
                return result
            }
        }

        // Get top suggestion candidates via edit distance
        val candidates = trie.findByEditDistance(typed, maxDist = autocorrectMaxEdit, maxResults = 5)
        
        if (candidates.isEmpty()) {
            val result = AutoCorrectionCandidate(typedWord, false, 0f)
            autocorrectCache[typed] = result  // NUEVO: Cache result
            return result
        }

        // ... resto del código de confidence scoring ...
        
        val result = if (checks.isEmpty()) {
            AutoCorrectionCandidate(topCandidate.word, true, confidence)
        } else {
            AutoCorrectionCandidate(typedWord, false, 0f)
        }
        
        autocorrectCache[typed] = result  // NUEVO: Cache result
        return result
    }
}
```

```kotlin
// CompactTrie.kt

fun findByEditDistance(typed: String, maxDist: Int = 2, maxResults: Int = 10): List<Entry> {
    if (!isLoaded || typed.length < 2) return emptyList()
    
    val t = typed.lowercase()
    val results = mutableListOf<Pair<Entry, Int>>()
    
    val firstChar = t[0]
    val adjacentKeys = qwertyAdjacent[firstChar] ?: emptySet()
    val validFirstChars = setOf(firstChar) + adjacentKeys
    
    var evaluated = 0
    val maxEvaluations = 2000  // NUEVO: Limit evaluations
    
    for (entry in words) {
        if (evaluated++ > maxEvaluations) break  // NUEVO: Early exit
        
        val w = entry.word
        if (w.length < 2) continue
        if (w[0] !in validFirstChars) continue
        if (kotlin.math.abs(w.length - t.length) > 2) continue
        
        val dist = damerauLevenshtein(t, w, maxDist)
        if (dist <= maxDist && dist > 0) {
            results.add(entry to dist)
        }
    }
    
    return results
        .sortedWith(compareBy({ it.second }, { -it.first.freq }))
        .take(maxResults)
        .map { it.first }
}
```

---

## Testing

Después de aplicar fix:

```bash
# 1. Build
./gradlew assembleDebug

# 2. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Test typing speed
# Escribir rápidamente: "hola mundo esto es una prueba"
# No debería haber lag

# 4. Check logs
adb logcat | grep "Autocorrect: cache"
# Deberías ver "cache hit" para palabras repetidas
```

---

## Métricas Esperadas

**Antes del fix:**
- Primera corrección de "hola": ~5-10ms
- Segunda corrección de "hola": ~5-10ms (sin caché)
- Total: ~10-20ms por SPACE

**Después del fix:**
- Primera corrección de "hola": ~5-10ms
- Segunda corrección de "hola": ~0.1ms (caché hit)
- Total: ~0.1-10ms por SPACE (10-100x más rápido para palabras repetidas)
