package com.example.notesdetector.data.utils

import android.content.Context
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
}