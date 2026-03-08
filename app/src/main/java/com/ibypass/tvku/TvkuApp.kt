package com.ibypass.tvku

import android.app.Application
import android.util.Log

class TvkuApp : Application() {

    companion object {
        private const val TAG = "TvkuApp"
    }

    private var authReady = false

    override fun onCreate() {
        super.onCreate()

        try {
            initializeApp()
        } catch (e: Exception) {
            Log.e(TAG, "Initialization error: ${e.message}")
        }
    }

    private fun initializeApp() {
        // Karena Firebase sudah dihapus, kita hanya inisialisasi sederhana
        authReady = true
        Log.d(TAG, "App initialized without Firebase")
    }

    fun isAuthReady(): Boolean {
        return authReady
    }

    suspend fun ensureAuthenticated(): Boolean {
        return try {
            // Tidak ada Firebase Auth lagi
            authReady = true
            Log.d(TAG, "Authentication bypass (no Firebase)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed: ${e.message}")
            false
        }
    }

    fun isFirebaseReady(): Boolean {
        // Selalu true supaya kode lama tidak crash
        return true
    }
}