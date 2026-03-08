package com.ibypass.tvku.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Key
import com.ibypass.tvku.utils.getOrCreateSubscriptionId
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarSection(
    selectedCategory: String,
    filteredCount: Int,
    subscriptionExpiryText: String,
    searchQuery: String = "",
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSearch: () -> Unit = {},
    onClearSearch: () -> Unit = {},
    onReloadChannels: () -> Unit = {},
    onShowError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val subscriptionId = remember { getOrCreateSubscriptionId(context) }

    val refreshSource = remember { MutableInteractionSource() }
    val settingsSource = remember { MutableInteractionSource() }
    val searchSource = remember { MutableInteractionSource() }
    val infoSource = remember { MutableInteractionSource() }

    val refreshFocused = refreshSource.collectIsFocusedAsState().value
    val settingsFocused = settingsSource.collectIsFocusedAsState().value
    val searchFocused = searchSource.collectIsFocusedAsState().value
    val infoFocused = infoSource.collectIsFocusedAsState().value

    var showSubscriptionDialog by remember { mutableStateOf(false) }

    val handleSmartRefresh = {
        if (searchQuery.isNotEmpty()) {
            onClearSearch()
        } else {
            onReloadChannels()
        }
    }

    TopAppBar(
        modifier = Modifier
            .height(48.dp)
            .padding(horizontal = 8.dp),
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = subscriptionExpiryText.replace("\n", " • "),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showSubscriptionDialog = true },
                            modifier = Modifier
                                .scale(if (infoFocused) 1.05f else 1f)
                                .border(
                                    width = if (infoFocused) 1.dp else 0.dp,
                                    color = if (infoFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            interactionSource = infoSource
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier
                                .scale(if (searchFocused) 1.05f else 1f)
                                .border(
                                    width = if (searchFocused) 1.dp else 0.dp,
                                    color = if (searchFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            interactionSource = searchSource
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = handleSmartRefresh,
                            modifier = Modifier
                                .scale(if (refreshFocused) 1.05f else 1f)
                                .border(
                                    width = if (refreshFocused) 1.dp else 0.dp,
                                    color = if (refreshFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            interactionSource = refreshSource
                        ) {
                            val refreshIcon = if (searchQuery.isNotEmpty()) {
                                Icons.Default.Clear
                            } else {
                                Icons.Default.Refresh
                            }

                            Icon(
                                imageVector = refreshIcon,
                                contentDescription = if (searchQuery.isNotEmpty()) "Clear Search" else "Refresh",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .scale(if (settingsFocused) 1.05f else 1f)
                                .border(
                                    width = if (settingsFocused) 1.dp else 0.dp,
                                    color = if (settingsFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                ),
                            interactionSource = settingsSource
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )

    if (showSubscriptionDialog) {
        SimpleSubscriptionDialog(
            subscriptionId = subscriptionId,
            subscriptionExpiryText = subscriptionExpiryText,
            onDismiss = { showSubscriptionDialog = false }
        )
    }
}

@Composable
fun SimpleSubscriptionDialog(
    subscriptionId: String,
    subscriptionExpiryText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp

    val isTablet = screenWidth >= 600.dp
    val isSmallScreen = screenHeight < 600.dp || screenWidth < 360.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = when {
                        isTablet -> 48.dp
                        isSmallScreen -> 12.dp
                        else -> 20.dp
                    },
                    vertical = when {
                        isSmallScreen -> 8.dp
                        else -> 16.dp
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val maxHeight = if (isSmallScreen) screenHeight * 0.95f else screenHeight * 0.9f

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                                    shape = RoundedCornerShape(if (isSmallScreen) 16.dp else 20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                SimpleSubscriptionDialogContent(
                    subscriptionId = subscriptionId,
                    subscriptionExpiryText = subscriptionExpiryText,
                    onDismiss = onDismiss,
                    isSmallScreen = isSmallScreen,
                    isTablet = isTablet
                )
            }
        }
    }
}

@Composable
private fun SimpleSubscriptionDialogContent(
    subscriptionId: String,
    subscriptionExpiryText: String,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isTablet: Boolean
) {
    val context = LocalContext.current

    val hasActiveSubscription = !subscriptionExpiryText.contains("Free trial") &&
            !subscriptionExpiryText.contains("tidak ada langganan") &&
            !subscriptionExpiryText.contains("kadaluarsa")

    val isExpired = subscriptionExpiryText.contains("kadaluarsa")
    val isNotSubscribed = !hasActiveSubscription && !isExpired

    val telegramSource = remember { MutableInteractionSource() }
    val headerCopySource = remember { MutableInteractionSource() }
    val headerCloseSource = remember { MutableInteractionSource() }

    val telegramFocused = telegramSource.collectIsFocusedAsState().value
    val headerCopyFocused = headerCopySource.collectIsFocusedAsState().value
    val headerCloseFocused = headerCloseSource.collectIsFocusedAsState().value
    
    val copyToClipboard = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("Subscription ID", subscriptionId)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "ID disalin ke clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Gagal mengakses clipboard", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal menyalin", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A2634),
                        Color(0xFF1F2F3D)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF2C5282),
                                Color(0xFF2B4C6F)
                            )
                        )
                    )
                    .padding(
                        top = if (isSmallScreen) 12.dp else 16.dp,
                        start = if (isSmallScreen) 16.dp else 20.dp,
                        end = if (isSmallScreen) 16.dp else 20.dp,
                        bottom = if (isSmallScreen) 12.dp else 16.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (isSmallScreen) 22.dp else 24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Text(
                        text = "Informasi Langganan",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = when {
                                isTablet -> 22.sp
                                isSmallScreen -> 18.sp
                                else -> 20.sp
                            },
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(if (isSmallScreen) 32.dp else 36.dp)
                            .border(
                                width = if (headerCloseFocused) 2.dp else 1.dp,
                                color = if (headerCloseFocused) Color.White else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        interactionSource = headerCloseSource
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(if (isSmallScreen) 16.dp else 18.dp)
                        )
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isSmallScreen) 16.dp else 24.dp,
                        vertical = if (isSmallScreen) 12.dp else 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 12.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Subscription ID Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (isSmallScreen) 10.dp else 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263545)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = if (isSmallScreen) 14.dp else 16.dp,
                            vertical = if (isSmallScreen) 12.dp else 14.dp
                        ),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFFADD8E6),
                                modifier = Modifier.size(if (isSmallScreen) 16.dp else 18.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "ID Langganan Anda",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = if (isSmallScreen) 13.sp else 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFFADD8E6)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Divider(color = Color(0xFF3A4A5F), thickness = 1.dp)
                        
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = subscriptionId,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = if (isSmallScreen) 0.5.sp else 0.8.sp,
                                    fontSize = when {
                                        isTablet -> 16.sp
                                        isSmallScreen -> 13.sp
                                        else -> 14.sp
                                    }
                                ),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = copyToClipboard,
                                modifier = Modifier
                                    .height(if (isSmallScreen) 32.dp else 34.dp)
                                    .border(
                                        width = if (headerCopyFocused) 2.dp else 0.dp,
                                        color = if (headerCopyFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4A6F8C),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                interactionSource = headerCopySource
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy ID",
                                    modifier = Modifier.size(if (isSmallScreen) 14.dp else 16.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                Text(
                                    text = "Salin",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = if (isSmallScreen) 11.sp else 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (isSmallScreen) 10.dp else 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            hasActiveSubscription && !isExpired -> Color(0xFF2E6B41)
                            isExpired -> Color(0xFF8B4A4A)
                            else -> Color(0xFF4F5A64)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isSmallScreen) 14.dp else 16.dp,
                                vertical = if (isSmallScreen) 12.dp else 14.dp
                            ),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    hasActiveSubscription && !isExpired -> Icons.Default.CheckCircle
                                    isExpired -> Icons.Default.Cancel
                                    else -> Icons.Default.Schedule
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (isSmallScreen) 18.dp else 20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = when {
                                    hasActiveSubscription && !isExpired -> "Status Aktif"
                                    isExpired -> "Langganan Kadaluarsa"
                                    else -> "Belum Berlangganan"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isSmallScreen) 14.sp else 16.sp
                                ),
                                color = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (isNotSubscribed) "tidak ada langganan" else subscriptionExpiryText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = when {
                                    isTablet -> 14.sp
                                    isSmallScreen -> 12.sp
                                    else -> 13.sp
                                }
                            ),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                if (isNotSubscribed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+jsLIIefJCMhjYzNl"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak dapat membuka Telegram", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isSmallScreen) 44.dp else 48.dp)
                            .border(
                                width = if (telegramFocused) 2.dp else 0.dp,
                                color = if (telegramFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(if (isSmallScreen) 8.dp else 10.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B6387),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(if (isSmallScreen) 8.dp else 10.dp),
                        contentPadding = PaddingValues(0.dp),
                        interactionSource = telegramSource
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (isSmallScreen) 18.dp else 20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(10.dp))
                            
                            Text(
                                text = "Gabung Channel Telegram",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = when {
                                        isTablet -> 15.sp
                                        isSmallScreen -> 13.sp
                                        else -> 14.sp
                                    },
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "Hubungi admin untuk aktivasi langganan",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = if (isSmallScreen) 11.sp else 12.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFADD8E6),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

fun generateSubscriptionId(): String {
    val safeChars = "123456789ABCDEFGHIJKLMNPQRSTUVWXYZ"
    val length = 12

    return (1..length)
        .map { safeChars.random() }
        .joinToString("")
}

fun generateNumericSubscriptionId(): String {
    val safeDigits = "123456789"
    val length = 12

    return (1..length)
        .map { safeDigits.random() }
        .joinToString("")
}