package com.ibypass.tvku.utils

import android.os.Build
import android.util.Base64
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private object SecurityConstants {
    private const val ENC_KEY = "VFZLdVNlY3VyZUtleTIwMjU="
    private const val ENC_IV = "VFZLdUluaXRWZWN0b3I="

    internal const val ENC_TAG = "U3Vic2NyaXB0aW9uQ2hlY2s="
    internal const val ENC_CONFIG_KEY = "c3Vic2NyaXB0aW9uX2NoZWNrX3VybA=="
    internal const val ENC_METHOD = "R0VU"
    internal const val ENC_USER_AGENT = "VFZLdS1BbmRyb2lkLzEuMA=="
    internal const val ENC_ACCEPT = "dGV4dC9wbGFpbg=="

    fun decrypt(encryptedData: String): String {
        return try {
            if (encryptedData.isBlank()) {
                return ""
            }

            val pattern = "^[A-Za-z0-9+/]*={0,2}$"
            if (!encryptedData.matches(Regex(pattern))) {
                return ""
            }

            try {
                String(Base64.decode(encryptedData, Base64.NO_WRAP))
            } catch (e: IllegalArgumentException) {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun getKey(): ByteArray {
        val keyString = decrypt(ENC_KEY)
        if (keyString.isEmpty()) {
            return "TvkuSecureKey2025".toByteArray() // Fallback key
        }
        return MessageDigest.getInstance("SHA-256").digest(keyString.toByteArray())
    }

    fun getIV(): ByteArray {
        val ivString = decrypt(ENC_IV)
        if (ivString.isEmpty()) {
            return "TvkuInitVector".toByteArray().copyOf(16) // Fallback IV
        }
        return ivString.toByteArray().copyOf(16)
    }
}

private fun xF4k9L2m(input: String): String {
    return try {
        if (input.isBlank()) {
            return input
        }

        val pattern = "^[A-Za-z0-9+/]*={0,2}$"
        if (!input.matches(Regex(pattern))) {
            return input
        }
        
        val key = SecurityConstants.getKey()
        val iv = SecurityConstants.getIV()

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        
        val decodedData = try {
            Base64.decode(input, Base64.NO_WRAP)
        } catch (e: Exception) {
            return input
        }
        
        try {
            val decryptedBytes = cipher.doFinal(decodedData)
            String(decryptedBytes)
        } catch (e: Exception) {
            input
        }
    } catch (e: Exception) {
        input
    }
}

private fun nM8q3Rz7(input: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private fun bV6p1Tn4(data: ByteArray): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
        Base64.encodeToString(data, Base64.NO_WRAP)
    } else {
        Base64.encodeToString(data, Base64.DEFAULT).replace("\n", "")
    }
}

private fun zQ2w8Kx5(): Boolean {
    return try {
        val debuggerConnected = android.os.Debug.isDebuggerConnected()
        val waitingForDebugger = android.os.Debug.waitingForDebugger()

        !(debuggerConnected || waitingForDebugger)
    } catch (e: Exception) {
        true
    }
}

private fun setupSSLForOldAndroid() {
    try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {

                }
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {

                }
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })

            val protocols = arrayOf("TLSv1.2", "TLSv1.1", "TLS", "SSL")
            var sslContext: SSLContext? = null

            for (protocol in protocols) {
                try {
                    sslContext = SSLContext.getInstance(protocol)
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    break
                } catch (e: Exception) {
                }
            }

            sslContext?.let {
                HttpsURLConnection.setDefaultSSLSocketFactory(it.socketFactory)
                HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
            }
        }
    } catch (e: Exception) {
    }
}

