package com.ibypass.tvku

data class QualityOption(
    val id: String,
    val name: String,
    val description: String,
    val height: Int,
    val width: Int,
    val bitrate: Long,
    val isSelected: Boolean = false
)