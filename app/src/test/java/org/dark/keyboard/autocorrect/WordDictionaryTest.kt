package org.dark.keyboard.autocorrect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class WordDictionaryTest {

    private lateinit var dict: WordDictionary

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dict = WordDictionary(ctx)
        dict.load("es")
    }

    // ── Levenshtein ────────────────────────────────────────────────────

    @Test fun `levenshtein - misma palabra es 0`() {
        assertEquals(0, dict.levenshtein("hola", "hola"))
    }

    @Test fun `levenshtein - insercion de un char es 1`() {
        assertEquals(1, dict.levenshtein("hola", "holaa"))
    }

    @Test fun `levenshtein - borrado de un char es 1`() {
        assertEquals(1, dict.levenshtein("holaa", "hola"))
    }

    @Test fun `levenshtein - sustitucion de un char es 1`() {
        assertEquals(1, dict.levenshtein("hola", "bola"))
    }

    @Test fun `levenshtein - transposicion teh-the es 2 (Levenshtein estandar)`() {
        // Levenshtein estándar NO cuenta transposiciones como 1 operación.
        // "teh" → "the": sustituir e→h + sustituir h→e = 2 operaciones.
        // (Damerau-Levenshtein daría 1, pero no es lo que implementamos)
        assertEquals(2, dict.levenshtein("teh", "the"))
    }

    @Test fun `levenshtein - dos errores es 2`() {
        assertEquals(2, dict.levenshtein("recibe", "recibir"))
    }

    @Test fun `levenshtein - palabras muy distintas supera MAX`() {
        val d = dict.levenshtein("xyz", "hola")
        assertTrue("Esperado > 2, fue $d", d > 2)
    }

    @Test fun `levenshtein - strings vacias son 0`() {
        assertEquals(0, dict.levenshtein("", ""))
    }

    @Test fun `levenshtein - una vacia retorna longitud de la otra`() {
        assertEquals(4, dict.levenshtein("", "hola"))
        assertEquals(4, dict.levenshtein("hola", ""))
    }

    // ── getFreq ────────────────────────────────────────────────────────

    @Test fun `getFreq - palabra comun tiene frecuencia alta`() {
        assertTrue(dict.getFreq("hola") > 0)
    }

    @Test fun `getFreq - palabra inventada retorna 0`() {
        assertEquals(0, dict.getFreq("xzqwerty"))
    }

    @Test fun `contains - word en diccionario`() {
        assertTrue(dict.contains("bien"))
    }

    @Test fun `contains - word inventada no existe`() {
        assertFalse(dict.contains("ghjkqzx"))
    }

    // ── findByPrefix ───────────────────────────────────────────────────

    @Test fun `findByPrefix - retorna palabras que empiezan con prefijo`() {
        val results = dict.findByPrefix("hol")
        assertTrue(results.isNotEmpty())
        results.forEach { assertTrue("'${it.word}' no empieza con 'hol'", it.word.startsWith("hol")) }
    }

    @Test fun `findByPrefix - resultado ordenado por frecuencia desc`() {
        val results = dict.findByPrefix("bu")
        if (results.size >= 2) {
            assertTrue(results[0].freq >= results[1].freq)
        }
    }

    @Test fun `findByPrefix - prefijo sin match retorna lista vacia`() {
        val results = dict.findByPrefix("xzqwerty")
        assertTrue(results.isEmpty())
    }

    // ── findByEditDistance ─────────────────────────────────────────────

    @Test fun `findByEditDistance - teh encuentra the`() {
        val results = dict.findByEditDistance("teh", maxDist = 2)
        val words = results.map { it.word }
        assertTrue("Esperado 'the' en $words", words.contains("the"))
    }

    @Test fun `findByEditDistance - no incluye la misma palabra`() {
        val results = dict.findByEditDistance("hola", maxDist = 2)
        assertFalse(results.any { it.word == "hola" })
    }

    @Test fun `findByEditDistance - no incluye palabras mas alla del max`() {
        val results = dict.findByEditDistance("abc", maxDist = 1)
        results.forEach {
            val d = dict.levenshtein("abc", it.word)
            assertTrue("dist=$d debe ser <=1", d <= 1)
        }
    }

    @Test fun `findByEditDistance - palabra correcta no produce correcciones absurdas`() {
        // "bueno" es una palabra real — no debe sugerir nada con dist>2
        val results = dict.findByEditDistance("bueno", maxDist = 2)
        results.forEach {
            val d = dict.levenshtein("bueno", it.word)
            assertTrue(d in 1..2)
        }
    }

    // ── Multilenguaje ──────────────────────────────────────────────────

    @Test fun `switchLang - carga ingles correctamente`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val enDict = WordDictionary(ctx)
        enDict.load("en")
        assertTrue(enDict.contains("hello"))
        assertTrue(enDict.contains("world"))
        assertFalse(enDict.contains("hola"))  // español no en dict inglés
    }
}
