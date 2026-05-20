package org.dark.keyboard.suggestions

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Descarga el modelo TFLite desde GitHub Releases.
 *
 * Fase 4: modelo bajo demanda.
 * APK base: 18MB. Con modelo: 18MB + 119MB descargado en filesDir.
 */
object ModelDownloader {



    private const val RELEASE_BASE =
        "https://github.com/JDis03/darkkeyboard/releases/download/v1.2.0-models"

    // SHA256 — T5 encoder t5_small_multi TFLite INT8 (34MB)
    private const val EXPECTED_CHECKSUM = "79c5a734d0d37261247bcee46e945e1b6964f0bc3e1628946c7b852e354c1066"

    val FILES = listOf(
        "suggestions_model.tflite",
        "spiece.model"
    )

    fun totalSizeMB() = 35  // T5 encoder INT8 + SentencePiece vocab

    fun modelsDir(context: Context): File =
        (context.getExternalFilesDir(null)?.let { File(it, "models") }
            ?: File(context.filesDir, "models")).also { it.mkdirs() }

    fun modelFile(context: Context): File =
        File(modelsDir(context), "suggestions_model.tflite")

    fun areModelsDownloaded(context: Context): Boolean =
        FILES.all { File(modelsDir(context), it).exists() }

    /**
     * Inicia la descarga de todos los archivos del modelo.
     * Usa DownloadManager para descargas en background con progress.
     *
     * @param onComplete llamado en el main thread cuando termina
     * @param onError    llamado si falla alguna descarga
     */
    fun download(
        context: Context,
        onProgress: (Int) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val dir = modelsDir(context)
        val downloadIds = mutableListOf<Long>()
        var completed = 0
        val mainHandler = Handler(Looper.getMainLooper())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id !in downloadIds) return

                val query = DownloadManager.Query().setFilterById(id)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusCol)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        completed++
                        val progress = (completed * 100) / FILES.size
                        mainHandler.post { onProgress(progress) }
                        Timber.i("Download $completed/${FILES.size} complete")
                        if (completed == FILES.size) {
                            try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                            mainHandler.post { onComplete() }
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = cursor.getInt(reasonCol)
                        try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                        mainHandler.post { onError("Download failed (reason=$reason)") }
                        Timber.e("Download failed: reason=$reason")
                    }
                }
                cursor.close()
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register receiver")
            onError("Failed to start download: ${e.message}")
            return
        }

        FILES.forEach { fileName ->
            val destFile = File(dir, fileName)
            if (destFile.exists()) {
                completed++
                if (completed == FILES.size) {
                    context.unregisterReceiver(receiver)
                    onComplete()
                    return
                }
                return@forEach
            }

            // Resolver redirect de GitHub → CDN URL directa
            // (GitHub redirige a Azure Blob con token firmado que expira)
            val githubUrl = "$RELEASE_BASE/$fileName"
            Thread {
                try {
                    val resolvedUrl = resolveRedirect(githubUrl)
                    Timber.i("Resolved URL: $resolvedUrl")
                    mainHandler.post {
                        val request = DownloadManager.Request(Uri.parse(resolvedUrl)).apply {
                            setTitle("DarkKeyboard AI Model")
                            setDescription("Downloading $fileName...")
                            setDestinationInExternalFilesDir(context, "models", fileName)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                            setAllowedOverMetered(true)
                            setAllowedOverRoaming(true)
                            setAllowedNetworkTypes(
                                DownloadManager.Request.NETWORK_WIFI or
                                DownloadManager.Request.NETWORK_MOBILE
                            )
                        }
                        val id = dm.enqueue(request)
                        downloadIds.add(id)
                        Timber.i("Enqueued download: $fileName (id=$id)")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to resolve URL for $fileName")
                    mainHandler.post { onError("Cannot resolve download URL: ${e.message}") }
                }
            }.start()
        }
    }

    /**
     * Elimina los modelos descargados (libera espacio).
     */
    fun deleteModels(context: Context) {
        FILES.forEach { File(modelsDir(context), it).delete() }
        Timber.i("Models deleted")
    }

    /**
     * Resuelve redirects HTTP hasta obtener la URL final (CDN directa).
     * GitHub Releases redirige a Azure Blob Storage con token firmado.
     */
    fun resolveRedirect(url: String, maxRedirects: Int = 5): String {
        var currentUrl = url
        repeat(maxRedirects) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "DarkKeyboard/1.2")
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: return currentUrl
                currentUrl = location
                conn.disconnect()
            } else {
                conn.disconnect()
                return currentUrl
            }
        }
        return currentUrl
    }

    /**
     * Calcula SHA256 checksum de un archivo.
     */
    fun checksum(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifica checksum del modelo descargado.
     * Retorna true si coincide con EXPECTED_CHECKSUM.
     */
    fun verifyChecksum(context: Context): Boolean {
        val modelFile = modelFile(context)
        if (!modelFile.exists()) return false
        val actual = checksum(modelFile)
        val match = actual.equals(EXPECTED_CHECKSUM, ignoreCase = true)
        if (!match) {
            Timber.w("Checksum mismatch! expected=$EXPECTED_CHECKSUM actual=$actual")
        }
        return match
    }
}
