package com.example.notesdetector.data.utils

import android.content.Context
import android.content.ContentUris
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri

object FileUtils {

    fun getFileNameFromUri(context: Context, filePath: String?): String {
        if (filePath.isNullOrEmpty()) return "Unknown"

        val uri = filePath.toUri()
        var name = "Unknown file"

        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }

                if (name == "Unknown file") {
                    name = getNameFromDocumentId(context, uri) ?: name
                }
            } else if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } else {
                name = uri.lastPathSegment ?: "Unknown"
            }
        } catch (e: Exception) {
            name = filePath.substringAfterLast("%3A").substringAfterLast("/")
        }

        return name.substringBeforeLast(".")
    }

    private fun getNameFromDocumentId(context: Context, uri: android.net.Uri): String? {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        if (!documentId.startsWith("msf:")) return null

        val mediaId = documentId.substringAfter("msf:").toLongOrNull() ?: return null
        val mediaUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), mediaId)

        return context.contentResolver.query(
            mediaUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else null
        }
    }
}
