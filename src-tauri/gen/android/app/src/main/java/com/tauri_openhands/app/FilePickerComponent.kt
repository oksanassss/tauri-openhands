package com.tauri_openhands.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Native component that handles file picker messages.
 */
class FilePickerComponent(private val context: Context) : NativeComponent {
    private val TAG = "FilePickerComponent"
    private var bridgeManager: StradaBridgeManager? = null
    private val FILE_PICKER_REQUEST_CODE = 1001

    fun setBridgeManager(bridgeManager: StradaBridgeManager) {
        this.bridgeManager = bridgeManager
    }

    override fun handleMessage(message: StradaMessage) {
        when (message.event) {
            "open" -> {
                val mimeType = message.data["type"] as? String ?: "*/*"
                openFilePicker(mimeType)
            }
        }
    }

    private fun openFilePicker(mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = mimeType
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            
            val activity = context as? Activity
            if (activity != null) {
                activity.startActivityForResult(
                    Intent.createChooser(intent, "Select a file"),
                    FILE_PICKER_REQUEST_CODE
                )
            } else {
                Log.e(TAG, "Context is not an Activity")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file picker", e)
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                val fileName = getFileName(uri)
                val fileSize = getFileSize(uri)
                val mimeType = getMimeType(uri)
                
                // Send result back to web
                bridgeManager?.sendToWeb("filePicker", "selected", mapOf(
                    "fileName" to (fileName ?: "Unknown"),
                    "fileSize" to (fileSize ?: 0),
                    "mimeType" to (mimeType ?: "application/octet-stream"),
                    "uri" to uri.toString()
                ))
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                it.getString(nameIndex)
            } else {
                uri.lastPathSegment
            }
        } ?: uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
            if (sizeIndex >= 0 && it.moveToFirst()) {
                it.getLong(sizeIndex)
            } else {
                null
            }
        }
    }

    private fun getMimeType(uri: Uri): String? {
        return context.contentResolver.getType(uri) ?: run {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }
}