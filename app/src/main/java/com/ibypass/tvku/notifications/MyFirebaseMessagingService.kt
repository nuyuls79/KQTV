package com.ibypass.tvku.notifications

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ibypass.tvku.utils.DatabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        saveTokenToDatabase(token)

        FirebaseMessaging.getInstance().subscribeToTopic("all")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                } else {
                }
            }
    }

    private fun saveTokenToDatabase(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = "device_1"
                val data = mapOf(
                    "token" to token,
                    "timestamp" to System.currentTimeMillis()
                )

                DatabaseManager.writeSecureData("fcm_tokens/$deviceId", data)
            } catch (e: Exception) {
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
    }
}
