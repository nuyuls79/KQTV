package com.ibypass.tvku

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ibypass.tvku.ui.theme.TvkuTheme
import com.ibypass.tvku.utils.UpdateChecker
import com.ibypass.tvku.utils.RulesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager


class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        setContent {
            TvkuTheme {
                var statusText by remember { mutableStateOf("Memeriksa koneksi...") }
                var navigateNext by remember { mutableStateOf(false) }
                var progress by remember { mutableStateOf(0f) }
                var isError by remember { mutableStateOf(false) }

                MinimalSplashScreenUI(
                    statusText = statusText,
                    progress = progress,
                    isError = isError
                )

                if (navigateNext) {
                    LaunchedEffect(Unit) {
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        finish()
                    }
                }

                LaunchedEffect(Unit) {
                    while (progress < 1f && !isError) {
                        delay(30)
                        progress += 0.01f
                    }
                }

                LaunchedEffect(Unit) {
                    delay(1000)

                    if (!isInternetAvailable(this@SplashActivity)) {
                        statusText = "Tidak ada koneksi internet"
                        isError = true
                        delay(2000)
                        finish()
                        return@LaunchedEffect
                    }

                    statusText = "Mempersiapkan sistem..."
                    
                    try {
                        val app = application as TvkuApp
                        if (!app.isFirebaseReady()) {
                            throw Exception("Firebase belum siap")
                        }
                        
                        statusText = "Melakukan autentikasi..."
                        if (!app.isAuthReady()) {
                            val authenticated = app.ensureAuthenticated()
                            if (!authenticated) {
                                statusText = "Gagal melakukan autentikasi"
                                isError = true
                                delay(2000)
                                finish()
                                return@LaunchedEffect
                            }
                        }

                        RulesManager.initializeRules(this@SplashActivity)
                        if (!RulesManager.validateRules(this@SplashActivity)) {
                            statusText = "Terjadi kesalahan sistem"
                            isError = true
                            delay(2000)
                            finish()
                            return@LaunchedEffect
                        }
                    } catch (e: Exception) {
                        statusText = "Terjadi kesalahan sistem: ${e.message}"
                        isError = true
                        delay(2000)
                        finish()
                        return@LaunchedEffect
                    }

                    statusText = "Memeriksa pembaruan..."

                    if (needsStoragePermission()) {
                        requestStoragePermission()
                        return@LaunchedEffect
                    }

                    proceedWithUpdateCheck(statusText) { newStatus ->
                        statusText = newStatus
                    }
                }
            }
        }
    }

    private fun needsStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> false

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {

                try {
                    val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                    val permissions = info.requestedPermissions
                    !permissions?.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)!!
                } catch (e: Exception) {
                    true
                }
            }

            else -> false
        }
    }

    private fun requestStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1002)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                lifecycleScope.launch {
                    proceedWithUpdateCheck("Memeriksa pembaruan...") { }
                }
            }

            else -> {
                lifecycleScope.launch {
                    proceedWithUpdateCheck("Memeriksa pembaruan...") { }
                }
            }
        }
    }

    private fun proceedWithUpdateCheck(currentStatus: String, onStatusChange: (String) -> Unit) {
        UpdateChecker.checkForUpdate(this) {
            lifecycleScope.launch {
                onStatusChange("Menyiapkan aplikasi...")
                delay(1500)
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            lifecycleScope.launch {
                proceedWithUpdateCheck("Memeriksa pembaruan...") { }
            }
        }
    }
}


@Composable
fun MinimalSplashScreenUI(
    statusText: String,
    progress: Float,
    isError: Boolean
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000),
        label = "fade_in"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.1f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(32.dp))

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = statusText,
                color = if (isError) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isError) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .width(200.dp)
                        .height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Text(
            text = "© 2025 Ervan Kurnia Dev's",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(alpha)
        )
    }
}