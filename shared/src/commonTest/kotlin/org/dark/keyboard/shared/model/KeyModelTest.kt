package org.dark.keyboard.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyModelTest {
    
    @Test
    fun `create basic key with all required properties`() {
        val key = KeyModel(
            label = "Q",
            code = 113,
            x = 0,
            y = 0,
            width = 100,
            height = 150
        )
        
        assertEquals("Q", key.label)
        assertEquals(113, key.code)
        assertEquals(0, key.x)
        assertEquals(0, key.y)
        assertEquals(100, key.width)
        assertEquals(150, key.height)
        assertEquals(emptyList(), key.popupKeys)
        assertFalse(key.repeatable)
    }
    
    @Test
    fun `create key with popup keys`() {
        val key = KeyModel(
            label = "A",
            code = 97,
            x = 10,
            y = 10,
            width = 100,
            height = 150,
            popupKeys = listOf("á", "à", "â", "ä", "1")
        )
        
        assertEquals(5, key.popupKeys.size)
        assertEquals("á", key.popupKeys[0])
    }
    
    @Test
    fun `create repeatable key`() {
        val key = KeyModel(
            label = "⌫",
            code = KeyModel.CODE_DELETE,
            x = 0,
            y = 0,
            width = 100,
            height = 150,
            repeatable = true
        )
        
        assertTrue(key.repeatable)
        assertEquals(KeyModel.CODE_DELETE, key.code)
    }
    
    @Test
    fun `create modifier key with sticky behavior`() {
        val shiftKey = KeyModel(
            label = "Shift",
            code = KeyModel.CODE_SHIFT,
            x = 0,
            y = 0,
            width = 150,
            height = 150,
            isModifier = true,
            isSticky = true
        )
        
        assertTrue(shiftKey.isModifier)
        assertTrue(shiftKey.isSticky)
    }
    
    @Test
    fun `create key with shift label`() {
        val key = KeyModel(
            label = "1",
            code = 49, // '1'
            x = 0,
            y = 0,
            width = 100,
            height = 150,
            shiftLabel = "!"
        )
        
        assertEquals("1", key.label)
        assertEquals("!", key.shiftLabel)
    }
    
    @Test
    fun `create key with edge flags`() {
        val key = KeyModel(
            label = "Q",
            code = 113,
            x = 0,
            y = 0,
            width = 100,
            height = 150,
            edgeFlags = KeyModel.EDGE_LEFT or KeyModel.EDGE_TOP
        )
        
        assertEquals(KeyModel.EDGE_LEFT or KeyModel.EDGE_TOP, key.edgeFlags)
    }
    
    @Test
    fun `edge flag constants are correct`() {
        assertEquals(1, KeyModel.EDGE_LEFT)
        assertEquals(2, KeyModel.EDGE_RIGHT)
        assertEquals(4, KeyModel.EDGE_TOP)
        assertEquals(8, KeyModel.EDGE_BOTTOM)
    }
    
    @Test
    fun `common key codes are defined`() {
        assertEquals(-1, KeyModel.CODE_SHIFT)
        assertEquals(-113, KeyModel.CODE_CTRL)
        assertEquals(-57, KeyModel.CODE_ALT)
        assertEquals(-119, KeyModel.CODE_FN)
        assertEquals(-5, KeyModel.CODE_DELETE)
        assertEquals(10, KeyModel.CODE_ENTER)
        assertEquals(32, KeyModel.CODE_SPACE)
        assertEquals(9, KeyModel.CODE_TAB)
    }
    
    @Test
    fun `key data class equality works`() {
        val key1 = KeyModel("A", 97, 0, 0, 100, 150)
        val key2 = KeyModel("A", 97, 0, 0, 100, 150)
        val key3 = KeyModel("B", 98, 0, 0, 100, 150)
        
        assertEquals(key1, key2)
        assertTrue(key1 != key3)
    }
}
