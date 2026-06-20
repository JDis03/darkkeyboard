# Debug: Teclado se Cuelga al Escribir

## Síntomas
- QWERTY layout se congela al escribir
- Posiblemente después de varias teclas

## Causas Posibles

### 1. **TFLite Model Blocking (MÁS PROBABLE)**
El modelo T5 (34MB) puede estar bloqueando el thread principal.

**Verificar:**
```bash
# Ver logs en tiempo real
adb logcat -c && adb logcat | grep -E "DarkIME|TFLite|FATAL|ANR"

# Buscar ANR (Application Not Responding)
adb shell ls /data/anr/
```

**Solución temporal:**
- Deshabilitar modelo TFLite en Settings
- O reducir debounce delay

### 2. **Infinite Loop en Suggestion Engine**
Posible loop infinito en:
- `CompactTrie.lookup()` - binary search
- `CompactTrie.findByEditDistance()` - itera 8K palabras
- `BpeTokenizer.bpe()` - while(true) con break

**Verificar:**
```bash
# Ver si hay CPU spike
adb shell top -n 1 | grep org.dark.keyboard
```

### 3. **Memory Leak / OOM**
Modelo TFLite + sugerencias pueden consumir mucha RAM.

**Verificar:**
```bash
# Ver memoria
adb shell dumpsys meminfo org.dark.keyboard
```

### 4. **Deadlock en synchronized(lock)**
TFLiteReRanker usa `synchronized(lock)` que podría causar deadlock.

**Verificar logs:**
```bash
adb logcat | grep "T5 inference"
```

---

## Diagnóstico Paso a Paso

### Paso 1: Capturar Logs Durante Freeze
```bash
# Terminal 1: Logs en tiempo real
adb logcat -c
adb logcat -v time | tee freeze_log.txt

# Terminal 2: Escribir en el teclado hasta que se cuelgue
# Presionar Ctrl+C en Terminal 1 cuando se cuelgue
```

### Paso 2: Analizar Logs
```bash
# Buscar errores
grep -E "FATAL|Exception|Error|ANR" freeze_log.txt

# Buscar última actividad antes del freeze
grep "DarkIME" freeze_log.txt | tail -50

# Buscar TFLite activity
grep "T5\|TFLite" freeze_log.txt
```

### Paso 3: Verificar Thread Dump
```bash
# Si hay ANR, obtener thread dump
adb shell cat /data/anr/traces.txt | grep -A 50 "org.dark.keyboard"
```

---

## Soluciones Rápidas

### Solución 1: Deshabilitar TFLite
En `DictSuggestionEngine.kt` línea 81-86:

```kotlin
// ANTES
private val reRanker: ReRanker by lazy {
    try {
        TFLiteReRanker(context).also { it.initialize() }
            .takeIf { it.isAvailable } ?: NoOpReRanker()
    } catch (_: Exception) { NoOpReRanker() }
}

// DESPUÉS (forzar NoOp)
private val reRanker: ReRanker = NoOpReRanker()
```

### Solución 2: Aumentar Debounce
En `DarkIME2.kt` línea 1010:

```kotlin
// ANTES
delay(DEBOUNCE_MS)  // 150ms

// DESPUÉS
delay(500)  // 500ms - más lento pero más estable
```

### Solución 3: Limitar Sugerencias
En `DictSuggestionEngine.kt` línea 192:

```kotlin
// ANTES
.take(20) // top-20 para el re-ranker

// DESPUÉS
.take(5)  // solo top-5 (menos trabajo para TFLite)
```

### Solución 4: Deshabilitar Autocorrect
En Settings → Autocorrect → OFF

---

## Código Sospechoso

### TFLiteReRanker.rerank() - Línea 108-128
```kotlin
override fun rerank(candidates: List<String>, context: String): List<String> {
    if (!isAvailable || interpreter == null || candidates.size <= 1) return candidates

    return try {
        val ctxEmb = embed(context) ?: return candidates  // ← Puede ser lento

        val scored = candidates.mapNotNull { word ->
            val wordEmb = embed(word) ?: return@mapNotNull null  // ← Loop sobre cada candidato
            val score   = cosineSimilarity(ctxEmb, wordEmb)
            word to score
        }
        // ...
    }
}
```

**Problema:** Si hay 20 candidatos, hace 21 inferencias TFLite (1 contexto + 20 palabras).

### CompactTrie.findByEditDistance() - Línea 100-140
```kotlin
fun findByEditDistance(typed: String, maxDist: Int = 2, maxResults: Int = 10): List<Entry> {
    // ...
    for (entry in words) {  // ← Itera sobre 8000 palabras
        // ...
        val dist = damerauLevenshtein(typed, entry.word, maxDist)
        // ...
    }
}
```

**Problema:** Calcula edit distance para 8000 palabras en cada llamada.

---

## Métricas Esperadas

**Performance normal:**
- `updateSuggestions()`: ~150-300ms total
- `CompactTrie.lookup()`: ~1-2ms
- `CompactTrie.findByEditDistance()`: ~2-5ms
- `TFLiteReRanker.rerank()`: ~50-150ms (20 candidatos)

**Si tarda más de 500ms → problema**

---

## Próximos Pasos

1. **Conectar ADB** y capturar logs durante freeze
2. **Analizar logs** para ver última operación antes del freeze
3. **Probar soluciones rápidas** (deshabilitar TFLite primero)
4. **Reportar hallazgos** con logs adjuntos
