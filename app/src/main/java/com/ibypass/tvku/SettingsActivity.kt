package com.ibypass.tvku

import android.content.Context
import android.os.Bundle
import android.view.View
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.ibypass.tvku.ui.theme.TvkuTheme
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        setContent {
            TvkuTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ModernSettingsScreen {
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
fun ModernSettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("tvku_settings", Context.MODE_PRIVATE)

    var autoPlay by remember { mutableStateOf(prefs.getBoolean("auto_play", true)) }
    var hardwareDecoder by remember { mutableStateOf(prefs.getBoolean("hardware_decoder", true)) }
    var networkTimeout by remember { mutableStateOf(prefs.getInt("network_timeout", 15)) }
    var gridColumns by remember { mutableStateOf(prefs.getInt("grid_columns", 3)) }
    var showChannelLogos by remember { mutableStateOf(prefs.getBoolean("show_channel_logos", true)) }
    val bgUrl = Firebase.remoteConfig.getString("main_bg_url")

    Box(modifier = Modifier.fillMaxSize()) {
        if (bgUrl.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(bgUrl)
                    .crossfade(true)
                    .placeholder(android.R.drawable.screen_background_dark)
                    .error(android.R.drawable.screen_background_dark)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalTextStyle provides LocalTextStyle.current.copy(color = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )

                    val closeButtonInteractionSource = remember { MutableInteractionSource() }
                    val isCloseButtonFocused by closeButtonInteractionSource.collectIsFocusedAsState()

                    IconButton(
                        onClick = onBackPressed,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (isCloseButtonFocused) 
                                    Color.White.copy(alpha = 0.2f) 
                                else 
                                    Color.White.copy(alpha = 0.1f), 
                                shape = CircleShape
                            )
                            .border(
                                width = if (isCloseButtonFocused) 2.dp else 0.dp,
                                color = if (isCloseButtonFocused) Color(0xFF2196F3) else Color.Transparent,
                                shape = CircleShape
                            )
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && 
                                    (keyEvent.key == Key.DirectionCenter || 
                                     keyEvent.key == Key.Enter || 
                                     keyEvent.key == Key.Spacebar)
                                ) {
                                    onBackPressed()
                                    true
                                } else false
                            },
                        interactionSource = closeButtonInteractionSource
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                }

                // Settings Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SettingsSectionHeader(title = "Tampilan")
                    }

                    item {
                        SwitchSettingItem(
                            title = "Tampilkan Logo Channel",
                            description = "Menampilkan logo channel jika tersedia",
                            icon = Icons.Default.Image,
                            isChecked = showChannelLogos,
                            onCheckedChange = { checked ->
                                showChannelLogos = checked
                                prefs.edit().putBoolean("show_channel_logos", checked).apply()
                            }
                        )
                    }

                    item {
                        SliderSettingItem(
                            title = "Jumlah Kolom Grid",
                            description = "Atur jumlah kolom dalam tampilan grid channel",
                            icon = Icons.Default.GridView,
                            value = gridColumns.toFloat(),
                            valueRange = 2f..5f,
                            steps = 2,
                            onValueChange = { value ->
                                val columns = value.roundToInt()
                                gridColumns = columns
                                prefs.edit().putInt("grid_columns", columns).apply()
                            },
                            valueText = "$gridColumns kolom"
                        )
                    }

                    item {
                        SettingsSectionHeader(title = "Pemutaran")
                    }

                    item {
                        SwitchSettingItem(
                            title = "Putar Otomatis",
                            description = "Putar channel secara otomatis saat dipilih",
                            icon = Icons.Default.PlayCircle,
                            isChecked = autoPlay,
                            onCheckedChange = { checked ->
                                autoPlay = checked
                                prefs.edit().putBoolean("auto_play", checked).apply()
                            }
                        )
                    }

                    item {
                        SwitchSettingItem(
                            title = "Hardware Decoder",
                            description = "Gunakan hardware acceleration untuk decoding video",
                            icon = Icons.Default.Memory,
                            isChecked = hardwareDecoder,
                            onCheckedChange = { checked ->
                                hardwareDecoder = checked
                                prefs.edit().putBoolean("hardware_decoder", checked).apply()
                            }
                        )
                    }

                    item {
                        SliderSettingItem(
                            title = "Timeout Jaringan",
                            description = "Waktu tunggu maksimum untuk koneksi (dalam detik)",
                            icon = Icons.Default.Timer,
                            value = networkTimeout.toFloat(),
                            valueRange = 5f..60f,
                            steps = 10,
                            onValueChange = { value ->
                                val timeout = value.roundToInt()
                                networkTimeout = timeout
                                prefs.edit().putInt("network_timeout", timeout).apply()
                            },
                            valueText = "$networkTimeout detik"
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color(0xFF90CAF9)
        ),
        modifier = Modifier.padding(vertical = 8.dp)
    )
    Divider(
        color = Color(0xFF90CAF9).copy(alpha = 0.3f),
        thickness = 1.dp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SwitchSettingItem(
    title: String,
    description: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onCheckedChange(!isChecked)
            }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF2196F3) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            onCheckedChange(!isChecked)
                            true
                        }
                        else -> false
                    }
                } else false
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2196F3),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun SliderSettingItem(
    title: String,
    description: String,
    icon: ImageVector,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueText: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Calculate step size for keyboard/remote navigation
    val stepSize = if (steps > 0) {
        (valueRange.endInclusive - valueRange.start) / (steps + 1)
    } else {
        1f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF2196F3) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            val newValue = (value - stepSize).coerceIn(valueRange)
                            if (newValue != value) {
                                onValueChange(newValue)
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            val newValue = (value + stepSize).coerceIn(valueRange)
                            if (newValue != value) {
                                onValueChange(newValue)
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus button
                IconButton(
                    onClick = {
                        val newValue = (value - stepSize).coerceIn(valueRange)
                        onValueChange(newValue)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2196F3).copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = Color.White
                    )
                }
                
                // Slider
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF2196F3),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                
                // Plus button
                IconButton(
                    onClick = {
                        val newValue = (value + stepSize).coerceIn(valueRange)
                        onValueChange(newValue)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2196F3).copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
