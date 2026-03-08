package com.ibypass.tvku.utils

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityManager {
    private const val TAG = "SecurityManager"
    
    // Kunci statis untuk debugging
    private const val DEBUG_RULES = """
        {
            "rules": {
                ".read": false,
                ".write": false,
                "fcm_tokens": {
                    ".read": true,
                    ".write": true,
                    "${'$'}deviceId": {
                        ".validate": "newData.hasChildren(['token', 'timestamp'])"
                    }
                },
                "subscriptions": {
                    ".read": true,
                    ".write": false,
                    ".indexOn": ["active", "expiresAt"],
                    "${'$'}subscriptionId": {
                        ".validate": "newData.hasChildren(['active', 'expiresAt'])",
                        "active": {
                            ".validate": "newData.isBoolean()"
                        },
                        "expiresAt": {
                            ".validate": "newData.isNumber()"
                        }
                    }
                }
            }
        }
    """

    private const val ENC_MASTER_KEY = "VFZLdVNlY3VyZUtleTIwMjU="
    private const val ENC_MASTER_IV = "VFZLdUluaXRWZWN0b3I="

    private fun decryptBase64(encryptedData: String): String {
        return try {
            if (encryptedData.isBlank()) {
                return ""
            }
            
            // Validate base64 pattern before decoding
            val pattern = "^[A-Za-z0-9+/]*={0,2}$"
            if (!encryptedData.matches(Regex(pattern))) {
                return ""
            }
            
            val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)
            String(decoded)
        } catch (e: Exception) {
            ""
        }
    }

    private fun encryptBase64(data: ByteArray): String {
        return try {
            if (data.isEmpty()) {
                return ""
            }
            val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
            encoded
        } catch (e: Exception) {
            ""
        }
    }

    private fun getMasterKey(): ByteArray {
        val keyString = decryptBase64(ENC_MASTER_KEY)
        if (keyString.isBlank()) {
            throw Exception("Master key tidak valid")
        }
        val key = MessageDigest.getInstance("SHA-256").digest(keyString.toByteArray())
        return key
    }

    private fun getMasterIV(): ByteArray {
        val ivString = decryptBase64(ENC_MASTER_IV)
        if (ivString.isBlank()) {
            throw Exception("Master IV tidak valid")
        }
        val iv = ivString.toByteArray().copyOf(16)
        return iv
    }

    fun generateSecureRules(read: Boolean = true, write: Boolean = false): String {
        try {
            // Untuk debugging, gunakan rules statis
            val rules = DEBUG_RULES.trimIndent()

            if (!rules.contains("\"rules\"")) {
                throw Exception("Format rules tidak valid")
            }

            val key = getMasterKey()
            val iv = getMasterIV()

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            
            val rulesBytes = rules.toByteArray(Charsets.UTF_8)
            
            val encryptedBytes = cipher.doFinal(rulesBytes)
            
            val encoded = encryptBase64(encryptedBytes)
            
            if (encoded.isBlank()) {
                throw Exception("Hasil enkripsi kosong")
            }

            return encoded
        } catch (e: Exception) {
            throw e
        }
    }

    fun decryptRules(encryptedRules: String): String {
        try {
            if (encryptedRules.isBlank()) {
                return ""
            }

            // Validate base64 pattern
            val pattern = "^[A-Za-z0-9+/]*={0,2}$"
            if (!encryptedRules.matches(Regex(pattern))) {
                return ""
            }

            val key = getMasterKey()
            val iv = getMasterIV()

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            val decodedBytes = try {
                Base64.decode(encryptedRules, Base64.NO_WRAP)
            } catch (e: Exception) {
                return ""
            }
            
            val decryptedBytes = try {
                cipher.doFinal(decodedBytes)
            } catch (e: Exception) {
                return ""
            }
            
            val decrypted = String(decryptedBytes, Charsets.UTF_8)

            if (!decrypted.contains("\"rules\"")) {
                return ""
            }

            return decrypted
        } catch (e: Exception) {
            return ""
        }
    }
} 