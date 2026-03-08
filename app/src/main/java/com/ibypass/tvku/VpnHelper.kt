package com.ibypass.tvku

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import android.util.Base64

object VpnHelper {
    private const val TAG = "VpnHelper"
    private val secureRandom = SecureRandom()

    private val dangerousSniffingApps = listOf(
        "com.proxyman", "com.charlesproxy.charles",
        "app.greyshirts.sslcapture", "com.httptoolkit.android.v1",
        "jp.co.taosoftware.android.packetcapture",
        "lv.n3o.lcapture", "com.minhui.networkcapture",
        "com.packagesniffer", "com.egorovandreyrm.wireshark",

        "com.android.ssl.kill.switch", "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager", "com.topjohnwu.magisk.frida",
        "com.github.unidbg.android", "jadx.gui.android",

        "com.github.shadowsocks", "io.nekohasekai.sagernet",
        "com.clash.vpn", "com.v2ray.ang"
    )

    private val maliciousHosts = listOf(
        "mitmproxy", "burpsuite", "owasp", "fiddler",
        "proxyman", "charles", "wireshark", "tcpdump"
    )

    fun isVpnOrProxyActive(context: Context): Boolean {
        return isVpnTransportActive(context) ||
                hasSuspiciousNetworkInterface() ||
                isHttpProxySet() ||
                detectMaliciousProxy() ||
                checkSuspiciousDns() ||
                detectSniffingTools(context) ||
                isActivelyBeingDebugged() ||
                detectSSLBypassTools()
    }

    private fun isVpnTransportActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun hasSuspiciousNetworkInterface(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var suspiciousCount = 0

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name?.lowercase() ?: continue

                if ((iface.isUp && !iface.isLoopback && iface.interfaceAddresses.isNotEmpty()) &&
                    listOf("tun", "tap", "ppp", "vpn", "clash", "v2ray", "proxy", "mitm").any { name.contains(it) }) {
                    suspiciousCount++
                }
            }

