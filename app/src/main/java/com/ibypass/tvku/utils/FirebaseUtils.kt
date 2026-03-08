package com.ibypass.tvku.utils

import android.content.Context
import android.util.Log
import com.ibypass.tvku.screens.generateSubscriptionId
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "FirebaseUtils"

suspend fun isSubscriptionActive(subscriptionId: String): Boolean {
    try {
        if (subscriptionId.isBlank()) {
            return false
        }
        
        val data = try {
            DatabaseManager.readSecureData("subscriptions/$subscriptionId") as? Map<*, *>
        } catch (e: Exception) {
            null
        }
        
        if (data == null) {
            return false
        }

        val active = try {
            when (val activeValue = data["active"]) {
                is Boolean -> activeValue
                is String -> activeValue.equals("true", ignoreCase = true)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
        
        if (!active) {
            return false
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
        
        if (expiresAt <= 0) {
            return false
        }

    val now = System.currentTimeMillis()
        val isValid = active && now < expiresAt
        
        return isValid
    } catch (e: Exception) {
        return false
    }
}

suspend fun getSubscriptionExpiryText(subscriptionId: String): String {
    return try {
        if (subscriptionId.isBlank()) {
            return "Free trial (tidak ada langganan)"
        }
        
        val data = try {
            DatabaseManager.readSecureData("subscriptions/$subscriptionId") as? Map<*, *>
        } catch (e: Exception) {
            null
        }
        
        if (data == null) {
            return "Free trial (tidak ada langganan)"
        }

        val active = try {
            when (val activeValue = data["active"]) {
                is Boolean -> activeValue
                is String -> activeValue.equals("true", ignoreCase = true)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
        
        if (!active) {
            return "Free trial (tidak ada langganan)"
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
        
        if (expiresAt <= 0) {
            return "Free trial (tidak ada langganan)"
        }

        val now = System.currentTimeMillis()
        val millisLeft = expiresAt - now

        if (millisLeft <= 0) {
            return "Langganan kadaluarsa"
        } else {
            val text = formatExpiryTimeWithDate(expiresAt, millisLeft)
            return text
        }
    } catch (e: Exception) {
        return "Free trial (tidak ada langganan)"
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val date = Date(timestamp)
        SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale("id")).format(date)
    } catch (e: Exception) {
        timestamp.toString()
    }
}

private fun formatExpiryTimeWithDate(expiresAt: Long, millisLeft: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = expiresAt

    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    val exactDate = dateFormat.format(calendar.time)

    val daysLeft = (millisLeft / (1000 * 60 * 60 * 24)).toInt()
    val hoursLeft = ((millisLeft % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
    val minutesLeft = ((millisLeft % (1000 * 60 * 60)) / (1000 * 60)).toInt()

    return when {
        daysLeft > 30 -> {
            val monthsLeft = daysLeft / 30
            val remainingDays = daysLeft % 30
            if (remainingDays > 0) {
                "Aktif ${monthsLeft} bulan ${remainingDays} hari lagi\nBerakhir: $exactDate"
            } else {
                "Aktif ${monthsLeft} bulan lagi\nBerakhir: $exactDate"
            }
        }

        daysLeft > 7 -> {
            val weeksLeft = daysLeft / 7
            val remainingDays = daysLeft % 7
            if (remainingDays > 0) {
                "Aktif ${weeksLeft} minggu ${remainingDays} hari lagi\nBerakhir: $exactDate"
            } else {
                "Aktif ${weeksLeft} minggu lagi\nBerakhir: $exactDate"
            }
        }

        daysLeft > 0 -> {
            if (hoursLeft > 0) {
                "Aktif ${daysLeft} hari ${hoursLeft} jam lagi\nBerakhir: $exactDate"
            } else {
                "Aktif ${daysLeft} hari lagi\nBerakhir: $exactDate"
            }
        }

        hoursLeft > 0 -> {
            if (minutesLeft > 0) {
                "Aktif ${hoursLeft} jam ${minutesLeft} menit lagi\nBerakhir: $exactDate"
            } else {
                "Aktif ${hoursLeft} jam lagi\nBerakhir: $exactDate"
            }
        }

        minutesLeft > 0 -> {
            "⚠️ Aktif ${minutesLeft} menit lagi\nBerakhir: $exactDate"
        }

        else -> {
            "🚨 Aktif kurang dari 1 menit lagi\nBerakhir: $exactDate"
        }
    }
}

suspend fun getSubscriptionStatusColor(subscriptionId: String): SubscriptionColor {
    return try {
        val data = DatabaseManager.readSecureData("subscriptions/$subscriptionId") as? Map<*, *>
            ?: return SubscriptionColor.INACTIVE

        val active = data["active"] as? Boolean ?: false
        val expiresAt = data["expiresAt"] as? Long ?: return SubscriptionColor.INACTIVE

        if (!active) return SubscriptionColor.INACTIVE

        val now = System.currentTimeMillis()
        val millisLeft = expiresAt - now

        if (millisLeft <= 0) {
            SubscriptionColor.EXPIRED
        } else {
            val daysLeft = (millisLeft / (1000 * 60 * 60 * 24)).toInt()
            val hoursLeft = ((millisLeft % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()

            when {
                daysLeft == 0 && hoursLeft <= 1 -> SubscriptionColor.CRITICAL
                daysLeft == 0 -> SubscriptionColor.WARNING
                daysLeft <= 3 -> SubscriptionColor.CAUTION
                daysLeft <= 7 -> SubscriptionColor.ACTIVE_WARNING
                else -> SubscriptionColor.ACTIVE
            }
        }
    } catch (e: Exception) {
        SubscriptionColor.INACTIVE
    }
}

enum class SubscriptionColor {
    ACTIVE,
    ACTIVE_WARNING,
    CAUTION,
    WARNING,
    CRITICAL,
    EXPIRED,
    INACTIVE
}

suspend fun getExactExpiryDate(subscriptionId: String): String {
    return try {
        val data = DatabaseManager.readSecureData("subscriptions/$subscriptionId") as? Map<*, *>
            ?: return "Tidak ada tanggal berakhir"

        val expiresAt = data["expiresAt"] as? Long
            ?: return "Tidak ada tanggal berakhir"

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = expiresAt

        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy 'pukul' HH:mm", Locale("id", "ID"))
        dateFormat.format(calendar.time)
    } catch (e: Exception) {
        "Tidak ada tanggal berakhir"
    }
}

fun getOrCreateSubscriptionId(context: Context): String {
    val prefs = context.getSharedPreferences("tvku_settings", Context.MODE_PRIVATE)
    val existingId = prefs.getString("subscription_id", null)

    return if (existingId != null && existingId.isNotBlank()) {
        existingId
    } else {
        val newId = generateSubscriptionId()
        prefs.edit().putString("subscription_id", newId).apply()
        newId
    }
}
