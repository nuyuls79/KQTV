package com.ibypass.tvku

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import org.json.JSONObject

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var videoUrl: String? = null
    private var channelName: String? = null
    private var headers: Map<String, String> = emptyMap()
    var trackSelector: DefaultTrackSelector? = null
    private var currentQuality: QualityOption? = null

    private val fullscreenHandler = Handler(Looper.getMainLooper())
    private var isActivityVisible = false

    companion object {
        private const val TAG = "PlayerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // PERBAIKAN DRM: Tambahkan FLAG_SECURE agar sistem mengizinkan rendering konten terlindungi
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        
        if (!VpnHelper.isNetworkSecure(this)) {
            Toast.makeText(this, "Akses ditolak karena alasan keamanan", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        forceHideSystemUI()
        setTheme(android.R.style.Theme_NoTitleBar_Fullscreen)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isActivityVisible = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        videoUrl = intent.getStringExtra("video_url")?.let { url ->
            VpnHelper.unprotectUrl(url)
        }
        channelName = intent.getStringExtra("channel_name") ?: "Unknown Channel"

        val headerMap = mutableMapOf<String, String>()
        intent.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                if (key.startsWith("header_")) {
                    val headerKey = key.removePrefix("header_")
                    val headerValue = bundle.getString(key)
                    if (headerValue != null) {
                        headerMap[headerKey] = headerValue
                    }
                }
            }
        }

        // PERBAIKAN: Tambahkan mapping khusus untuk drm_key dan drm_type jika ada di extras langsung
        intent.getStringExtra("drm_key")?.let {
            headerMap["drm_key"] = it
            Log.d(TAG, "DRM Key from extras: ${it.take(20)}...")
        }
        intent.getStringExtra("drm_type")?.let {
            headerMap["drm_type"] = it
            Log.d(TAG, "DRM Type from extras: $it")
        }
        intent.getStringExtra("manifest_type")?.let {
            headerMap["manifest_type"] = it
        }

        val secureHeaders = VpnHelper.generateSecureHeaders(this)
        val combinedHeaders = mutableMapOf<String, String>()
        combinedHeaders.putAll(secureHeaders)
        combinedHeaders.putAll(headerMap)
        headers = combinedHeaders

        Log.d(TAG, "Final headers: ${headers.keys}")
        Log.d(TAG, "Video URL: $videoUrl")

        if (videoUrl.isNullOrEmpty()) {
            Toast.makeText(this, "URL tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            CustomPlayerScreen(
                videoUrl = videoUrl!!,
                channelName = channelName!!,
                headers = headers,
                onPlayerReady = { player -> exoPlayer = player },
                onError = { error ->
                    Toast.makeText(this@PlayerActivity, "Error: $error", Toast.LENGTH_LONG).show()
                },
                onQualityClick = { showQualityDialog() },
                onExitClick = { finish() }
            )
        }
    }

    private fun forceHideSystemUI() {
        try {
            supportActionBar?.hide()

            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            val uiOptions = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LOW_PROFILE)

            window.decorView.systemUiVisibility = uiOptions
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

        } catch (e: Exception) {
            Log.e(TAG, "Error hiding system UI", e)
        }
    }

    fun isDrmWidevineSupported(): Boolean {
        return try {
            val mediaDrm = FrameworkMediaDrm.newInstance(C.WIDEVINE_UUID)
            mediaDrm.release()
            true
        } catch (e: Exception) {
            androidx.appcompat.app.AlertDialog.Builder(this).apply {
                setTitle("Playback Error")
                setMessage("Device tidak support Widevine DRM")
                setCancelable(false)
                setPositiveButton("OK") { _, _ -> finish() }
                create()
                show()
            }
            false
        }
    }

    private fun showQualityDialog() {
        trackSelector?.let { selector ->
            val dialog = QualityDialog(
                context = this,
                trackSelector = selector,
                onQualityChanged = { quality ->
                    currentQuality = quality
                    Toast.makeText(this, "Kualitas diubah ke ${quality.name}", Toast.LENGTH_SHORT).show()
                }
            )

            dialog.setOnShowListener {
                forceHideSystemUI()
            }

            dialog.setOnDismissListener {
                forceHideSystemUI()
                if (isActivityVisible) {
                }
            }

            dialog.show()
        }
    }

    private fun setContent(content: @Composable () -> Unit) {
        val composeView = ComposeView(this)
        composeView.setContent(content)
        setContentView(composeView)
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        forceHideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            forceHideSystemUI()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        forceHideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityVisible = false
        exoPlayer?.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CustomPlayerScreen(
    videoUrl: String,
    channelName: String,
    headers: Map<String, String>,
    onPlayerReady: (ExoPlayer) -> Unit,
    onError: (String) -> Unit,
    onQualityClick: () -> Unit = {},
    onExitClick: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var showChannelInfo by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var interactionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var isLocked by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val focusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    val updateInteractionTimestamp = {
        interactionTimestamp = System.currentTimeMillis()
    }

    val showControlsAndUpdateTime = {
        showControls = true
        updateInteractionTimestamp()
    }

    LaunchedEffect(Unit) {
        delay(3000)
        showChannelInfo = false
        delay(500)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error requesting focus", e)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && !isLocked) {
            delay(100)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                focusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(interactionTimestamp, isLocked, showControls) {
        if (!isLocked && showControls) {
            delay(5000)
            val currentTime = System.currentTimeMillis()
            if (currentTime - interactionTimestamp >= 4800) {
                showControls = false
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer?.let { player ->
            while (true) {
                currentPosition = player.currentPosition
                duration = player.duration.takeIf { it > 0 } ?: 0L
                isPlaying = player.isPlaying
                delay(1000)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .focusRequester(focusRequester)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when {
                        !showControls && !isLocked -> {
                            when (keyEvent.key) {
                                Key.DirectionUp, Key.DirectionDown,
                                Key.DirectionLeft, Key.DirectionRight,
                                Key.Enter, Key.Spacebar -> {
                                    showControlsAndUpdateTime()
                                    true
                                }
                                Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> {
                                    exoPlayer?.let { player ->
                                        if (player.isPlaying) player.pause() else player.play()
                                    }
                                    showControlsAndUpdateTime()
                                    true
                                }
                                Key.MediaFastForward -> {
                                    exoPlayer?.let { player ->
                                        player.seekTo(minOf(player.duration, player.currentPosition + 10000))
                                    }
                                    showControlsAndUpdateTime()
                                    true
                                }
                                Key.MediaRewind -> {
                                    exoPlayer?.seekTo(maxOf(0, (exoPlayer?.currentPosition ?: 0) - 10000))
                                    showControlsAndUpdateTime()
                                    true
                                }
                                Key.Back -> {
                                    onExitClick()
                                    true
                                }
                                else -> {
                                    showControlsAndUpdateTime()
                                    true
                                }
                            }
                        }
                        showControls && !isLocked -> {
                            when (keyEvent.key) {
                                Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> {
                                    exoPlayer?.let { player ->
                                        if (player.isPlaying) player.pause() else player.play()
                                    }
                                    updateInteractionTimestamp()
                                    true
                                }
                                Key.MediaFastForward -> {
                                    exoPlayer?.let { player ->
                                        player.seekTo(minOf(player.duration, player.currentPosition + 10000))
                                    }
                                    updateInteractionTimestamp()
                                    true
                                }
                                Key.MediaRewind -> {
                                    exoPlayer?.seekTo(maxOf(0, (exoPlayer?.currentPosition ?: 0) - 10000))
                                    updateInteractionTimestamp()
                                    true
                                }
                                Key.Back -> {
                                    onExitClick()
                                    true
                                }
                                else -> {
                                    updateInteractionTimestamp()
                                    false
                                }
                            }
                        }
                        isLocked -> {
                            when (keyEvent.key) {
                                Key.Back -> {
                                    onExitClick()
                                    true
                                }
                                Key.Enter, Key.Spacebar -> {
                                    isLocked = false
                                    showControlsAndUpdateTime()
                                    true
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(isLocked) {
                detectTapGestures {
                    if (!isLocked) {
                        showControls = !showControls
                        interactionTimestamp = System.currentTimeMillis()
                    }
                }
            }
    ) {
        AndroidView(
            factory = { context ->
                createSimplePlayerView(
                    context = context,
                    videoUrl = videoUrl,
                    headers = headers,
                    onPlayerReady = { player ->
                        exoPlayer = player
                        onPlayerReady(player)
                    },
                    onLoadingChanged = { loading -> isLoading = loading },
                    onError = onError
                )
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView ->
                playerView.resizeMode = aspectRatioMode
            }
        )

        if (showControls && !isLoading) {
            XmlBasedControlOverlay(
                modifier = Modifier.fillMaxSize(),
                exoPlayer = exoPlayer,
                aspectRatioMode = aspectRatioMode,
                onAspectRatioChange = { newMode ->
                    aspectRatioMode = newMode
                },
                currentPosition = currentPosition,
                duration = duration,
                isPlaying = isPlaying,
                isLocked = isLocked,
                playButtonFocusRequester = playButtonFocusRequester,
                onExitClick = onExitClick,
                onQualityClick = onQualityClick,
                onLockToggle = { isLocked = !isLocked },
                onPreviousClick = { },
                onNextClick = { },
                onRewindClick = {
                    exoPlayer?.seekTo(maxOf(0, (exoPlayer?.currentPosition ?: 0) - 10000))
                },
                onForwardClick = {
                    exoPlayer?.let { player ->
                        player.seekTo(minOf(player.duration, player.currentPosition + 10000))
                    }
                },
                onPlayPauseClick = {
                    exoPlayer?.let { player ->
                        if (player.isPlaying) player.pause() else player.play()
                    }
                },
                onSeekTo = { position ->
                    exoPlayer?.seekTo(position)
                },
                onInteraction = updateInteractionTimestamp
            )
        }

        if (isLocked) {
            IconButton(
                onClick = {
                    isLocked = false
                    showControlsAndUpdateTime()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .focusable()
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp && it.key == Key.Enter) {
                            isLocked = false
                            showControlsAndUpdateTime()
                            true
                        } else false
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock Controls",
                    tint = Color.White
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        if (showChannelInfo) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = channelName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📺 Live Stream",
                        color = Color.Cyan,
                        fontSize = 14.sp
                    )
                    if (headers.containsKey("drm_type")) {
                        Text(
                            text = "🔐 DRM Protected (${headers["drm_type"]?.uppercase()})",
                            color = Color.Green,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun XmlBasedControlOverlay(
    modifier: Modifier = Modifier,
    aspectRatioMode: Int,
    onAspectRatioChange: (Int) -> Unit,
    exoPlayer: ExoPlayer?,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    isLocked: Boolean,
    playButtonFocusRequester: FocusRequester,
    onExitClick: () -> Unit,
    onQualityClick: () -> Unit,
    onLockToggle: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onInteraction: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .focusGroup()
    ) {
        if (!isLocked) {
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            LaunchedEffect(isFocused) {
                if (isFocused) {
                    onInteraction()
                }
            }

            IconButton(
                onClick = onExitClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 27.dp)
                    .border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(50)
                    )
                    .focusable(interactionSource = interactionSource)
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp && it.key == Key.Enter) {
                            onExitClick()
                            true
                        } else false
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit",
                    tint = Color.White
                )
            }
        }

        if (!isLocked && duration > 0) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 80.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color(0xFFBEBEBE),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )

                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { value ->
                            val newPosition = (value * duration).toLong()
                            onSeekTo(newPosition)
                            onInteraction()
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.Gray
                        )
                    )

                    Text(
                        text = formatTime(duration),
                        color = Color(0xFFBEBEBE),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }

        if (!isLocked) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val prevSource = remember { MutableInteractionSource() }
                val prevFocused by prevSource.collectIsFocusedAsState()

                LaunchedEffect(prevFocused) {
                    if (prevFocused) {
                        onInteraction()
                    }
                }

                IconButton(
                    onClick = {
                        onPreviousClick()
                        onInteraction()
                    },
                    modifier = Modifier
                        .focusable(interactionSource = prevSource)
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && it.key == Key.Enter) {
                                onPreviousClick()
                                onInteraction()
                                true
                            } else false
                        }
                        .border(
                            width = if (prevFocused) 2.dp else 0.dp,
                            color = if (prevFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White
                    )
                }

                val rewindSource = remember { MutableInteractionSource() }
                val rewindFocused by rewindSource.collectIsFocusedAsState()

                LaunchedEffect(rewindFocused) {
                    if (rewindFocused) {
                        onInteraction()
                    }
                }

                IconButton(
                    onClick = {
                        onRewindClick()
                        onInteraction()
                    },
                    modifier = Modifier
                        .focusable(interactionSource = rewindSource)
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && it.key == Key.Enter) {
                                onRewindClick()
                                onInteraction()
                                true
                            } else false
                        }
                        .border(
                            width = if (rewindFocused) 2.dp else 0.dp,
                            color = if (rewindFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        tint = Color.White
                    )
                }

                val playSource = remember { MutableInteractionSource() }
                val playFocused by playSource.collectIsFocusedAsState()

                LaunchedEffect(playFocused) {
                    if (playFocused) {
                        onInteraction()
                    }
                }

                IconButton(
                    onClick = {
                        onPlayPauseClick()
                        onInteraction()
                    },
                    modifier = Modifier
                        .focusRequester(playButtonFocusRequester)
                        .focusable(interactionSource = playSource)
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && it.key == Key.Enter) {
                                onPlayPauseClick()
                                onInteraction()
                                true
                            } else false
                        }
                        .border(
                            width = if (playFocused) 2.dp else 0.dp,
                            color = if (playFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }

                val forwardSource = remember { MutableInteractionSource() }
                val forwardFocused by forwardSource.collectIsFocusedAsState()

                LaunchedEffect(forwardFocused) {
                    if (forwardFocused) {
                        onInteraction()
                    }
                }

                IconButton(
                    onClick = {
                        onForwardClick()
                        onInteraction()
                    },
                    modifier = Modifier
                        .focusable(interactionSource = forwardSource)
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && it.key == Key.Enter) {
                                onForwardClick()
                                onInteraction()
                                true
                            } else false
                        }
                        .border(
                            width = if (forwardFocused) 2.dp else 0.dp,
                            color = if (forwardFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward",
                        tint = Color.White
                    )
                }

                val nextSource = remember { MutableInteractionSource() }
                val nextFocused by nextSource.collectIsFocusedAsState()

                LaunchedEffect(nextFocused) {
                    if (nextFocused) {
                        onInteraction()
                    }
                }

                IconButton(
                    onClick = {
                        onNextClick()
                        onInteraction()
                    },
                    modifier = Modifier
                        .focusable(interactionSource = nextSource)
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && it.key == Key.Enter) {
                                onNextClick()
                                onInteraction()
                                true
                            } else false
                        }
                        .border(
                            width = if (nextFocused) 2.dp else 0.dp,
                            color = if (nextFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }
            }
        }

        if (!isLocked) {
            val screenSource = remember { MutableInteractionSource() }
            val qualitySource = remember { MutableInteractionSource() }

            val isScreenFocused by screenSource.collectIsFocusedAsState()
            val isQualityFocused by qualitySource.collectIsFocusedAsState()

            LaunchedEffect(isScreenFocused) {
                if (isScreenFocused) {
                    onInteraction()
                }
            }

            LaunchedEffect(isQualityFocused) {
                if (isQualityFocused) {
                    onInteraction()
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 72.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newMode = when (aspectRatioMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        onAspectRatioChange(newMode)
                        onInteraction()
                    },
                    modifier = Modifier
                        .focusable(interactionSource = screenSource)
                        .border(
                            width = if (isScreenFocused) 2.dp else 0.dp,
                            color = if (isScreenFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = when (aspectRatioMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.Fullscreen
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Default.ZoomOutMap
                            else -> Icons.Default.AspectRatio
                        },
                        contentDescription = "Screen Mode",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        onQualityClick()
                        onInteraction()
                    },
                    modifier = Modifier
                        .focusable(interactionSource = qualitySource)
                        .border(
                            width = if (isQualityFocused) 2.dp else 0.dp,
                            color = if (isQualityFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Track Selection",
                        tint = Color.White
                    )
                }
            }
        }

        val lockSource = remember { MutableInteractionSource() }
        val isLockFocused by lockSource.collectIsFocusedAsState()

        LaunchedEffect(isLockFocused) {
            if (isLockFocused) {
                onInteraction()
            }
        }

        IconButton(
            onClick = {
                onLockToggle()
                onInteraction()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .focusable(interactionSource = lockSource)
                .border(
                    width = if (isLockFocused) 2.dp else 0.dp,
                    color = if (isLockFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(50)
                )
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Lock/Unlock Controls",
                tint = Color.White
            )
        }
    }
}

fun formatTime(timeMs: Long): String {
    val seconds = timeMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        else -> String.format("%d:%02d", minutes, seconds % 60)
    }
}

// ============================================
// PERBAIKAN UTAMA - DRM CLEARKEY HANDLING
// ============================================

@UnstableApi
private fun createSimplePlayerView(
    context: android.content.Context,
    videoUrl: String,
    headers: Map<String, String>,
    onPlayerReady: (ExoPlayer) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit
): PlayerView {

    if (!VpnHelper.isNetworkSecure(context)) {
        onError("Akses ditolak karena alasan keamanan")
        return PlayerView(context)
    }

    val playerView = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT 
        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        setBackgroundColor(android.graphics.Color.BLACK)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setPadding(0, 0, 0, 0)
    }

    Log.d("PlayerActivity", "Creating player for URL: $videoUrl")
    Log.d("PlayerActivity", "Headers: ${headers.keys.joinToString()}")

    val requestProperties = mutableMapOf<String, String>()
    headers.forEach { (key, value) ->
        if (!key.startsWith("drm_")) {
            requestProperties[key.trim().lowercase()] = value
        }
    }

    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(requestProperties["user-agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)
        .setDefaultRequestProperties(requestProperties)
        .setAllowCrossProtocolRedirects(true)

    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    val drmSessionManager = createDrmSessionManager(context, headers, httpDataSourceFactory)
    Log.d("PlayerActivity", "DRM Session Manager created: $drmSessionManager")

    val mediaSourceFactory = when {
        headers["manifest_type"]?.lowercase() == "dash" -> {
            DashMediaSource.Factory(dataSourceFactory)
        }
        headers["manifest_type"]?.lowercase() == "hls" -> {
            HlsMediaSource.Factory(dataSourceFactory)
        }
        else -> {
            when {
                videoUrl.endsWith(".mpd", ignoreCase = true) -> DashMediaSource.Factory(dataSourceFactory)
                videoUrl.endsWith(".m3u8", ignoreCase = true) -> HlsMediaSource.Factory(dataSourceFactory)
                else -> DefaultMediaSourceFactory(dataSourceFactory)
            }
        }
    }.setDrmSessionManagerProvider { drmSessionManager }

    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setMaxVideoSize(1920, 1080)
            .build()
    }

    if (context is PlayerActivity) {
        context.trackSelector = trackSelector
    }

    val exoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    val mediaItem = createMediaItemWithDrm(videoUrl, headers, context)

    exoPlayer.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            onLoadingChanged(playbackState == Player.STATE_BUFFERING)
            val state = when(playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d("PlayerActivity", "Playback State: $state")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerActivity", "Player Error [${error.errorCode}]: ${error.errorCodeName}", error)
            val errorMessage = when (error.errorCode) {
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> 
                    "Gagal mendapatkan license DRM. Format ClearKey mungkin salah."
                PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR ->
                    "Error sistem DRM. Device tidak support."
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                    "Decoder error. Coba restart aplikasi."
                else -> error.message ?: "Error: ${error.errorCodeName}"
            }
            onError(errorMessage)
        }
        
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            Log.d("PlayerActivity", "Video Size Changed: ${videoSize.width}x${videoSize.height}")
        }
        
        override fun onRenderedFirstFrame() {
            Log.d("PlayerActivity", "First frame rendered successfully!")
        }
    })

    playerView.player = exoPlayer
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.prepare()
    exoPlayer.playWhenReady = true

    onPlayerReady(exoPlayer)

    return playerView
}

@OptIn(UnstableApi::class)
private fun createDrmSessionManager(
    context: android.content.Context,
    headers: Map<String, String>,
    httpDataSourceFactory: DefaultHttpDataSource.Factory
): DrmSessionManager {

    val drmType = headers["drm_type"]?.lowercase()
    val drmLicense = headers["drm_key"] ?: headers["license_url"]

    Log.d("PlayerActivity", "Creating DRM Session Manager")
    Log.d("PlayerActivity", "DRM Type: $drmType")
    Log.d("PlayerActivity", "DRM License: ${drmLicense?.take(30)}...")

    if (drmType.isNullOrEmpty() || drmLicense.isNullOrEmpty()) {
        Log.d("PlayerActivity", "No DRM config found, returning DRM_UNSUPPORTED")
        return DrmSessionManager.DRM_UNSUPPORTED
    }

    return try {
        val mediaDrmCallback: MediaDrmCallback = when {
            drmType.contains("clearkey") -> {
                handleClearKeyLicense(drmLicense, httpDataSourceFactory)
            }

            drmType.contains("widevine") -> {
                if (context is PlayerActivity && !context.isDrmWidevineSupported()) {
                    return DrmSessionManager.DRM_UNSUPPORTED
                }
                val callback = HttpMediaDrmCallback(drmLicense, httpDataSourceFactory)
                headers["drm_token"]?.let { token ->
                    callback.setKeyRequestProperty("Authorization", "Bearer $token")
                }
                headers["authorization"]?.let { auth ->
                    callback.setKeyRequestProperty("Authorization", auth)
                }
                callback
            }

            else -> {
                Log.w("PlayerActivity", "Unsupported DRM type: $drmType")
                return DrmSessionManager.DRM_UNSUPPORTED
            }
        }

        val drmSchemeUuid = when {
            drmType.contains("clearkey") -> C.CLEARKEY_UUID
            drmType.contains("widevine") -> C.WIDEVINE_UUID
            drmType.contains("playready") -> C.PLAYREADY_UUID
            else -> {
                Log.w("PlayerActivity", "Unknown DRM scheme: $drmType")
                return DrmSessionManager.DRM_UNSUPPORTED
            }
        }

        Log.d("PlayerActivity", "Building DRM Session Manager with UUID: $drmSchemeUuid")

        DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(drmSchemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(true)
            .build(mediaDrmCallback)

    } catch (e: Exception) {
        Log.e("PlayerActivity", "Error creating DRM Session Manager: ${e.message}", e)
        DrmSessionManager.DRM_UNSUPPORTED
    }
}

/**
 * Handler untuk berbagai format ClearKey license
 */
@OptIn(UnstableApi::class)
private fun handleClearKeyLicense(
    drmLicense: String,
    httpDataSourceFactory: DefaultHttpDataSource.Factory
): MediaDrmCallback {
    
    Log.d("PlayerActivity", "Handling ClearKey license, format detection...")
    
    return when {
        // Format 1: URL license server
        drmLicense.startsWith("http") -> {
            Log.d("PlayerActivity", "ClearKey format: HTTP URL")
            HttpMediaDrmCallback(drmLicense, httpDataSourceFactory)
        }
        
        // Format 2: Data URI base64
        drmLicense.startsWith("data:application/json;base64,") -> {
            Log.d("PlayerActivity", "ClearKey format: Data URI Base64")
            val base64Data = drmLicense.removePrefix("data:application/json;base64,")
            val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val decoded = String(decodedBytes, Charsets.UTF_8)
            LocalMediaDrmCallback(processClearKeyJson(decoded).toByteArray(Charsets.UTF_8))
        }
        
        // Format 3: Raw JSON string
        drmLicense.trim().startsWith("{") -> {
            Log.d("PlayerActivity", "ClearKey format: Raw JSON")
            LocalMediaDrmCallback(processClearKeyJson(drmLicense).toByteArray(Charsets.UTF_8))
        }
        
        // Format 4: HEX format KID:KEY (Vision+ format)
        // Contoh: d386001215594043a8995db796ad9e9c:3404792cb4c804902acdc6ca65c1a298
        drmLicense.contains(":") && isValidHexFormat(drmLicense) -> {
            Log.d("PlayerActivity", "ClearKey format: HEX KID:KEY (Vision+)")
            val clearkeyJson = convertHexToClearKeyJson(drmLicense)
            Log.d("PlayerActivity", "Converted to JSON: $clearkeyJson")
            LocalMediaDrmCallback(clearkeyJson.toByteArray(Charsets.UTF_8))
        }
        
        // Format 5: Coba sebagai base64 langsung
        else -> {
            try {
                android.util.Base64.decode(drmLicense, android.util.Base64.DEFAULT)
                Log.d("PlayerActivity", "ClearKey format: Raw Base64")
                LocalMediaDrmCallback(drmLicense.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                Log.d("PlayerActivity", "ClearKey format: Fallback to JSON processing")
                LocalMediaDrmCallback(processClearKeyJson(drmLicense).toByteArray(Charsets.UTF_8))
            }
        }
    }
}

/**
 * Cek apakah string adalah format HEX KID:KEY yang valid
 */
private fun isValidHexFormat(license: String): Boolean {
    val parts = license.split(":")
    if (parts.size != 2) return false
    
    val hexPattern = Regex("^[0-9a-fA-F]+$")
    return parts[0].trim().matches(hexPattern) && parts[1].trim().matches(hexPattern)
}

/**
 * Konversi format HEX KID:KEY ke JSON ClearKey
 * Contoh input: d386001215594043a8995db796ad9e9c:3404792cb4c804902acdc6ca65c1a298
 */
private fun convertHexToClearKeyJson(hexLicense: String): String {
    return try {
        val parts = hexLicense.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("Format harus KID:KEY")
        }

        val kidHex = parts[0].trim().replace("-", "").replace(" ", "")
        val keyHex = parts[1].trim().replace("-", "").replace(" ", "")

        // Validasi panjang hex (harus 32 karakter untuk 16 bytes)
        if (kidHex.length != 32 || keyHex.length != 32) {
            Log.w("PlayerActivity", "Hex length unusual - KID: ${kidHex.length}, KEY: ${keyHex.length}")
        }

        // Convert hex ke bytes
        val kidBytes = kidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        // Encode ke Base64URL (URL-safe, no padding)
        val kidBase64 = android.util.Base64.encodeToString(
            kidBytes, 
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
        ).trim()
        
        val keyBase64 = android.util.Base64.encodeToString(
            keyBytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
        ).trim()

        // Format JSON ClearKey sesuai EME spec
        """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}]}"""

    } catch (e: Exception) {
        Log.e("PlayerActivity", "ClearKey conversion error: ${e.message}", e)
        throw e
    }
}

/**
 * Proses dan perbaiki JSON ClearKey yang sudah ada
 */
private fun processClearKeyJson(jsonString: String): String {
    return try {
        val jsonObj = JSONObject(jsonString)
        
        if (jsonObj.has("keys")) {
            val keysArray = jsonObj.getJSONArray("keys")
            
            for (i in 0 until keysArray.length()) {
                val keyObj = keysArray.getJSONObject(i)
                
                // Konversi ke Base64URL jika perlu
                if (keyObj.has("kid")) {
                    val kid = keyObj.getString("kid").trim()
                    keyObj.put("kid", convertToBase64Url(kid))
                }
                if (keyObj.has("k")) {
                    val k = keyObj.getString("k").trim()
                    keyObj.put("k", convertToBase64Url(k))
                }
            }
        }
        
        jsonObj.toString()
        
    } catch (e: Exception) {
        Log.w("PlayerActivity", "JSON processing failed, returning as-is: ${e.message}")
        jsonString
    }
}

/**
 * Konversi base64 standar ke base64url (URL-safe, no padding)
 */
private fun convertToBase64Url(base64String: String): String {
    return base64String
        .trim()
        .replace('+', '-')
        .replace('/', '_')
        .replace("=", "")
}

@OptIn(UnstableApi::class)
private fun createMediaItemWithDrm(
    videoUrl: String,
    headers: Map<String, String>,
    context: android.content.Context
): MediaItem {

    val drmType = headers["drm_type"]?.lowercase()
    val drmLicense = headers["drm_key"] ?: headers["license_url"]

    val mediaItemBuilder = MediaItem.Builder()
        .setUri(Uri.parse(videoUrl))

    if (!drmType.isNullOrEmpty() && !drmLicense.isNullOrEmpty()) {
        try {
            val drmSchemeUuid = when {
                drmType.contains("clearkey") -> C.CLEARKEY_UUID
                drmType.contains("widevine") -> C.WIDEVINE_UUID
                drmType.contains("playready") -> C.PLAYREADY_UUID
                else -> null
            } ?: return mediaItemBuilder.build()

            val drmConfigBuilder = MediaItem.DrmConfiguration.Builder(drmSchemeUuid)

            when {
                drmType.contains("clearkey") -> {
                    // Untuk ClearKey dengan license lokal (hex/json),
                    // license dihandle oleh LocalMediaDrmCallback di createDrmSessionManager
                    if (drmLicense.startsWith("http")) {
                        drmConfigBuilder
                            .setLicenseUri(drmLicense)
                            .setForceDefaultLicenseUri(true)
                    }
                }
                
                drmType.contains("widevine") || drmType.contains("playready") -> {
                    drmConfigBuilder
                        .setLicenseUri(drmLicense)
                        .setMultiSession(true)
                        .setForceDefaultLicenseUri(true)
                }
            }

            mediaItemBuilder.setDrmConfiguration(drmConfigBuilder.build())

        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error creating DRM config: ${e.message}")
        }
    }

    return mediaItemBuilder.build()
}
