package com.ibypass.tvku.utils

import android.util.Base64
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private object CryptoManager {
    private const val ENC_MASTER_KEY = "RmlyZWJhc2VNYW5hZ2VyS2V5MjAyNQ=="
    private const val ENC_MASTER_IV = "RmlyZWJhc2VJbml0VmVjdG9y"

    internal const val ENC_PLAYLIST_KEY = "cGxheWxpc3RfdXJs"
    internal const val ENC_HTTP_PREFIX = "aHR0cA=="
    internal const val ENC_HTTPS_PREFIX = "aHR0cHM="

    fun decryptBase64(encryptedData: String): String {
        return try {
            String(Base64.decode(encryptedData, Base64.DEFAULT))
        } catch (e: Exception) {
            ""
        }
    }

    private fun getMasterKey(): ByteArray {
        val keyString = decryptBase64(ENC_MASTER_KEY)
        return MessageDigest.getInstance("SHA-256").digest(keyString.toByteArray())
    }

    private fun getMasterIV(): ByteArray {
        val ivString = decryptBase64(ENC_MASTER_IV)
        return ivString.toByteArray().copyOf(16)
    }

    fun advancedDecrypt(encryptedData: String): String {
        return try {
            val key = getMasterKey()
            val iv = getMasterIV()

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT))
            String(decryptedBytes)
        } catch (e: Exception) {
            encryptedData
        }
    }
}

private object SecurityUtil {
    fun checkRuntimeSecurity(): Boolean {
        return try {
            val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic") ||
                    android.os.Build.FINGERPRINT.startsWith("unknown") ||
                    android.os.Build.MODEL.contains("google_sdk") ||
                    android.os.Build.MODEL.contains("Emulator") ||
                    android.os.Build.MODEL.contains("Android SDK built for x86")

            val isDebugging = android.os.Debug.isDebuggerConnected()

            !(isEmulator || isDebugging)
        } catch (e: Exception) {
            true
        }
    }

    fun generateChecksum(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

object qL8wN3kR {

    private const val mF7xC2vB = 3600L
    private const val pJ9sA4nM = 0L

    private val dummyList = listOf("config_a", "config_b", "config_c")
    private val dummyMap = mapOf("key1" to "value1", "key2" to "value2")

    fun xT5nK8qW(bR4vL6mZ: Boolean = false, uY2cF9sQ: (String?) -> Unit) {

        if (!SecurityUtil.checkRuntimeSecurity()) {
            uY2cF9sQ(null)
            return
        }

        val dummyOp1 = dummyList.map { SecurityUtil.generateChecksum(it) }
        val dummyOp2 = dummyMap.keys.joinToString("-")

        val rC8nL5vX = Firebase.remoteConfig

        val wE9kM7tQ = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (bR4vL6mZ) pJ9sA4nM else mF7xC2vB
        }
        rC8nL5vX.setConfigSettingsAsync(wE9kM7tQ)

        val randomDelay = (50..200).random()
        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(randomDelay.toLong())

            rC8nL5vX.fetchAndActivate()
                .addOnCompleteListener { gH6dP3zN ->
                    if (gH6dP3zN.isSuccessful) {

                        val decryptedKey = CryptoManager.decryptBase64(CryptoManager.ENC_PLAYLIST_KEY)
                        val jQ4vR8nK = rC8nL5vX.getString(decryptedKey).trim()

                        if (validateUrlResponse(jQ4vR8nK)) {
                            val checksumInput = "$jQ4vR8nK${System.currentTimeMillis()}"
                            val checksumResult = SecurityUtil.generateChecksum(checksumInput)

                            uY2cF9sQ(jQ4vR8nK)
                        } else {
                            uY2cF9sQ(null)
                        }
                    } else {
                        uY2cF9sQ(null)
                    }
                }
        }
    }

    private fun validateUrlResponse(nL6kT9mX: String): Boolean {
        if (nL6kT9mX.isEmpty()) return false

        val httpPrefix = CryptoManager.decryptBase64(CryptoManager.ENC_HTTP_PREFIX)
        val httpsPrefix = CryptoManager.decryptBase64(CryptoManager.ENC_HTTPS_PREFIX)

        val isValidUrl = nL6kT9mX.startsWith(httpPrefix) || nL6kT9mX.startsWith(httpsPrefix)

        val containsSuspiciousChars = nL6kT9mX.contains(Regex("[<>\"'`]"))
        val isReasonableLength = nL6kT9mX.length in 10..2048

        return isValidUrl && !containsSuspiciousChars && isReasonableLength
    }

    private fun decoyConfigFetch(): String {
        return "decoy_config_value"
    }

    private fun decoyValidation(input: String): Boolean {
        return input.length > 5 && input.contains("://")
    }
}

object FirebaseManager {
    
    private const val TAG = "FirebaseManager"

    init {
        if (!SecurityUtil.checkRuntimeSecurity()) {
            throw SecurityException("Runtime security check failed")
        }
    }

    fun fetchPlaylistUrl(forceFetch: Boolean = false, onResult: (String?) -> Unit) {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                onResult(null)
                return
            }
            
            val sanitizedForceFetch = forceFetch

            qL8wN3kR.xT5nK8qW(sanitizedForceFetch) { result ->
                val validatedResult = if (result != null && result.isNotBlank()) {
                    if (result.startsWith("http")) result else null
                } else {
                    null
                }

                onResult(validatedResult)
            }

        } catch (e: Exception) {
            onResult(null)
        }
    }

    fun getConfigVersion(): String {
        return "1.0.0"
    }

    fun isConfigValid(): Boolean {
        return true
    }
}

private class ConfigValidator {
    companion object {
        fun validateResponse(response: String?): Boolean {
            return response?.let {
                it.isNotEmpty() && it.length < 1000
            } ?: false
        }
    }
}

private object NetworkHelper {
    fun sanitizeUrl(url: String): String {
        return url.trim().take(500)
    }
}