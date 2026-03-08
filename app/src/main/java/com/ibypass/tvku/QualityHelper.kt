package com.ibypass.tvku

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

object QualityHelper {

    @OptIn(UnstableApi::class)
    fun getAvailableQualities(trackSelector: DefaultTrackSelector): List<QualityOption> {
        val qualities = mutableListOf<QualityOption>()

        // Add Auto option - get current selection state
        val currentParameters = trackSelector.parameters
        val isAutoSelected = currentParameters.maxVideoWidth == Int.MAX_VALUE &&
                currentParameters.maxVideoHeight == Int.MAX_VALUE

        qualities.add(
            QualityOption(
                id = "auto",
                name = "Auto",
                description = "Automatic quality selection",
                height = 0,
                width = 0,
                bitrate = 0,
                isSelected = isAutoSelected
            )
        )

        try {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo
            if (mappedTrackInfo != null) {
                for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
                    val trackType = mappedTrackInfo.getRendererType(rendererIndex)

                    if (trackType == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                        val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)

                        for (groupIndex in 0 until trackGroupArray.length) {
                            val trackGroup = trackGroupArray[groupIndex]

                            for (trackIndex in 0 until trackGroup.length) {
                                val format = trackGroup.getFormat(trackIndex)

                                if (format.height > 0 && format.width > 0) {
                                    val qualityName = getQualityName(format.height)
                                    val description = "${format.width}x${format.height}"

                                    // Use actual bitrate from format, or estimate if not available
                                    val actualBitrate = if (format.bitrate > 0) {
                                        format.bitrate.toLong()
                                    } else {
                                        estimateBitrate(format.height, format.width)
                                    }

                                    val existingQuality = qualities.find {
                                        it.height == format.height && it.width == format.width
                                    }

                                    // Check if this quality is currently selected
                                    val isCurrentlySelected = !isAutoSelected &&
                                            currentParameters.maxVideoHeight == format.height &&
                                            currentParameters.maxVideoWidth == format.width

                                    if (existingQuality == null) {
                                        qualities.add(
                                            QualityOption(
                                                id = "${format.width}x${format.height}",
                                                name = qualityName,
                                                description = description,
                                                height = format.height,
                                                width = format.width,
                                                bitrate = actualBitrate,
                                                isSelected = isCurrentlySelected
                                            )
                                        )
                                    } else if (actualBitrate > existingQuality.bitrate) {
                                        // Update with higher bitrate if found
                                        val index = qualities.indexOf(existingQuality)
                                        qualities[index] = existingQuality.copy(
                                            bitrate = actualBitrate,
                                            isSelected = isCurrentlySelected
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Add some default qualities if detection fails
            addDefaultQualities(qualities)
        }

        // If no video qualities found, add defaults
        if (qualities.size <= 1) {
            addDefaultQualities(qualities)
        }

        // Sort by height descending, but keep Auto first
        val sortedQualities = qualities.drop(1).sortedByDescending { it.height }
        return listOf(qualities.first()) + sortedQualities
    }

    private fun addDefaultQualities(qualities: MutableList<QualityOption>) {
        val defaultQualities = listOf(
            QualityOption("1920x1080", "1080p", "1920x1080", 1080, 1920, estimateBitrate(1080, 1920)),
            QualityOption("1280x720", "720p", "1280x720", 720, 1280, estimateBitrate(720, 1280)),
            QualityOption("854x480", "480p", "854x480", 480, 854, estimateBitrate(480, 854)),
            QualityOption("640x360", "360p", "640x360", 360, 640, estimateBitrate(360, 640))
        )

        defaultQualities.forEach { defaultQuality ->
            val existing = qualities.find {
                it.height == defaultQuality.height && it.width == defaultQuality.width
            }
            if (existing == null) {
                qualities.add(defaultQuality)
            }
        }
    }

    private fun estimateBitrate(height: Int, width: Int): Long {
        // Estimate bitrate based on resolution
        // These are reasonable estimates for streaming video
        return when {
            height >= 2160 -> 25_000_000L // 25 Mbps for 4K
            height >= 1440 -> 16_000_000L // 16 Mbps for 1440p
            height >= 1080 -> 8_000_000L  // 8 Mbps for 1080p
            height >= 720 -> 5_000_000L   // 5 Mbps for 720p
            height >= 480 -> 2_500_000L   // 2.5 Mbps for 480p
            height >= 360 -> 1_000_000L   // 1 Mbps for 360p
            height >= 240 -> 500_000L     // 500 Kbps for 240p
            else -> 250_000L              // 250 Kbps for lower
        }
    }

    private fun getQualityName(height: Int): String {
        return when {
            height >= 2160 -> "4K"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height >= 480 -> "480p"
            height >= 360 -> "360p"
            height >= 240 -> "240p"
            else -> "${height}p"
        }
    }

    @OptIn(UnstableApi::class)
    fun applyQualitySelection(
        trackSelector: DefaultTrackSelector,
        qualityOption: QualityOption
    ) {
        try {
            val parametersBuilder = trackSelector.parameters.buildUpon()

            if (qualityOption.id == "auto") {
                // Enable adaptive bitrate
                parametersBuilder
                    .clearVideoSizeConstraints()
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setMinVideoBitrate(0)
            } else {
                // Set specific quality constraints
                parametersBuilder
                    .setMaxVideoSize(qualityOption.width, qualityOption.height)
                    .setMinVideoSize(qualityOption.width, qualityOption.height)

                // Also set bitrate constraints if available
                if (qualityOption.bitrate > 0) {
                    parametersBuilder
                        .setMaxVideoBitrate(qualityOption.bitrate.toInt())
                        .setMinVideoBitrate((qualityOption.bitrate * 0.8).toInt()) // Allow 20% tolerance
                }
            }

            trackSelector.setParameters(parametersBuilder)

        } catch (e: Exception) {
        }
    }
}