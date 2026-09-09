package com.example.entimate.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {
    fun downloadedFile(context: Context): File = File(context.cacheDir, "entimate-update.apk")

    suspend fun download(context: Context, url: String, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("User-Agent", "ENTimate")
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                val code = conn.responseCode
                if (code !in 200..299) throw RuntimeException("HTTP $code")
                val total = conn.contentLength.toLong()
                val file = downloadedFile(context)
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
                file
            } finally {
                conn.disconnect()
            }
        }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else null
}