package org.dark.keyboard

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.InputStream

object XmlKeyboardStorage {


    private const val LAYOUTS_DIR = "layouts"

    private fun layoutsDir(context: Context): File {
        val dir = File(context.filesDir, LAYOUTS_DIR)
        dir.mkdirs()
        return dir
    }

    fun listLayouts(context: Context): List<String> =
        layoutsDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".xml") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    fun exists(context: Context, name: String): Boolean =
        File(layoutsDir(context), "$name.xml").exists()

    fun openInputStream(context: Context, name: String): InputStream? {
        val file = File(layoutsDir(context), "$name.xml")
        return if (file.exists()) file.inputStream() else null
    }

    fun readContent(context: Context, name: String): String? {
        val file = File(layoutsDir(context), "$name.xml")
        return if (file.exists()) file.readText() else null
    }

    fun saveLayout(context: Context, name: String, xmlContent: String) {
        File(layoutsDir(context), "$name.xml").writeText(xmlContent)
        Timber.i("Saved custom layout: $name")
    }

    fun deleteLayout(context: Context, name: String): Boolean {
        val deleted = File(layoutsDir(context), "$name.xml").delete()
        if (deleted) Timber.i("Deleted custom layout: $name")
        return deleted
    }

    fun getTemplateContent(context: Context, xmlResId: Int): String {
        return try {
            context.resources.openRawResource(
                context.resources.getIdentifier(
                    context.resources.getResourceEntryName(xmlResId),
                    "xml",
                    context.packageName
                )
            ).bufferedReader().readText()
        } catch (e: Exception) {
            Timber.e(e, "Failed to read template XML")
            ""
        }
    }
}
