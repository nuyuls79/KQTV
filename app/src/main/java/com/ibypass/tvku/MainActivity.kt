package com.ibypass.tvku

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.*
import androidx.lifecycle.lifecycleScope
import com.ibypass.tvku.ui.theme.TvkuTheme
import kotlinx.coroutines.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import com.ibypass.tvku.screens.SecurityWarningScreen
import com.ibypass.tvku.screens.ProfessionalIPTVScreen

class MainActivity : ComponentActivity() {

    private var vpnCheckJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        lifecycleScope.launch {

            try {

                if (VpnHelper.isVpnOrProxyActive(this@MainActivity)) {
                    showSecurityWarning()
                    return@launch
                }

                // PLAYLIST HARDCODE
                val playlistUrl = "https://iptv-org.github.io/iptv/index.m3u"

                setContent {
                    TvkuTheme(darkTheme = true) {
                        ProfessionalIPTVScreen(
                            playlistUrl = playlistUrl
                        )
                    }
                }

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    "Terjadi kesalahan",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }
    }

    private fun showSecurityWarning() {
        setContent {
            TvkuTheme {
                Surface {
                    SecurityWarningScreen {
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnCheckJob?.cancel()
    }
}