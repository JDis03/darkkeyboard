package org.dark.keyboard.util

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Custom Timber.Tree que escribe logs a archivos.
 *
 * Basado en el sistema de logs de DarkRDP.
 *
 * Ruta: {cacheDir}/logs/
 */
class FileLoggingTree(private val context: Context) : Timber.Tree() {

    companion object {
        private const val TAG = "FileLoggingTree"
        private const val MAX_LOG_FILES = 5
        private const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 // 5MB
        private const val LOG_PREFIX = "darkime_"
        private const val LOG_SUFFIX = ".log"
    }

    private val logDir: File = File(context.cacheDir, "logs").apply {
        if (!exists()) mkdirs()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private var currentLogFile: File? = null
    private var fileWriter: FileWriter? = null

    init {
        initLogFile()
        cleanOldLogs()
        android.util.Log.i(TAG, "Initialized. Log dir: ${logDir.absolutePath}, files: ${getLogFiles().size}")
    }

    private fun initLogFile() {
        try {
            val timestamp = fileNameFormat.format(Date())
            currentLogFile = File(logDir, "${LOG_PREFIX}${timestamp}${LOG_SUFFIX}")
            fileWriter = FileWriter(currentLogFile, true)

            // Write header
            fileWriter?.apply {
                write("=".repeat(80) + "\n")
                write("DarkIME Log Session Started\n")
                write("Timestamp: ${dateFormat.format(Date())}\n")
                write("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                write("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                write("=".repeat(80) + "\n\n")
                flush()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun cleanOldLogs() {
        try {
            val logFiles = logDir.listFiles { file ->
                file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_SUFFIX)
            }?.sortedByDescending { it.lastModified() } ?: return

            logFiles.drop(MAX_LOG_FILES).forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rotateIfNeeded() {
        currentLogFile?.let { file ->
            if (file.length() > MAX_FILE_SIZE_BYTES) {
                try {
                    fileWriter?.close()
                    initLogFile()
                    cleanOldLogs()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            rotateIfNeeded()

            val timestamp = dateFormat.format(Date())
            val level = when (priority) {
                android.util.Log.VERBOSE -> "V"
                android.util.Log.DEBUG -> "D"
                android.util.Log.INFO -> "I"
                android.util.Log.WARN -> "W"
                android.util.Log.ERROR -> "E"
                android.util.Log.ASSERT -> "A"
                else -> "?"
            }

            val logLine = StringBuilder()
            logLine.append("[$timestamp] ")
            logLine.append("$level/")
            logLine.append(tag ?: "DarkIME")
            logLine.append(": ")
            logLine.append(message)
            logLine.append("\n")

            t?.let {
                logLine.append("Exception: ${it.javaClass.simpleName}: ${it.message}\n")
                logLine.append(it.stackTraceToString())
                logLine.append("\n")
            }

            synchronized(this) {
                fileWriter?.apply {
                    write(logLine.toString())
                    flush()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            fileWriter?.apply {
                write("\n" + "=".repeat(80) + "\n")
                write("Log Session Ended: ${dateFormat.format(Date())}\n")
                write("=".repeat(80) + "\n")
                flush()
                close()
            }
            fileWriter = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getLogFiles(): List<File> {
        return logDir.listFiles { file ->
            file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_SUFFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getCurrentLogFile(): File? = currentLogFile

    fun getTotalLogSizeMB(): Double {
        val totalBytes = logDir.listFiles()?.sumOf { it.length() } ?: 0L
        return totalBytes / (1024.0 * 1024.0)
    }

    fun clearAllLogs() {
        try {
            fileWriter?.close()
            logDir.listFiles()?.forEach { it.delete() }
            initLogFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
