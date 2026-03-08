package com.ibypass.tvku.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RulesManager {
    private const val PREFS_NAME = "tvku_rules"
    private const val KEY_RULES = "encrypted_rules"
    private const val KEY_LAST_UPDATE = "last_rules_update"
    private const val KEY_VERSION = "rules_version"
    private const val CURRENT_VERSION = 2

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun migratePrefsIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val version = prefs.getInt(KEY_VERSION, 0)
        
        if (version < CURRENT_VERSION) {
            prefs.edit()
                .clear()
                .putInt(KEY_VERSION, CURRENT_VERSION)
                .apply()
        }
    }

    suspend fun initializeRules(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                migratePrefsIfNeeded(context)
                
                val prefs = getPrefs(context)
                val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastUpdate

                if (timeDiff > 24 * 60 * 60 * 1000 || !prefs.contains(KEY_RULES)) {
                    val encryptedRules = SecurityManager.generateSecureRules(read = true, write = false)
                    if (encryptedRules.isBlank()) {
                        throw Exception("Rules tidak valid")
                    }

                    val decryptedRules = SecurityManager.decryptRules(encryptedRules)
                    if (decryptedRules.isBlank()) {
                        throw Exception("Rules tidak valid")
                    }
                    
                    if (!decryptedRules.contains("\"rules\"")) {
                        throw Exception("Rules tidak valid")
                    }

                    prefs.edit()
                        .putString(KEY_RULES, encryptedRules)
                        .putLong(KEY_LAST_UPDATE, currentTime)
                        .putInt(KEY_VERSION, CURRENT_VERSION)
                        .apply()
                }
            } catch (e: Exception) {
                getPrefs(context).edit()
                    .clear()
                    .putInt(KEY_VERSION, CURRENT_VERSION)
                    .apply()
                
                throw e
            }
        }
    }

    suspend fun getRules(context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = getPrefs(context)
                val encryptedRules = prefs.getString(KEY_RULES, null)
                
                if (!encryptedRules.isNullOrBlank()) {
                    val decrypted = SecurityManager.decryptRules(encryptedRules)
                    if (decrypted.isBlank()) {
                        throw Exception("Rules tidak valid")
                    }
                    decrypted
                } else {
                    val newRules = SecurityManager.generateSecureRules(read = true, write = false)
                    if (newRules.isBlank()) {
                        throw Exception("Rules tidak valid")
                    }

                    prefs.edit()
                        .putString(KEY_RULES, newRules)
                        .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                        .putInt(KEY_VERSION, CURRENT_VERSION)
                        .apply()
                    
                    val decrypted = SecurityManager.decryptRules(newRules)
                    if (decrypted.isBlank()) {
                        throw Exception("Rules tidak valid")
                    }
                    decrypted
                }
            } catch (e: Exception) {
                getPrefs(context).edit()
                    .clear()
                    .putInt(KEY_VERSION, CURRENT_VERSION)
                    .apply()
                
                throw e
            }
        }
    }

    suspend fun validateRules(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val rules = getRules(context)
                
                if (rules.isBlank()) {
                    return@withContext false
                }

                if (!rules.contains("\"rules\"")) {
                    return@withContext false
                }

                true
            } catch (e: Exception) {
                getPrefs(context).edit()
                    .clear()
                    .putInt(KEY_VERSION, CURRENT_VERSION)
                    .apply()
                
                false
            }
        }
    }
} 