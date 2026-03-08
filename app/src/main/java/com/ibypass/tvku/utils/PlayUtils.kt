package com.ibypass.tvku.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.ibypass.tvku.Channel
import com.ibypass.tvku.PlayerActivity

fun isValidStreamUrl(url: String): Boolean {
    return try {
        val uri = Uri.parse(url)
        uri.scheme in listOf("http", "https", "rtmp", "rtsp") &&
                !uri.host.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }
}

fun Channel.buildHeaders(): Map<String, String> {
    val headers = mutableMapOf<String, String>()

    userAgent?.let { headers["user-agent"] = it }
    referer?.let { headers["referer"] = it }

    drmType?.let { headers["drm_type"] = it.lowercase() }
    drmKey?.let { headers["drm_key"] = it }

    return headers
}

@androidx.annotation.OptIn(UnstableApi::class)
fun playChannel(context: Context, channel: Channel) {
    if (!isValidStreamUrl(channel.url)) {
        Toast.makeText(context, "Invalid stream URL for ${channel.name}", Toast.LENGTH_LONG).show()
        return
    }

    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("video_url", channel.url)
        putExtra("channel_name", channel.name)

        // Inject headers dari channel info
        channel.buildHeaders().forEach { (key, value) ->
            putExtra("header_$key", value)
        }
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

