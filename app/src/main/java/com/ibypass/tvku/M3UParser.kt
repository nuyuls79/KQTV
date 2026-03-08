
package com.ibypass.tvku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val referer: String? = null,
    val drmId: String? = null,
    val drmType: String? = null,
    val drmKey: String? = null
)

data class DrmLicense(
    val drmId: String,
    val drmType: String,
    val drmKey: String
)

class M3UParser {

    companion object {
        private const val TAG = "M3UParser"

        suspend fun parseFromUrl(url: String): List<Channel> = withContext(Dispatchers.IO) {
            try {
                val content = downloadContent(url)
                val channels = parseContent(content)
                channels
            } catch (e: Exception) {
                throw Exception("Parse error: ${e.message}")
            }
        }

        suspend fun parseFromUrlWithDrm(url: String): Pair<List<Channel>, List<DrmLicense>> = withContext(Dispatchers.IO) {
            try {
                val content = downloadContent(url)
                val result = parseContentWithDrm(content)
                result
            } catch (e: Exception) {
                throw Exception("Enhanced parse error: ${e.message}")
            }
        }

        private fun parseContentWithDrm(content: String): Pair<List<Channel>, List<DrmLicense>> {
            val channels = mutableListOf<Channel>()
            val drmLicenses = mutableListOf<DrmLicense>()
            val lines = content.split("\n")

            var i = 0
            var drmCounter = 0

            while (i < lines.size) {
                val line = lines[i].trim()

                when {
                    line.startsWith("#EXTINF:") -> {
                        try {
                            val result = parseChannelBlock(lines, i)
                            if (result.channel != null) {
                                channels.add(result.channel)

                                if (result.channel.drmType != null && result.channel.drmKey != null) {
                                    val drmId = result.channel.drmId ?: "drm_${drmCounter++}"
                                    drmLicenses.add(
                                        DrmLicense(
                                            drmId = drmId,
                                            drmType = result.channel.drmType,
                                            drmKey = result.channel.drmKey
                                        )
                                    )
                                }
                            }
                            i = result.nextIndex
                        } catch (e: Exception) {
                            i++
                        }
                    }
                    else -> i++
                }
            }

            return Pair(channels, drmLicenses)
        }

        private fun parseContent(content: String): List<Channel> {
            val result = parseContentWithDrm(content)
            return result.first
        }

        private fun parseChannelBlock(lines: List<String>, startIndex: Int): ChannelParseResult {
            val extinf = lines[startIndex].trim()

            val name = extractChannelName(extinf)
            val logo = extractAttribute(extinf, "tvg-logo")
            val group = extractAttribute(extinf, "group-title")

            val headers = mutableMapOf<String, String>()
            var userAgent: String? = null
            var referer: String? = null
            var drmType: String? = null
            var drmKey: String? = null
            var drmId: String? = null

            var manifestType: String? = null

            var j = startIndex + 1
            var streamUrl = ""

            while (j < lines.size) {
                val nextLine = lines[j].trim()

                when {
                    nextLine.startsWith("#EXTVLCOPT:http-referrer=") -> {
                        referer = nextLine.substringAfter("=")
                        headers["Referer"] = referer
                    }
                    nextLine.startsWith("#EXTVLCOPT:http-user-agent=") -> {
                        userAgent = nextLine.substringAfter("=")
                        headers["User-Agent"] = userAgent
                    }
                    nextLine.startsWith("#EXTVLCOPT:http-origin=") -> {
                        headers["Origin"] = nextLine.substringAfter("=")
                    }

                    nextLine.startsWith("#KODIPROP:inputstream.adaptive.license_type=") -> {
                        val rawType = nextLine.substringAfter("=")
                        drmType = when (rawType) {
                            "clearkey" -> "clearkey"
                            "com.widevine.alpha" -> "widevine"
                            "com.microsoft.playready" -> "playready"
                            else -> rawType
                        }
                    }

                    nextLine.startsWith("#KODIPROP:inputstream.adaptive.manifest_type=") -> {
                        manifestType = nextLine.substringAfter("=")
                        headers["manifest_type"] = manifestType
                    }

                    nextLine.startsWith("#KODIPROP:inputstream.adaptive.license_key=") -> {
                        drmKey = nextLine.substringAfter("=")

                        if (drmType == "clearkey" && drmKey?.contains(":") == true) {
                            drmKey = buildClearkeyDataUri(drmKey!!)
                        }
                    }

                    nextLine.startsWith("#KODIPROP:inputstreamaddon=") -> {
                        val addon = nextLine.substringAfter("=")
                        headers["inputstreamaddon"] = addon
                    }

                    !nextLine.startsWith("#") && nextLine.isNotEmpty() -> {
                        streamUrl = parseStreamUrl(nextLine)

                        val urlParts = nextLine.split("|")
                        if (urlParts.size > 1) {
                            streamUrl = urlParts[0]
                            for (k in 1 until urlParts.size) {
                                val headerPart = urlParts[k]
                                when {
                                    headerPart.startsWith("user-agent=") -> {
                                        userAgent = headerPart.substringAfter("=")
                                        headers["User-Agent"] = userAgent
                                    }
                                    headerPart.startsWith("referer=") -> {
                                        referer = headerPart.substringAfter("=")
                                        headers["Referer"] = referer
                                    }
                                    headerPart.startsWith("origin=") -> {
                                        headers["Origin"] = headerPart.substringAfter("=")
                                    }
                                }
                            }
                        }
                        break
                    }

                    nextLine.isEmpty() -> {
                    }

                    else -> {
                    }
                }
                j++
            }

            if (drmType != null && drmKey != null && drmId == null) {
                drmId = "drm_${drmType}_${drmKey.hashCode().toString(16).takeLast(8)}"
            }

            val channel = if (streamUrl.isNotEmpty() && name.isNotEmpty()) {
                Channel(
                    name = name,
                    url = streamUrl,
                    logo = logo,
                    group = group,
                    headers = headers.toMap(),
                    userAgent = userAgent,
                    referer = referer,
                    drmId = drmId,
                    drmType = drmType,
                    drmKey = drmKey
                )
            } else null

            return ChannelParseResult(channel, j + 1)
        }

        private fun parseStreamUrl(urlLine: String): String {
            val urls = urlLine.split("\n", "\r\n").filter { it.trim().isNotEmpty() }
            return urls.firstOrNull { it.trim().startsWith("http") }?.trim()
                ?: urlLine.split("|")[0].trim()
        }

        private fun buildClearkeyDataUri(licenseKey: String): String {
            return try {
                val parts = licenseKey.split(":")
                if (parts.size == 2) {
                    val kid = parts[0].lowercase()
                    val k = parts[1].lowercase()
                    val json = """
                {
                  "keys": [
                    {
                      "kty": "oct",
                      "kid": "$kid",
                      "k": "$k"
                    }
                  ],
                  "type": "temporary"
                }
            """.trimIndent()
                    val encoded = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
                    "data:application/json;base64,$encoded"
                } else {
                    licenseKey
                }
            } catch (e: Exception) {
                licenseKey
            }
        }

        private fun downloadContent(url: String): String {
            return try {
                return downloadWithRetry(url, maxRetries = 3)
            } catch (e: java.net.SocketTimeoutException) {
                throw Exception("Connection timeout - Cloudflare might be enforcing strict limits")
            } catch (e: java.net.ConnectException) {
                throw Exception("Connection failed: ${e.message}")
            } catch (e: javax.net.ssl.SSLException) {
                throw Exception("SSL/TLS error: ${e.message}")
            } catch (e: Exception) {
                throw Exception("Download failed: ${e.message}")
            }
        }

        private fun downloadWithRetry(url: String, maxRetries: Int): String {
            val userAgents = listOf(
                "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0"
            )

            for (attempt in 0 until maxRetries) {
                try {
                    val userAgent = userAgents[attempt % userAgents.size]
                    val result = attemptDownload(url, userAgent, attempt)
                    if (result != null) return result
                    if (attempt < maxRetries - 1) {
                        val delay = (1000 * (attempt + 1) * 2).toLong()
                        Thread.sleep(delay)
                    }
                } catch (e: Exception) {
                    if (attempt == maxRetries - 1) throw e
                }
            }

            throw Exception("All download attempts failed")
        }

        private fun attemptDownload(url: String, userAgent: String, attempt: Int): String? {
            val connection = URL(url).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 25000
                connection.readTimeout = 25000
                connection.instanceFollowRedirects = true
                connection.doInput = true

                when (attempt % 3) {
                    0 -> setupMobileHeaders(connection, userAgent)
                    1 -> setupDesktopHeaders(connection, userAgent)
                    else -> setupMinimalHeaders(connection, userAgent)
                }

                val responseCode = connection.responseCode

                return when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val inputStream = when (connection.contentEncoding) {
                            "gzip" -> java.util.zip.GZIPInputStream(connection.inputStream)
                            "deflate" -> java.util.zip.InflaterInputStream(connection.inputStream)
                            "br" -> {
                                connection.inputStream
                            }
                            else -> connection.inputStream
                        }

                        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        content
                    }

                    HttpURLConnection.HTTP_FORBIDDEN,
                    HttpURLConnection.HTTP_UNAVAILABLE,
                    429 -> {
                        null
                    }

                    else -> {
                        val errorStream = connection.errorStream
                        val errorMessage = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        throw Exception("HTTP Error $responseCode: $errorMessage")
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun setupMobileHeaders(connection: HttpURLConnection, userAgent: String) {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
            connection.setRequestProperty("Connection", "keep-alive")
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
            connection.setRequestProperty("Sec-Fetch-Dest", "document")
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
            connection.setRequestProperty("Sec-Fetch-Site", "none")
            connection.setRequestProperty("Sec-Fetch-User", "?1")
            connection.setRequestProperty("Cache-Control", "max-age=0")
        }

        private fun setupDesktopHeaders(connection: HttpURLConnection, userAgent: String) {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br")
            connection.setRequestProperty("DNT", "1")
            connection.setRequestProperty("Connection", "keep-alive")
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
            connection.setRequestProperty("Sec-Fetch-Dest", "document")
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
            connection.setRequestProperty("Sec-Fetch-Site", "none")
            connection.setRequestProperty("Sec-Fetch-User", "?1")
        }

        private fun setupMinimalHeaders(connection: HttpURLConnection, userAgent: String) {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("Connection", "keep-alive")
        }

        private fun extractChannelName(extinf: String): String {
            return try {
                val commaIndex = extinf.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex < extinf.length - 1) {
                    extinf.substring(commaIndex + 1).trim()
                } else {
                    "Unknown Channel"
                }
            } catch (e: Exception) {
                "Unknown Channel"
            }
        }

        private fun extractAttribute(extinf: String, attribute: String): String {
            return try {
                val pattern = """$attribute="([^"]*)"""".toRegex()
                val match = pattern.find(extinf)
                match?.groupValues?.get(1)?.trim() ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }
}

private data class ChannelParseResult(
    val channel: Channel?,
    val nextIndex: Int
)