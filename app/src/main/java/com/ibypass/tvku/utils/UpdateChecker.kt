package com.ibypass.tvku.utils

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import com.ibypass.tvku.R
import java.io.File

object UpdateChecker {

    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var percentText: TextView? = null

    fun checkForUpdate(activity: Activity, onNoUpdate: () -> Unit) {
        val ref = FirebaseDatabase.getInstance().getReference("app_update")
        ref.get().addOnSuccessListener { snapshot ->
            val latestVersion = snapshot.child("latest_version_code").getValue(Int::class.java) ?: return@addOnSuccessListener
            val updateUrl = snapshot.child("update_url").getValue(String::class.java) ?: return@addOnSuccessListener
            val forceUpdate = snapshot.child("force_update").getValue(Boolean::class.java) ?: false

            val currentVersion = activity.packageManager
                .getPackageInfo(activity.packageName, 0).versionCode

            if (currentVersion < latestVersion) {
                showUpdateDialog(activity, updateUrl, forceUpdate)
            } else {
                onNoUpdate()
            }
        }.addOnFailureListener {
            Toast.makeText(activity, "Gagal cek update", Toast.LENGTH_SHORT).show()
            onNoUpdate()
        }
    }

    private fun showUpdateDialog(activity: Activity, url: String, force: Boolean) {
        AlertDialog.Builder(activity).apply {
            setTitle("🚀 Update Tersedia")
            setMessage("Versi baru tersedia. Silakan update untuk menikmati fitur terbaru.")
            setCancelable(!force)
            setPositiveButton("Update Sekarang") { _, _ ->
                downloadAndInstallApk(activity, url)
            }
            if (!force) {
                setNegativeButton("Nanti") { _, _ -> /* skip update */ }
            }
            show()
        }
    }

    private fun showLoading(activity: Activity) {
        val inflater = activity.layoutInflater
        val view = inflater.inflate(R.layout.dialog_progress, null)

        progressBar = view.findViewById(R.id.progress_bar)
        percentText = view.findViewById(R.id.tv_progress_percent)

        progressDialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()

        progressDialog?.show()
    }

    private fun hideLoading() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        percentText = null
    }

    private fun downloadAndInstallApk(activity: Activity, url: String) {
        val fileName = "ndeloktipi-1.4.apk"
        showLoading(activity)

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Mengunduh Update")
            setDescription("Mendownload file aplikasi...")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
        }

        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val handler = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Handler(Looper.getMainLooper())
        } else {
            @Suppress("DEPRECATION")
            Handler()
        }

        val query = DownloadManager.Query().setFilterById(downloadId)
        var isMonitoring = true

        val progressRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return

                val cursor = manager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    if (bytesTotal > 0) {
                        val progress = (bytesDownloaded * 100L / bytesTotal).toInt()
                        progressBar?.progress = progress
                        percentText?.text = "$progress%"
                    }

                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cursor.close()
                        isMonitoring = false

                        handler.postDelayed({
                            installApk(activity, fileName)
                        }, 500)
                        return
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        cursor.close()
                        isMonitoring = false
                        hideLoading()
                        Toast.makeText(activity, "Download gagal", Toast.LENGTH_LONG).show()
                        return
                    }
                }
                cursor?.close()
                if (isMonitoring) {
                    handler.postDelayed(this, 500)
                }
            }
        }

        handler.post(progressRunnable)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(activity, fileName)
                    activity.unregisterReceiver(this)
                }
            }
        }

        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun installApk(activity: Activity, fileName: String) {
        hideLoading()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = activity.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivity(intent)
                Toast.makeText(activity, "Izinkan instalasi dari sumber ini, lalu coba lagi.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val apkFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (!apkFile.exists()) {
            Toast.makeText(activity, "File APK tidak ditemukan di: ${apkFile.absolutePath}", Toast.LENGTH_LONG).show()
            return
        }

        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apkFile)
            } catch (e: Exception) {
                Toast.makeText(activity, "Error FileProvider: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            Uri.fromFile(apkFile)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            activity.startActivity(installIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "Gagal membuka installer: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Error install: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}