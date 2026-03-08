package com.ibypass.tvku.utils

import android.content.Context
import android.os.Build
import kotlinx.coroutines.delay

/**
 * Subscription system disabled.
 * Semua fungsi selalu mengembalikan TRUE supaya aplikasi langsung berjalan.
 */

suspend fun isSubscriptionValid(subscriptionId: String): Boolean {
    return true
}

/**
 * Fungsi lama diganti dengan dummy
 */
suspend fun kR9n5Mq3(id: String): Boolean {
    return true
}

/**
 * Network check dummy
 */
fun isNetworkAvailable(context: Context): Boolean {
    return true
}

/**
 * Tamper detection dimatikan supaya tidak memblokir aplikasi
 */
object TamperDetection {
    fun checkIntegrity(): Boolean {
        return true
    }
}