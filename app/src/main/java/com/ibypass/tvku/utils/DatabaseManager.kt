package com.ibypass.tvku.utils

import android.util.Base64
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.google.firebase.auth.FirebaseAuth

object DatabaseManager {
    private const val TAG = "DatabaseManager"
    private const val DB_KEY = "VHZrdURhdGFiYXNlS2V5MjAyNQ=="
    private const val DB_IV = "VHZrdUluaXRWZWN0b3I="

    private val database: FirebaseDatabase by lazy {
        try {
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            throw e
        }
    }

    private fun getMasterKey(): ByteArray {
        val keyString = String(Base64.decode(DB_KEY, Base64.NO_WRAP))
        return MessageDigest.getInstance("SHA-256").digest(keyString.toByteArray())
    }

    private fun getMasterIV(): ByteArray {
        val ivString = String(Base64.decode(DB_IV, Base64.NO_WRAP))
        return ivString.toByteArray().copyOf(16)
    }

    private fun encrypt(data: String): String {
        try {
            val key = getMasterKey()
            val iv = getMasterIV()

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(data.toByteArray())
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun decrypt(encryptedData: String): String {
        try {
            if (encryptedData.isBlank()) {
                return ""
            }

            if (!isValidBase64(encryptedData)) {
                return encryptedData
            }

            val key = getMasterKey()
            val iv = getMasterIV()

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decodedBytes = try {
                Base64.decode(encryptedData, Base64.NO_WRAP)
            } catch (e: Exception) {
                return encryptedData
            }
            
            val decryptedBytes = cipher.doFinal(decodedBytes)
            return String(decryptedBytes)
        } catch (e: Exception) {
            return encryptedData
        }
    }

    private fun isValidBase64(input: String): Boolean {
        val base64Pattern = "^[A-Za-z0-9+/]*={0,2}$"

        val cleaned = input.trim()

        if (cleaned.length < 4) {
            return false
        }

        if (cleaned == "true" || cleaned == "false" || cleaned.matches(Regex("^[0-9]+$"))) {
            return false
        }

        return try {
            cleaned.matches(Regex(base64Pattern))
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readSecureData(path: String): Any? {
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                return null
            }
            
            val snapshot = database.reference.child(path).get().await()
            val data = snapshot.value
            
            when (data) {
                is String -> {
                    try {
                        if (isValidBase64(data)) {
                            decrypt(data)
                        } else {
                            data
                        }
                    } catch (e: Exception) {
                        data
                    }
                }
                is Map<*, *> -> {
                    try {
                        val nonEncryptedFields = setOf(
                            "active", "expiresAt", "createdAt", "lastUpdated", 
                            "timestamp", "count", "id", "uid", "status", "price",
                            "note"
                        )
                        
                        data.mapValues { (key, value) ->
                            if (value is String && !nonEncryptedFields.contains(key)) {
                                try {
                                    if (isValidBase64(value) && value.length > 8) {
                                        decrypt(value)
                                    } else {
                                        value
                                    }
                                } catch (e: Exception) {
                                    value
                                }
                            } else {
                                value
                            }
                        }
                    } catch (e: Exception) {
                        data
                    }
                }
                else -> data
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun writeSecureData(path: String, data: Any) {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                throw SecurityException("User not authenticated")
            }
            
            val encryptedData = when (data) {
                is String -> encrypt(data)
                is Map<*, *> -> data.mapValues { (_, value) ->
                    if (value is String) encrypt(value) else value
                }
                else -> data
            }

            database.reference.child(path).setValue(encryptedData).await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun setSecurityRules() {
        val rules = """
        {
          "rules": {
            ".read": false,
            ".write": false,
            "subscriptions": {
              "${'$'}uid": {
                ".read": "auth != null && auth.uid == ${'$'}uid",
                ".write": false
              },
              ".read": true
            },
            "fcm_tokens": {
              "${'$'}token_id": {
                ".read": false,
                ".write": "auth != null && newData.exists()"
              }
            },
            "app_update": {
              ".read": true,
              ".write": false
            }
          }
        }
        """
        
        try {
            database.reference.root.setValue(rules)
        } catch (e: Exception) {

        }
    }

    fun validateDatabaseAccess(): Boolean {
        return try {
            val key = getMasterKey()
            val iv = getMasterIV()
            key.isNotEmpty() && iv.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
} 