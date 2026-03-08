package com.ibypass.tvku

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ibypass.tvku.ui.theme.TvkuTheme
import com.ibypass.tvku.utils.RulesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TvkuTheme {
                SplashScreen()
            }
        }

        lifecycleScope.launch {

            try {

                // Delay kecil supaya splash terlihat
                delay(800)

                // Inisialisasi Rules
                RulesManager.initializeRules(this@SplashActivity)

                val rulesValid = RulesManager.validateRules(this@SplashActivity)

                if (!rulesValid) {
                    Toast.makeText(
                        this@SplashActivity,
                        "Terjadi kesalahan sistem",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                    return@launch
                }

                // Delay transisi
                delay(600)

                // Masuk ke MainActivity
                startActivity(
                    Intent(this@SplashActivity, MainActivity::class.java)
                )

                finish()

            } catch (e: Exception) {

                Toast.makeText(
                    this@SplashActivity,
                    "Gagal memulai aplikasi",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }
    }
}

@Composable
fun SplashScreen() {

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "KQTV",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                CircularProgressIndicator()

            }
        }
    }
}