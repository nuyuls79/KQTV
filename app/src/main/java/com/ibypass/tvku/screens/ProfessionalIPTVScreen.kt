package com.ibypass.tvku.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ibypass.tvku.Channel
import com.ibypass.tvku.M3UParser
import com.ibypass.tvku.MainActivity
import com.ibypass.tvku.SettingsActivity
import com.ibypass.tvku.ui.theme.TvkuTheme
import kotlinx.coroutines.*
import com.ibypass.tvku.utils.FirebaseManager
import com.ibypass.tvku.utils.playChannel
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.delay
import com.ibypass.tvku.VpnHelper
import com.ibypass.tvku.utils.getOrCreateSubscriptionId
import com.ibypass.tvku.utils.getSubscriptionExpiryText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ibypass.tvku.utils.isSubscriptionValid
import androidx.compose.material.icons.filled.ContactSupport
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.tasks.await
import com.ibypass.tvku.TvkuApp
import androidx.activity.ComponentActivity
import coil.request.CachePolicy
import androidx.compose.ui.graphics.painter.ColorPainter

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun ProfessionalIPTVScreen(playlistUrl: String) {
    val context = LocalContext.current
    var expiryText by remember { mutableStateOf("Memuat status langganan...") }
    val subscriptionId = remember { getOrCreateSubscriptionId(context) }
    var isAllowed by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    
    var isAuthenticated by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        val app = context.applicationContext as TvkuApp
        if (!app.isAuthReady()) {
            try {
                isAuthenticated = app.ensureAuthenticated()
                if (!isAuthenticated) {
                    Toast.makeText(context, "Gagal melakukan autentikasi", Toast.LENGTH_SHORT).show()
                    (context as? ComponentActivity)?.finish()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal autentikasi: ${e.message}", Toast.LENGTH_SHORT).show()
                (context as? ComponentActivity)?.finish()
            }
        }
    }

    var showError by remember { mutableStateOf("") }
    var showContactDialog by remember { mutableStateOf(false) }
    var showInfoBar by remember { mutableStateOf(true) }

    val prefs = context.getSharedPreferences("tvku_settings", Context.MODE_PRIVATE)

    var playlistUrl by remember { mutableStateOf(playlistUrl) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val gridColumns = prefs.getInt("grid_columns", 3)
    val showChannelLogos = prefs.getBoolean("show_channel_logos", true)

    fun loadChannelsFromUrl(url: String) {
        if (isLoading || !isAuthenticated) return

        scope.launch(Dispatchers.IO) {
            isLoading = true

            try {
                if (url.isBlank() || !url.startsWith("http")) {
                    withContext(Dispatchers.Main) {
                        channels = emptyList()
                        selectedCategory = "All"
                        if (isAllowed == true) {
                            Toast.makeText(context, "URL tidak valid", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                val parsedChannels = M3UParser.parseFromUrl(url)

                withContext(Dispatchers.Main) {
                    channels = parsedChannels

                    if (isAllowed == true) {
                        Toast.makeText(
                            context,
                            "Loaded ${parsedChannels.size} channels",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    channels = emptyList()
                    selectedCategory = "All"
                    if (isAllowed == true) {
                        Toast.makeText(context, "Failed to load: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(subscriptionId) {
        if (!isAuthenticated) return@LaunchedEffect
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            delay(1000)
        }

        try {
            isAllowed = withContext(Dispatchers.IO) {
                isSubscriptionValid(subscriptionId)
            }

            expiryText = if (isAllowed == true) {
                try {
                    withContext(Dispatchers.IO) {
                        getSubscriptionExpiryText(subscriptionId)
                    }
                } catch (e: Exception) {
                    "Status langganan aktif"
                }
            } else {
                "tidak ada langganan"
            }
        } catch (e: Exception) {
            isAllowed = false
            expiryText = "Gagal memuat status"
        }
    }

    LaunchedEffect(Unit) {
        try {
            val remoteConfig = Firebase.remoteConfig
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    remoteConfig.fetchAndActivate().await()
                } catch (e: Exception) {
                    remoteConfig.fetch().await()
                    delay(1000)
                    remoteConfig.activate().await()
                }
            } else {
                remoteConfig.fetch().await()
                delay(2000)
                remoteConfig.activate().await()
            }
        } catch (e: Exception) {
            // Silent fail untuk remote config
        }
    }

    LaunchedEffect(playlistUrl, isAllowed) {
        if (isAllowed == true && playlistUrl.isNotBlank()) {
            loadChannelsFromUrl(playlistUrl)
        } else if (isAllowed == false) {
            channels = emptyList()
            selectedCategory = "All"
        }
    }

    DisposableEffect(Unit) {
        val job = scope.launch {
            delay(30000)
            while (isActive) {
                val activity = context as? MainActivity
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    if (VpnHelper.isVpnOrProxyActive(context)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "VPN/Proxy detected! Closing app for security.",
                                Toast.LENGTH_LONG
                            ).show()
                            delay(2000)
                            activity.finish()
                        }
                        break
                    }
                } else break
                delay(30000)
            }
        }
        onDispose { job.cancel() }
    }

    val categories = remember(channels) {
        val cats = channels.map { it.group }.distinct().filter { it.isNotEmpty() }
        listOf("All") + cats.sorted()
    }

    val filteredChannels = remember(channels, selectedCategory, searchQuery) {
        channels.filter { channel ->
            val matchesCategory = selectedCategory == "All" ||
                    channel.group == selectedCategory

            val matchesSearch = searchQuery.isEmpty() ||
                    channel.name.contains(searchQuery, ignoreCase = true) ||
                    channel.group.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesSearch
        }
    }

    var forceReload by remember { mutableStateOf(false) }

    LaunchedEffect(playlistUrl, forceReload) {
        loadChannelsFromUrl(playlistUrl)
    }

    TvkuTheme(darkTheme = true) {
        val bgUrl = Firebase.remoteConfig.getString("main_bg_url")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (bgUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bgUrl)
                        .crossfade(true)
                        .allowHardware(false)
                        .placeholder(android.R.drawable.screen_background_dark)
                        .error(android.R.drawable.screen_background_dark)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Sidebar(
                        categories = if (isAllowed == true) categories else listOf(),
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        onRefresh = {
                            scope.launch {
                                isLoading = true
                                isAllowed = isSubscriptionValid(subscriptionId)

                                FirebaseManager.fetchPlaylistUrl(forceFetch = true) { newUrl ->
                                    playlistUrl = newUrl ?: ""
                                    forceReload = !forceReload
                                }
                            }
                        },
                        onSettings = {
                            try {
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            } catch (e: Exception) {
                            }
                        }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        TopBarSection(
                            selectedCategory = selectedCategory,
                            filteredCount = filteredChannels.size,
                            subscriptionExpiryText = expiryText.ifBlank { "Memuat langganan..." },
                            searchQuery = searchQuery,
                            onRefresh = {
                                scope.launch {
                                    isLoading = true
                                    FirebaseManager.fetchPlaylistUrl(forceFetch = true) { newUrl ->
                                        playlistUrl = newUrl ?: ""
                                        forceReload = !forceReload
                                    }
                                }
                            },
                            onOpenSettings = {
                                try {
                                    context.startActivity(Intent(context, SettingsActivity::class.java))
                                } catch (e: Exception) {
                                }
                            },
                            onToggleSearch = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            onClearSearch = {
                                searchQuery = ""
                            },
                            onReloadChannels = {
                                loadChannelsFromUrl(playlistUrl)
                            },
                            onShowError = { error ->
                                showError = error
                            }
                        )

                        if (isSearchActive) {
                            val searchFocusRequester = remember { FocusRequester() }
                            val clearFocusRequester = remember { FocusRequester() }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Search channels...", color = Color.White) },
                                placeholder = { Text("Type to search...", color = Color.White.copy(alpha = 0.5f)) },
                                textStyle = LocalTextStyle.current.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = Color.White,
                                    focusedLabelColor = Color.White,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .focusRequester(searchFocusRequester)
                                    .onKeyEvent { keyEvent ->
                                        when (keyEvent.key) {
                                            Key.DirectionDown -> false
                                            Key.DirectionRight -> {
                                                if (searchQuery.isNotEmpty()) {
                                                    clearFocusRequester.requestFocus()
                                                    true
                                                } else false
                                            }

                                            Key.Back, Key.Escape -> {
                                                isSearchActive = false
                                                searchQuery = ""
                                                true
                                            }

                                            else -> false
                                        }
                                    },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.focusRequester(clearFocusRequester)
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            )

                            LaunchedEffect(isSearchActive) {
                                if (isSearchActive) {
                                    delay(100)
                                    searchFocusRequester.requestFocus()
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            when {
                                isAllowed == null || isLoading -> {
                                    LoadingScreen()
                                }

                                isAllowed == false -> {
                                    Column {
                                        EmptyScreen(
                                            onRefresh = {
                                                scope.launch {
                                                    isLoading = true
                                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                                                        delay(1000)
                                                    }

                                                    try {
                                                        isAllowed = isSubscriptionValid(subscriptionId)
                                                        expiryText = if (isAllowed == true) {
                                                            try {
                                                                withContext(Dispatchers.IO) {
                                                                    getSubscriptionExpiryText(subscriptionId)
                                                                }
                                                            } catch (e: Exception) {
                                                                "Status langganan aktif"
                                                            }
                                                        } else {
                                                            "Tidak ada langganan"
                                                        }

                                                    } catch (e: Exception) {
                                                        showError = when {
                                                            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
                                                                "Coba lagi dalam beberapa saat"
                                                            else -> "Gagal memeriksa langganan: ${e.message}"
                                                        }
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            },
                                            message = "Langganan Tidak Aktif",
                                            subMessage = buildString {
                                                append("ID langganan Anda belum terdaftar atau sudah tidak aktif.\n\n")
                                                append("Solusi:\n")
                                                append("• Pastikan ID langganan benar\n")
                                                append("• Hubungi admin untuk aktivasi\n")
                                                append("• Periksa masa berlaku langganan")
                                            }
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            OutlinedButton(
                                                onClick = { showContactDialog = true },
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = Color.White
                                                ),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContactSupport,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Info Kontak")
                                            }
                                        }
                                    }
                                }

                                filteredChannels.isEmpty() -> {
                                    EmptyScreen(
                                        onRefresh = {
                                            if (searchQuery.isNotEmpty()) {
                                                searchQuery = ""
                                            } else {
                                                scope.launch {
                                                    try {
                                                        loadChannelsFromUrl(playlistUrl)
                                                    } catch (e: Exception) {
                                                        showError = "Gagal memuat channel: ${e.message}"
                                                    }
                                                }
                                            }
                                        },
                                        message = if (searchQuery.isNotEmpty()) {
                                            "Tidak Ditemukan"
                                        } else {
                                            "Channel Tidak Tersedia"
                                        },
                                        subMessage = if (searchQuery.isNotEmpty()) {
                                            buildString {
                                                append("Tidak ada channel dengan kata kunci \"$searchQuery\".\n\n")
                                                append("Tips pencarian:\n")
                                                append("• Coba kata kunci lain\n")
                                                append("• Periksa ejaan\n")
                                                append("• Gunakan kata kunci yang lebih umum")
                                            }
                                        } else {
                                            buildString {
                                                append("Saat ini tidak ada channel yang tersedia.\n\n")
                                                append("Kemungkinan penyebab:\n")
                                                append("• Koneksi internet bermasalah\n")
                                                append("• Server sedang maintenance\n")
                                                append("• Playlist belum ter-update")
                                            }
                                        }
                                    )
                                }

                                else -> {
                                    Column(modifier = Modifier.fillMaxSize()) {

                                        ChannelGrid(
                                            channels = filteredChannels,
                                            columns = gridColumns,
                                            showLogos = showChannelLogos,
                                            onChannelClick = { channel ->
                                                scope.launch {
                                                    try {
                                                        playChannel(context, channel)
                                                    } catch (e: Exception) {
                                                        showError = "Gagal memutar ${channel.name}: ${e.message}"
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            if (showError.isNotEmpty()) {
                                Snackbar(
                                    action = {
                                        TextButton(
                                            onClick = { showError = "" }
                                        ) {
                                            Text("Tutup", color = Color.White)
                                        }
                                    },
                                    dismissAction = {
                                        IconButton(onClick = { showError = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Tutup",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(16.dp),
                                    containerColor = Color(0xFFD32F2F)
                                ) {
                                    Text(
                                        text = showError,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            if (showContactDialog) {
                                AlertDialog(
                                    onDismissRequest = { showContactDialog = false },
                                    containerColor = Color(0xFF1E1E1E),
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ContactSupport,
                                                contentDescription = null,
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Informasi Kontak",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    text = {
                                        Column {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = Color(0xFF2A2A2A)
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    ContactInfoRow(
                                                        icon = Icons.Default.Message,
                                                        label = "Telegram",
                                                        value = "@akuerpan",
                                                        color = Color(0xFF25D366)
                                                    )

                                                    Spacer(modifier = Modifier.height(12.dp))

                                                    ContactInfoRow(
                                                        icon = Icons.Default.AlternateEmail,
                                                        label = "Email",
                                                        value = "admin@tvku.app",
                                                        color = Color(0xFF2196F3)
                                                    )

                                                    Spacer(modifier = Modifier.height(12.dp))

                                                    ContactInfoRow(
                                                        icon = Icons.Default.Schedule,
                                                        label = "Jam Layanan",
                                                        value = "08:00 - 22:00 WIB",
                                                        color = Color(0xFFFF9800)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = "💡 Sertakan ID langganan Anda saat menghubungi admin",
                                                fontSize = 12.sp,
                                                color = Color(0xFFBBBBBB),
                                                style = androidx.compose.ui.text.TextStyle(
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { showContactDialog = false }
                                        ) {
                                            Text("Tutup", color = Color(0xFF2196F3))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF888888),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Normal
            )
        }
    }
}