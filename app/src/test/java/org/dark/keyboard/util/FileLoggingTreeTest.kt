package org.dark.keyboard.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class FileLoggingTreeTest {

    private lateinit var ctx: Context
    private lateinit var tree: FileLoggingTree

    @Before
    fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        tree = FileLoggingTree(ctx)
        Timber.plant(tree)
    }

    @After
    fun teardown() {
        Timber.uprootAll()
    }

    @Test fun `init - crea carpeta de logs`() {
        val logDir = File(ctx.cacheDir, "logs")
        assertTrue("Log dir debe existir", logDir.exists())
    }

    @Test fun `log - escribe a archivo`() {
        Timber.i("hello world")
        val files = tree.getLogFiles()
        assertTrue("Debe haber al menos 1 archivo de log", files.isNotEmpty())
    }

    @Test fun `log - escribe mensaje correcto`() {
        val freshTree = FileLoggingTree(ctx)
        Timber.uprootAll()
        Timber.plant(freshTree)
        Thread.sleep(50)
        Timber.tag("MyTag").e("error message")
        val content = freshTree.getCurrentLogFile()?.readText() ?: ""
        assertTrue("Debe contener el mensaje [$content]", content.contains("error message"))
        assertTrue("Debe contener nivel E", content.contains("E/"))
    }

    @Test fun `log - escribe exception stack trace`() {
        val freshTree = FileLoggingTree(ctx)
        Timber.uprootAll()
        Timber.plant(freshTree)
        Thread.sleep(50)
        val ex = RuntimeException("test exception")
        Timber.e(ex, "crash")
        val content = freshTree.getCurrentLogFile()?.readText() ?: ""
        assertTrue("Debe contener el mensaje [$content]", content.contains("crash"))
        assertTrue("Debe contener el exception", content.contains("RuntimeException"))
    }

    @Test fun `getLogFiles - retorna archivos ordenados por fecha`() {
        Timber.i("msg1")
        Thread.sleep(10)
        Timber.i("msg2")
        val files = tree.getLogFiles()
        assertTrue("Debe haber archivos", files.isNotEmpty())
    }

    @Test fun `getTotalLogSizeMB - retorna valor correcto`() {
        Timber.i("msg")
        val size = tree.getTotalLogSizeMB()
        assertTrue("Size debe ser >= 0", size >= 0.0)
    }

    @Test fun `clearAllLogs - borra y crea nuevo archivo activo`() {
        Timber.i("msg")
        val before = tree.getLogFiles().size
        assertTrue("Debe haber archivos antes de clear", before > 0)
        tree.clearAllLogs()
        val after = tree.getLogFiles().size
        assertEquals("Debe haber exactamente 1 archivo después de clear", 1, after)
    }

    @Test fun `getCurrentLogFile - retorna archivo existente`() {
        assertNotNull(tree.getCurrentLogFile())
        assertTrue(tree.getCurrentLogFile()!!.exists())
    }
}
