package com.ibypass.tvku

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.drm.*
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var videoUrl: String? = null
    private var channelName: String? = null
    private var headers: Map<String, String> = emptyMap()

    var trackSelector: DefaultTrackSelector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!VpnHelper.isNetworkSecure(this)) {
            Toast.makeText(this,"Akses ditolak",Toast.LENGTH_LONG).show()
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        videoUrl = intent.getStringExtra("video_url")
        channelName = intent.getStringExtra("channel_name") ?: "Channel"

        val headerMap = mutableMapOf<String,String>()

        intent.extras?.keySet()?.forEach { key ->
            if (key.startsWith("header_")) {
                headerMap[key.removePrefix("header_")] =
                    intent.getStringExtra(key) ?: ""
            }
        }

        headers = headerMap

        setContent {
            PlayerScreen(
                videoUrl!!,
                channelName!!,
                headers,
                onPlayerReady = { exoPlayer = it },
                onExit = { finish() }
            )
        }
    }

    private fun setContent(content:@Composable ()->Unit){
        val composeView = ComposeView(this)
        composeView.setContent(content)
        setContentView(composeView)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}

@Composable
fun PlayerScreen(
    url:String,
    channel:String,
    headers:Map<String,String>,
    onPlayerReady:(ExoPlayer)->Unit,
    onExit:()->Unit
){

    AndroidView(
        factory = { context ->

            createSimplePlayerView(
                context,
                url,
                headers,
                onPlayerReady = onPlayerReady,
                onLoadingChanged = {},
                onError = {}
            )

        }
    )

}

@UnstableApi
private fun createSimplePlayerView(
    context:android.content.Context,
    videoUrl:String,
    headers:Map<String,String>,
    onPlayerReady:(ExoPlayer)->Unit,
    onLoadingChanged:(Boolean)->Unit,
    onError:(String)->Unit
):PlayerView{

    val playerView = PlayerView(context)

    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(headers["user-agent"] ?: "TvkuPlayer")
        .setAllowCrossProtocolRedirects(true)

    val dataSourceFactory =
        DefaultDataSource.Factory(context,httpFactory)

    val drmSessionManager =
        createDrmSessionManager(context,headers,httpFactory)

    val mediaSourceFactory = when{

        videoUrl.contains(".mpd",true) -> {
            DashMediaSource.Factory(dataSourceFactory)
        }

        videoUrl.contains(".m3u8",true) -> {
            HlsMediaSource.Factory(dataSourceFactory)
        }

        else -> {
            ProgressiveMediaSource.Factory(dataSourceFactory)
        }

    }.setDrmSessionManagerProvider { drmSessionManager }

    val trackSelector = DefaultTrackSelector(context)

    val player = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    val mediaItem = createMediaItemWithDrm(
        videoUrl,
        headers,
        context
    )

    player.setMediaItem(mediaItem)
    player.prepare()
    player.playWhenReady = true

    playerView.player = player

    onPlayerReady(player)

    return playerView
}

@UnstableApi
private fun createDrmSessionManager(
    context:android.content.Context,
    headers:Map<String,String>,
    httpFactory:DefaultHttpDataSource.Factory
):DrmSessionManager{

    val drmType = headers["drm_type"]
    val drmKey = headers["drm_key"] ?: headers["license_url"]

    if(drmType.isNullOrEmpty() || drmKey.isNullOrEmpty()){
        return DrmSessionManager.DRM_UNSUPPORTED
    }

    return try{

        val callback:MediaDrmCallback = when{

            drmType.contains("clearkey",true)->{

                val json = convertHexToJson(drmKey)
                LocalMediaDrmCallback(json.toByteArray())

            }

            drmType.contains("widevine",true)->{

                HttpMediaDrmCallback(drmKey,httpFactory)

            }

            else -> return DrmSessionManager.DRM_UNSUPPORTED
        }

        val uuid = when{

            drmType.contains("clearkey",true)-> C.CLEARKEY_UUID

            drmType.contains("widevine",true)-> C.WIDEVINE_UUID

            else -> return DrmSessionManager.DRM_UNSUPPORTED
        }

        DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(
                uuid,
                FrameworkMediaDrm.DEFAULT_PROVIDER
            )
            .build(callback)

    }catch(e:Exception){

        DrmSessionManager.DRM_UNSUPPORTED

    }

}

private fun convertHexToJson(input:String):String{

    val parts = input.split(":")
    if(parts.size!=2)return "{}"

    val kid = hexToBase64(parts[0])
    val key = hexToBase64(parts[1])

    return """
        {
         "keys":[
          {
           "kty":"oct",
           "kid":"$kid",
           "k":"$key"
          }
         ]
        }
    """.trimIndent()

}

private fun hexToBase64(hex:String):String{

    val bytes = hex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    return android.util.Base64.encodeToString(
        bytes,
        android.util.Base64.URL_SAFE or
        android.util.Base64.NO_PADDING
    )

}

@UnstableApi
private fun createMediaItemWithDrm(
    url:String,
    headers:Map<String,String>,
    context:android.content.Context
):MediaItem{

    val drmType = headers["drm_type"]
    val drmLicense = headers["drm_key"] ?: headers["license_url"]

    if(drmType.isNullOrEmpty() || drmLicense.isNullOrEmpty()){
        return MediaItem.fromUri(Uri.parse(url))
    }

    val uuid = when{

        drmType.contains("clearkey",true)-> C.CLEARKEY_UUID

        drmType.contains("widevine",true)-> C.WIDEVINE_UUID

        else -> return MediaItem.fromUri(Uri.parse(url))
    }

    val drmConfig = MediaItem.DrmConfiguration.Builder(uuid)
        .setLicenseUri(drmLicense)
        .setForceDefaultLicenseUri(true)
        .build()

    return MediaItem.Builder()
        .setUri(Uri.parse(url))
        .setDrmConfiguration(drmConfig)
        .build()

}