            suspiciousCount > 1
        } catch (e: Exception) {
            false
        }
    }

    private fun isHttpProxySet(): Boolean {
        val httpProxy = System.getProperty("http.proxyHost")
        val httpsProxy = System.getProperty("https.proxyHost")
        val socksProxy = System.getProperty("socksProxyHost")
        val proxyPort = System.getProperty("http.proxyPort")

        return (!httpProxy.isNullOrEmpty() && !proxyPort.isNullOrEmpty()) ||
                !httpsProxy.isNullOrEmpty() ||
                !socksProxy.isNullOrEmpty()
    }

    private fun detectMaliciousProxy(): Boolean {
        return try {
            val maliciousProxyPorts = listOf(8080, 8888, 3128, 8082, 8083, 9090)
            val localhost = InetAddress.getByName("127.0.0.1")

            maliciousProxyPorts.count { port ->
                try {
                    Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress(localhost, port), 50)
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            } > 1
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSuspiciousDns(): Boolean {
        return try {
            val systemDns = System.getProperty("net.dns1") ?: ""
            maliciousHosts.any { host -> systemDns.contains(host, true) }
        } catch (e: Exception) {
            false
        }
    }

    private fun detectSniffingTools(context: Context): Boolean {
        val packageManager = context.packageManager
        var foundCount = 0

        dangerousSniffingApps.forEach { packageName ->
            try {
                packageManager.getPackageInfo(packageName, 0)
                foundCount++
            } catch (e: PackageManager.NameNotFoundException) {
            }
        }

        return foundCount > 1
    }

    private fun isActivelyBeingDebugged(): Boolean {
        return try {
            android.os.Debug.isDebuggerConnected() ||
                    android.os.Debug.waitingForDebugger()
        } catch (e: Exception) {
            false
        }
    }

    private fun detectSSLBypassTools(): Boolean {
        return try {
            val bypassIndicators = listOf(
                "frida", "xposed", "substrate", "cydia"
            )

            bypassIndicators.any { tool ->
                !System.getProperty("ro.${tool}.present").isNullOrEmpty() ||
                        !System.getProperty("${tool}.server").isNullOrEmpty()
            } || detectXposedFramework() || detectFridaServer()
        } catch (e: Exception) {
            false
        }
    }

    private fun detectXposedFramework(): Boolean {
        return try {
            val xposedFiles = listOf(
                "/system/framework/XposedBridge.jar",
                "/data/data/de.robv.android.xposed.installer"
            )

            xposedFiles.any { File(it).exists() }
        } catch (e: Exception) {
            false
        }
    }

    private fun detectFridaServer(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", 27042), 100)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun detectProcessMonitoring(): Boolean {
        return try {
            val suspiciousProcesses = listOf(
                "tcpdump", "wireshark", "tshark", "mitmdump",
                "frida-server", "gdb", "strace"
            )

            val processDir = File("/proc")
            if (processDir.exists() && processDir.isDirectory) {
                processDir.listFiles()?.any { procDir ->
                    if (procDir.isDirectory && procDir.name.matches(Regex("\\d+"))) {
                        try {
                            val cmdlineFile = File(procDir, "cmdline")
                            if (cmdlineFile.exists()) {
                                val cmdline = cmdlineFile.readText().lowercase()
                                suspiciousProcesses.any { proc -> cmdline.contains(proc) }
                            } else false
                        } catch (e: Exception) {
                            false
                        }
                    } else false
                } ?: false
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun generateSecureHeaders(context: Context): Map<String, String> {
        val deviceId = getDeviceFingerprint(context)
        val timestamp = System.currentTimeMillis().toString()
        val nonce = generateSecureToken()

        return mapOf(
            "User-Agent" to generateSecureUserAgent(),
            "X-Device-ID" to hashString(deviceId),
            "X-Timestamp" to encryptData(timestamp, deviceId),
            "X-Nonce" to nonce,
            "X-App-Signature" to generateAppSignature(context, timestamp, nonce),
            "Cache-Control" to "no-cache, no-store, must-revalidate",
            "Pragma" to "no-cache",
            "Accept-Encoding" to "gzip, deflate",
            "Connection" to "close"
        )
    }

    private fun getDeviceFingerprint(context: Context): String {
        val deviceInfo = listOf(
            Build.DEVICE,
            Build.MODEL,
            Build.PRODUCT,
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ).joinToString("|")

        return hashString(deviceInfo)
    }

    private fun generateSecureUserAgent(): String {
        val version = "STB/${System.currentTimeMillis() % 10000}"
        return "STBPlayer/$version (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"
    }

    private fun generateAppSignature(context: Context, timestamp: String, nonce: String): String {
        val packageName = context.packageName
        val signature = "$packageName|$timestamp|$nonce"
        return hashString(signature).take(16)
    }

    fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING)
    }

    fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input
        }
    }

    fun encryptData(data: String, key: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key.take(16).padEnd(16, '0').toByteArray(), "AES")
            val iv = ByteArray(16)
            secureRandom.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(data.toByteArray())

            Base64.encodeToString(iv + encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            data
        }
    }

    fun protectUrl(originalUrl: String): String {
        return try {
            val encodedUrl = Base64.encodeToString(
                originalUrl.toByteArray(),
                Base64.URL_SAFE or Base64.NO_PADDING
            )
            "protected://${hashString(originalUrl).take(8)}/$encodedUrl"
        } catch (e: Exception) {
            originalUrl
        }
    }

    fun unprotectUrl(protectedUrl: String): String {
        return try {
            if (protectedUrl.startsWith("protected://")) {
                val parts = protectedUrl.removePrefix("protected://").split("/", limit = 2)
                if (parts.size == 2) {
                    String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
                } else {
                    protectedUrl
                }
            } else {
                protectedUrl
            }
        } catch (e: Exception) {
            protectedUrl
        }
    }

    fun isNetworkSecure(context: Context): Boolean {
        return !isVpnOrProxyActive(context) || !detectProcessMonitoring()
    }
}