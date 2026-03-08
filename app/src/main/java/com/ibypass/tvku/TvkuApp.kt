package com.ibypass.tvku

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import android.os.Build

class TvkuApp : Application(), ImageLoaderFactory {
    private var firebaseInitialized = false
    private var authInitialized = false

    companion object {
        private const val TAG = "TvkuApp"
    }

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .allowHardware(false) // Disable hardware bitmaps for older devices
            .build()
    }

    private fun initializeFirebase() {
        try {
            val app = try {
                FirebaseApp.initializeApp(this)
            } catch (e: Exception) {
                null
            }
            
            if (app == null) {
                firebaseInitialized = false
                return
            }
            
            try {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            } catch (e: Exception) {
                try {
                    FirebaseDatabase.getInstance().purgeOutstandingWrites()
                } catch (innerEx: Exception) {
                }
            }
            
            initializeAuth()
            
            try {
                val remoteConfig = Firebase.remoteConfig
                val configSettings = remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600
                }
                remoteConfig.setConfigSettingsAsync(configSettings)
            } catch (e: Exception) {
            }
            
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("all")
            } catch (e: Exception) {
            }

            firebaseInitialized = true
        } catch (e: Exception) {
            firebaseInitialized = false
        }
    }

    private fun initializeAuth() {
        try {
            val auth = FirebaseAuth.getInstance()
            
            if (auth.currentUser != null) {
                authInitialized = true
                return
            }
            
            auth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        authInitialized = true
                    } else {
                        authInitialized = false
                    }
                }
        } catch (e: Exception) {
            authInitialized = false
        }
    }

    suspend fun ensureAuthenticated(): Boolean {
        if (FirebaseAuth.getInstance().currentUser != null) {
            return true
        }
        
        return try {
            val authResult = FirebaseAuth.getInstance().signInAnonymously().await()
            authResult.user != null
        } catch (e: Exception) {
            false
        }
    }

    fun isFirebaseReady(): Boolean {
        return firebaseInitialized
    }
    
    fun isAuthReady(): Boolean {
        return authInitialized
    }
} 