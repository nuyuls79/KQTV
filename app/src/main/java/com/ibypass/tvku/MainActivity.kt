package com.ibypass.tvku

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ibypass.tvku.ui.theme.TvkuTheme
import kotlinx.coroutines.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import com.ibypass.tvku.screens.SecurityWarningScreen
import com.ibypass.tvku.screens.ProfessionalIPTVScreen
import com.ibypass.tvku.utils.RulesManager

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var vpnCheckJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        lifecycleScope.launch {
            try {

                RulesManager.initializeRules(this@MainActivity)
                val rulesValid = RulesManager.validateRules(this@MainActivity)

                if (!rulesValid) {
                    Toast.makeText(this@MainActivity, "Terjadi kesalahan sistem", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                if (VpnHelper.isVpnOrProxyActive(this@MainActivity)) {
                    showSecurityWarning()
                    return@launch
                }

                val app = application as TvkuApp
                if (!app.isAuthReady()) {
                    val authenticated = app.ensureAuthenticated()
                    if (!authenticated) {
                        Toast.makeText(this@MainActivity, "Gagal melakukan autentikasi", Toast.LENGTH_SHORT).show()
                        finish()
                        return@launch
                    }
                }

                // PLAYLIST HARDCODE
                val playlistUrl = "https://raw.githubusercontent.com/mimipipi22/lalajo/refs/heads/main/playlist25"

                setContent {
                    TvkuTheme(darkTheme = true) {
                        ProfessionalIPTVScreen(
                            playlistUrl = playlistUrl
                        )
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Terjadi kesalahan", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showSecurityWarning() {
        setContent {
            TvkuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecurityWarningScreen {
                        cleanupAndFinish()
                    }
                }
            }
        }
    }

    private fun cleanupAndFinish() {
        try {
            vpnCheckJob?.cancel()
            lifecycleScope.coroutineContext.cancelChildren()
        } catch (_: Exception) {
        } finally {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnCheckJob?.cancel()
        lifecycleScope.coroutineContext.cancelChildren()
    }

    override fun onPause() {
        super.onPause()
        vpnCheckJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startVpnMonitoring()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun startVpnMonitoring() {
        vpnCheckJob?.cancel()

        vpnCheckJob = lifecycleScope.launch {
            try {

                while (isActive && !isFinishing && !isDestroyed) {

                    delay(30000)

                    if (!isActive) break

                    if (VpnHelper.isVpnOrProxyActive(this@MainActivity)) {

                        withContext(Dispatchers.Main) {

                            if (!isFinishing && !isDestroyed) {

                                Toast.makeText(
                                    this@MainActivity,
                                    "VPN/Proxy detected! Closing app for security.",
                                    Toast.LENGTH_LONG
                                ).show()

                                delay(2000)

                                cleanupAndFinish()
                            }
                        }

                        break
                    }
                }

            } catch (_: CancellationException) {
            } catch (_: Exception) {
            }
        }
    }
}