suspend fun kR9n5Mq3(hT7xL4sP: String): Boolean {
    if (!zQ2w8Kx5()) {
        return false
    }

    val dummyOps = arrayOf("abc", "def", "ghi")
    val dummyResult = dummyOps.map { nM8q3Rz7(it) }.joinToString("")

    val jN4mQ9fX = try {
        var configSuccess = false
        var attempts = 0
        val maxAttempts = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) 5 else 3

        while (!configSuccess && attempts < maxAttempts) {
            try {
                val remoteConfig = Firebase.remoteConfig

                val timeoutMs = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) 30000L else 15000L

                val result = withTimeoutOrNull(timeoutMs) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            remoteConfig.fetchAndActivate().await()
                        } catch (e: Exception) {
                            remoteConfig.fetch().await()
                            delay(1000)
                            remoteConfig.activate().await()
                        }
                    } else {
                        remoteConfig.fetch().await()
                        delay(2000)
                        remoteConfig.activate().await()
                    }
                }

                configSuccess = result != null
                if (!configSuccess) {
                    attempts++
                    if (attempts < maxAttempts) {
                        delay((2000 * attempts).toLong())
                    }
                }
            } catch (e: Exception) {
                attempts++
                if (attempts < maxAttempts) {
                    delay((2000 * attempts).toLong())
                }
            }
        }

        configSuccess
    } catch (e: Exception) {
        false
    }

    if (!jN4mQ9fX) {
        return false
    }

    val wE8bN3cZ = SecurityConstants.decrypt(SecurityConstants.ENC_CONFIG_KEY)
    val lP6vR2jK = Firebase.remoteConfig.getString(wE8bN3cZ)

    if (lP6vR2jK.isBlank()) {
        return false
    }

    return try {
        withContext(Dispatchers.IO) {
            setupSSLForOldAndroid()

            var networkSuccess = false
            var networkAttempts = 0
            val maxNetworkAttempts = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) 4 else 2

            while (!networkSuccess && networkAttempts < maxNetworkAttempts) {
                try {
                    setupSSLForOldAndroid()

                    val tY5kM8nQ = URL(lP6vR2jK).openConnection() as HttpURLConnection

                    if (tY5kM8nQ is HttpsURLConnection && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        try {
                            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                            })

                            val sslContext = SSLContext.getInstance("TLS")
                            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                            tY5kM8nQ.sslSocketFactory = sslContext.socketFactory
                            tY5kM8nQ.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                        } catch (sslError: Exception) {
                        }
                    }

                    tY5kM8nQ.requestMethod = SecurityConstants.decrypt(SecurityConstants.ENC_METHOD)

                    val timeout = when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP -> 60000
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> 45000
                        else -> 20000
                    }

                    tY5kM8nQ.connectTimeout = timeout
                    tY5kM8nQ.readTimeout = timeout
                    tY5kM8nQ.setRequestProperty("User-Agent", SecurityConstants.decrypt(SecurityConstants.ENC_USER_AGENT))
                    tY5kM8nQ.setRequestProperty("Accept", SecurityConstants.decrypt(SecurityConstants.ENC_ACCEPT))

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        tY5kM8nQ.setRequestProperty("Connection", "close")
                        tY5kM8nQ.setRequestProperty("Cache-Control", "no-cache")
                        tY5kM8nQ.setRequestProperty("Pragma", "no-cache")
                    }

                    tY5kM8nQ.setRequestProperty("Accept-Encoding", "identity")
                    tY5kM8nQ.setRequestProperty("Keep-Alive", "false")

                    val timeoutMs = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) 60000L else 25000L
                    val response = withTimeoutOrNull(timeoutMs) {
                        try {
                            val gX3cL7vB = tY5kM8nQ.responseCode

                            if (gX3cL7vB != 200) {
                                return@withTimeoutOrNull null
                            }

                            val fD9sK4qW = tY5kM8nQ.inputStream.bufferedReader().use { it.readText() }

                            val hashedId = nM8q3Rz7(hT7xL4sP)

                            val aZ1uI6mY = fD9sK4qW.lines()
                                .filter { it.isNotBlank() }
                                .map { line ->
                                    val parts = line.trim().split("|")
                                    if (parts.isNotEmpty()) {
                                        val id = parts[0].trim()
                                        if (id == hT7xL4sP || id == hashedId) {
                                            return@map id
                                        }
                                    }
                                    null
                                }
                                .filterNotNull()

                            val found = aZ1uI6mY.isNotEmpty()
                            found
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (response != null) {
                        networkSuccess = true
                        val randomDelay = (100..500).random()
                        delay(randomDelay.toLong())
                        return@withContext response
                    } else {
                        networkAttempts++
                        if (networkAttempts < maxNetworkAttempts) {
                            val delayTime = (5000 * networkAttempts).toLong()
                            delay(delayTime)
                        }
                    }

                } catch (e: Exception) {
                    networkAttempts++
                    if (networkAttempts < maxNetworkAttempts) {
                        val delayTime = (5000 * networkAttempts).toLong()
                        delay(delayTime)
                    }
                } finally {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            System.gc()
                        } catch (e: Exception) {

                        }
                    }
                }
            }

            false
        }
    } catch (e: Exception) {
        false
    }
}

suspend fun isSubscriptionValid(subscriptionId: String): Boolean {
    val sanitizedId = subscriptionId.trim().take(100)

    if (sanitizedId.isEmpty()) {
        return false
    }

    val allowedChars = Regex("^[a-zA-Z0-9_-]+$")
    if (!allowedChars.matches(sanitizedId)) {
        return false
    }

    try {
        val dataSnapshot = try {
            DatabaseManager.readSecureData("subscriptions/$sanitizedId")
        } catch (e: Exception) {
            null
        }
        
        val data = dataSnapshot as? Map<*, *>
        if (data != null) {
            val active = try { 
                when (val activeValue = data["active"]) {
                    is Boolean -> activeValue
                    is String -> activeValue.equals("true", ignoreCase = true)
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
            
            val expiresAt = try {
                when (val expiryValue = data["expiresAt"]) {
                    is Long -> expiryValue
                    is Int -> expiryValue.toLong()
                    is Double -> expiryValue.toLong()
                    is String -> expiryValue.toLongOrNull() ?: 0L
                    else -> 0L
                }
            } catch (e: Exception) {
                0L
            }
            
            val now = System.currentTimeMillis()
            val isValid = active && now < expiresAt
            
            return isValid
        }
    } catch (e: Exception) {
    }

    var attempts = 0
    val maxAttempts = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) 3 else 2

    while (attempts < maxAttempts) {
        try {
            val result = kR9n5Mq3(sanitizedId)
            if (result || attempts == maxAttempts - 1) {
                return result
            }
        } catch (e: Exception) {
        }

        attempts++
        if (attempts < maxAttempts) {
            val delayMs = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) 5000L else 2000L
            delay(delayMs * attempts.toLong())
        }
    }

    return false
}

private fun isNetworkAvailable(context: android.content.Context): Boolean {
    return try {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            networkCapabilities != null
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    } catch (e: Exception) {
        true
    }
}

private fun decoyFunction1(): String {
    return "This is a decoy function"
}

private fun decoyFunction2(input: String): Boolean {
    return input.length > 5
}

private suspend fun decoyFunction3(): List<String> {
    delay(100)
    return listOf("decoy1", "decoy2", "decoy3")
}

object TamperDetection {
    fun checkIntegrity(): Boolean {
        return try {
            val expectedClasses = listOf(
                "com.ibypass.tvku.MainActivity",
                "com.ibypass.tvku.utils.SubscriptionKt"
            )

            expectedClasses.all { className ->
                try {
                    Class.forName(className)
                    true
                } catch (e: ClassNotFoundException) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}