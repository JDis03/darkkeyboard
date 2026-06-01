package org.dark.keyboard.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutModelTest {
    
    @Test
    fun `create basic layout with required properties`() {
        val layout = LayoutModel(
            name = "Test Layout",
            version = 1,
            width = 1080,
            height = 720,
            keys = emptyList()
        )
        
        assertEquals("Test Layout", layout.name)
        assertEquals(1, layout.version)
        assertEquals(1080, layout.width)
        assertEquals(720, layout.height)
        assertEquals(0, layout.keys.size)
    }
    
    @Test
    fun `create layout with multiple keys`() {
        val keys = listOf(
            KeyModel("Q", 113, 0, 0, 100, 150),
            KeyModel("W", 119, 100, 0, 100, 150),
            KeyModel("E", 101, 200, 0, 100, 150)
        )
        
        val layout = LayoutModel(
            name = "QWE Layout",
            width = 300,
            height = 150,
            keys = keys
        )
        
        assertEquals(3, layout.keys.size)
        assertEquals("Q", layout.keys[0].label)
        assertEquals("W", layout.keys[1].label)
        assertEquals("E", layout.keys[2].label)
    }
    
    @Test
    fun `layout constants are defined`() {
        assertEquals(1, LayoutModel.CURRENT_VERSION)
        assertEquals(100, LayoutModel.MAX_KEYS)
        assertEquals(60, LayoutModel.MIN_KEY_SIZE)
    }
    
    @Test
    fun `default version is CURRENT_VERSION`() {
        val layout = LayoutModel(
            name = "Test",
            width = 1080,
            height = 720,
            keys = emptyList()
        )
        
        assertEquals(LayoutModel.CURRENT_VERSION, layout.version)
    }
    
    @Test
    fun `layout with 100 keys (max)`() {
        val keys = (0 until 100).map { i ->
            KeyModel(
                label = i.toString(),
                code = i,
                x = (i % 10) * 100,
                y = (i / 10) * 150,
                width = 100,
                height = 150
            )
        }
        
        val layout = LayoutModel(
            name = "100 Keys Layout",
            width = 1000,
            height = 1500,
            keys = keys
        )
        
        assertEquals(100, layout.keys.size)
        assertTrue(layout.keys.size <= LayoutModel.MAX_KEYS)
    }
}
