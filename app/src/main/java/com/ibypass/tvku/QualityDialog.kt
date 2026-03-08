package com.ibypass.tvku

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

class QualityDialog @OptIn(UnstableApi::class) constructor(
    private val context: Context,
    private val trackSelector: DefaultTrackSelector,
    private val onQualityChanged: (QualityOption) -> Unit
) {

    private var dialog: AlertDialog? = null
    private var selectedQuality: QualityOption? = null
    private val availableQualities = QualityHelper.getAvailableQualities(trackSelector)
    private var currentFocusIndex = 0
    private val qualityViews = mutableListOf<View>()
    private val radioButtons = mutableListOf<RadioButton>()

    fun show() {
        selectedQuality = availableQualities.firstOrNull { it.isSelected }
            ?: availableQualities.firstOrNull()

        val dialogView = createDialogView()

        dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog)
            .setView(dialogView)
            .setCancelable(true)
            .setOnKeyListener { _, keyCode, event ->
                handleKeyEvent(keyCode, event)
            }
            .create()

        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Responsive sizing
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val dialogWidth = when {
                screenWidth < 600 -> (screenWidth * 0.95).toInt()
                screenWidth < 800 -> (screenWidth * 0.85).toInt()
                else -> (screenWidth * 0.75).toInt()
            }

            val maxHeight = (screenHeight * 0.8).toInt()
            setLayout(dialogWidth, maxHeight)
        }

        dialog?.show()

        // Set initial focus
        if (qualityViews.isNotEmpty()) {
            currentFocusIndex = availableQualities.indexOfFirst { it.id == selectedQuality?.id }.takeIf { it >= 0 } ?: 0
            updateFocus()
        }
    }

    private fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (currentFocusIndex > 0) {
                    currentFocusIndex--
                    updateFocus()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (currentFocusIndex < qualityViews.size - 1) {
                    currentFocusIndex++
                    updateFocus()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (currentFocusIndex < availableQualities.size) {
                    selectQuality(availableQualities[currentFocusIndex])
                }
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                dialog?.dismiss()
                true
            }
            else -> false
        }
    }

    private fun updateFocus() {
        qualityViews.forEachIndexed { index, view ->
            val isFocused = index == currentFocusIndex
            view.isSelected = isFocused

            // Update background
            view.setBackgroundColor(
                if (isFocused) Color.parseColor("#404040") else Color.TRANSPARENT
            )

            // Request focus
            if (isFocused) {
                view.requestFocus()
            }
        }
    }

    private fun selectQuality(quality: QualityOption) {
        selectedQuality = quality
        updateRadioButtons()

        // Apply immediately and close dialog
        QualityHelper.applyQualitySelection(trackSelector, quality)
        onQualityChanged(quality)
        dialog?.dismiss()
    }

    private fun updateRadioButtons() {
        radioButtons.forEachIndexed { index, radioButton ->
            radioButton.isChecked = availableQualities[index].id == selectedQuality?.id
        }
    }

    fun setOnShowListener(listener: () -> Unit) {
        dialog?.setOnShowListener { listener() }
    }

    fun setOnDismissListener(listener: () -> Unit) {
        dialog?.setOnDismissListener { listener() }
    }

    private fun createDialogView(): View {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.parseColor("#2C2C2E"))
            isFocusableInTouchMode = true
        }

        // Header
        val headerLayout = createHeader()
        rootLayout.addView(headerLayout)

        // Content
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = true
            setPadding(0, 8, 0, 8)
            isFocusable = false
        }

        val qualityList = createQualityList()
        scrollView.addView(qualityList)
        rootLayout.addView(scrollView)

        // Footer buttons
        val footerLayout = createFooter()
        rootLayout.addView(footerLayout)

        return rootLayout
    }

    private fun createHeader(): View {
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 12)
            setBackgroundColor(Color.parseColor("#2C2C2E"))
        }

        val titleText = TextView(context).apply {
            text = "Pilih Kualitas Video"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitleText = TextView(context).apply {
            text = "Gunakan ↑↓ untuk navigasi, Enter untuk pilih"
            textSize = 13f
            setTextColor(Color.parseColor("#BBBBBB"))
            setPadding(0, 4, 0, 0)
        }

        headerLayout.addView(titleText)
        headerLayout.addView(subtitleText)

        // Divider
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                topMargin = 12
            }
            setBackgroundColor(Color.parseColor("#444444"))
        }
        headerLayout.addView(divider)

        return headerLayout
    }

    private fun createQualityList(): View {
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 0, 8, 0)
        }

        qualityViews.clear()
        radioButtons.clear()

        availableQualities.forEachIndexed { index, quality ->
            val itemView = createQualityItem(quality, index)
            listLayout.addView(itemView)
            qualityViews.add(itemView)

            // Add divider except for last item
            if (index < availableQualities.size - 1) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        leftMargin = 60
                        rightMargin = 20
                    }
                    setBackgroundColor(Color.parseColor("#333333"))
                }
                listLayout.addView(divider)
            }
        }

        return listLayout
    }

    private fun createQualityItem(quality: QualityOption, index: Int): View {
        val itemLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true

            setOnClickListener {
                currentFocusIndex = index
                selectQuality(quality)
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    currentFocusIndex = index
                    updateFocus()
                }
            }
        }

        // Radio button
        val radioButton = RadioButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 16
            }

            isChecked = quality.isSelected || (selectedQuality?.id == quality.id)
            isFocusable = false
            isClickable = false

            // Custom colors for better visibility
            try {
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#007AFF"))
            } catch (e: Exception) {
                // Fallback for older Android versions
            }
        }

        radioButtons.add(radioButton)

        // Quality info container
        val infoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // Quality name and resolution
        val nameText = TextView(context).apply {
            text = if (quality.id == "auto") {
                "${quality.name} (Otomatis)"
            } else {
                "${quality.name} • ${quality.description}"
            }
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        infoLayout.addView(nameText)

        // Bitrate info (only show for non-auto qualities)
        if (quality.id != "auto" && quality.bitrate > 0) {
            val bitrateText = TextView(context).apply {
                text = "${formatBitrate(quality.bitrate)} • ${getQualityDescription(quality.height)}"
                textSize = 13f
                setTextColor(Color.parseColor("#BBBBBB"))
                setPadding(0, 2, 0, 0)
            }
            infoLayout.addView(bitrateText)
        } else if (quality.id == "auto") {
            val autoText = TextView(context).apply {
                text = "Menyesuaikan dengan koneksi internet"
                textSize = 13f
                setTextColor(Color.parseColor("#34C759"))
                setPadding(0, 2, 0, 0)
            }
            infoLayout.addView(autoText)
        }

        itemLayout.addView(radioButton)
        itemLayout.addView(infoLayout)

        return itemLayout
    }

    private fun createFooter(): View {
        val footerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 12, 20, 20)
            setBackgroundColor(Color.parseColor("#2C2C2E"))
        }

        // Divider
        val topDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                bottomMargin = 12
            }
            setBackgroundColor(Color.parseColor("#444444"))
        }
        footerLayout.addView(topDivider)

        // Instructions
        val instructionText = TextView(context).apply {
            text = "💡 Tips: Gunakan tombol ↑↓ untuk navigasi, Enter untuk memilih, Back untuk keluar"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, 8)
        }
        footerLayout.addView(instructionText)

        // Buttons
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        footerLayout.addView(buttonRow)

        return footerLayout
    }

    private fun formatBitrate(bitrate: Long): String {
        return when {
            bitrate >= 1_000_000 -> String.format("%.1f Mbps", bitrate / 1_000_000f)
            bitrate >= 1_000 -> String.format("%.0f Kbps", bitrate / 1_000f)
            else -> "${bitrate} bps"
        }
    }

    private fun getQualityDescription(height: Int): String {
        return when {
            height >= 2160 -> "Ultra HD"
            height >= 1440 -> "Quad HD"
            height >= 1080 -> "Full HD"
            height >= 720 -> "HD"
            height >= 480 -> "SD"
            else -> "Low Quality"
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}