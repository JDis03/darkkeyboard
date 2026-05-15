package org.dark.keyboard.suggestions

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Descarga el modelo TFLite desde GitHub Releases.
 *
 * Fase 4: modelo bajo demanda.
 * APK base: 18MB. Con modelo: 18MB + 119MB descargado en filesDir.
 */
object ModelDownloader {

    private const val TAG = "ModelDownloader"

    private const val RELEASE_BASE =
        "https://github.com/JDis03/darkkeyboard/releases/download/v1.1.0-models"

    val FILES = listOf(
        "suggestions_model.tflite",
        "bpe_vocab.json",
        "bpe_merges.txt"
    )

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").also { it.mkdirs() }

    fun modelFile(context: Context): File =
        File(modelsDir(context), "suggestions_model.tflite")

    fun areModelsDownloaded(context: Context): Boolean =
        FILES.all { File(modelsDir(context), it).exists() }

    fun totalSizeMB() = 119 + 1 + 1  // ~121 MB

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
                        onProgress(progress)
                        Log.i(TAG, "Download $completed/${FILES.size} complete")
                        if (completed == FILES.size) {
                            ctx.unregisterReceiver(this)
                            onComplete()
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = cursor.getInt(reasonCol)
                        ctx.unregisterReceiver(this)
                        onError("Download failed (reason=$reason)")
                        Log.e(TAG, "Download failed: reason=$reason")
                    }
                }
                cursor.close()
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
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

            val request = DownloadManager.Request(Uri.parse("$RELEASE_BASE/$fileName")).apply {
                setTitle("DarkKeyboard AI Model")
                setDescription("Downloading $fileName...")
                setDestinationUri(Uri.fromFile(destFile))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            val id = dm.enqueue(request)
            downloadIds.add(id)
            Log.i(TAG, "Enqueued download: $fileName (id=$id)")
        }
    }

    /**
     * Elimina los modelos descargados (libera espacio).
     */
    fun deleteModels(context: Context) {
        FILES.forEach { File(modelsDir(context), it).delete() }
        Log.i(TAG, "Models deleted")
    }
}
