package com.example.fastvideo.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ApkExporter {

    data class ExportResult(
        val success: Boolean,
        val filePathOrUri: String,
        val fileSizeBytes: Long,
        val message: String
    )

    /**
     * One-tap export: extracts the installed APK package and saves it directly to the
     * device Downloads / Storage folder as "FV_Offline_Converter.apk".
     */
    fun exportApkToDownloads(context: Context): ExportResult {
        try {
            val sourceApkPath = context.applicationInfo.sourceDir
            val sourceFile = File(sourceApkPath)
            if (!sourceFile.exists()) {
                return ExportResult(
                    success = false,
                    filePathOrUri = "",
                    fileSizeBytes = 0L,
                    message = "Source APK file not found at $sourceApkPath"
                )
            }

            val apkSize = sourceFile.length()
            val fileName = "FV_Offline_Converter.apk"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    return ExportResult(
                        success = false,
                        filePathOrUri = "",
                        fileSizeBytes = 0L,
                        message = "Could not create entry in MediaStore Downloads."
                    )
                }

                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                return ExportResult(
                    success = true,
                    filePathOrUri = uri.toString(),
                    fileSizeBytes = apkSize,
                    message = "Saved to Downloads/$fileName (${formatSize(apkSize)})"
                )
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                return ExportResult(
                    success = true,
                    filePathOrUri = targetFile.absolutePath,
                    fileSizeBytes = apkSize,
                    message = "Saved to ${targetFile.absolutePath} (${formatSize(apkSize)})"
                )
            }
        } catch (e: Exception) {
            return ExportResult(
                success = false,
                filePathOrUri = "",
                fileSizeBytes = 0L,
                message = "Failed to export APK: ${e.message}"
            )
        }
    }

    /**
     * One-tap share: shares the app's APK directly to other devices, flashcards, or apps.
     */
    fun shareApk(context: Context) {
        val sourceApkPath = context.applicationInfo.sourceDir
        val sourceFile = File(sourceApkPath)

        // Copy to app's cache directory first for FileProvider access
        val cacheApk = File(context.cacheDir, "FV_Offline_Converter.apk")
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(cacheApk).use { output ->
                input.copyTo(output)
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheApk
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_SUBJECT, "FV Offline Converter APK")
            putExtra(Intent.EXTRA_TEXT, "FV Offline Converter Android APK - offline video converter for Nintendo DS/DSi.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share FV Converter APK")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun getApkInfo(context: Context): Triple<String, Long, String> {
        val sourceApkPath = context.applicationInfo.sourceDir
        val sourceFile = File(sourceApkPath)
        val size = if (sourceFile.exists()) sourceFile.length() else 0L
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "1.0"
        return Triple(versionName, size, formatSize(size))
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return "%.2f MB".format(mb)
    }
}
