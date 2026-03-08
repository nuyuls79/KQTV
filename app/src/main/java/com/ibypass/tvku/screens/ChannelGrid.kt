package com.ibypass.tvku.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import com.ibypass.tvku.Channel
import coil.request.ImageRequest
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import androidx.compose.ui.graphics.painter.ColorPainter

@Composable
fun ChannelGrid(
    channels: List<Channel>,
    columns: Int,
    showLogos: Boolean,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(channels) { channel ->
            ChannelCard(
                channel = channel,
                showLogo = showLogos,
                onClick = onChannelClick
            )
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    showLogo: Boolean,
    onClick: (Channel) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick(channel) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                width = if (isFocused) 2.dp else 1.2.dp,
                color = if (isFocused)
                    Color.White.copy(alpha = 0.8f)
                else
                    Color.Gray.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            ChannelBackground(channel = channel, showLogo = showLogo)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MarqueeText(
                text = channel.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )

            if (channel.group.isNotEmpty()) {
                Text(
                    text = channel.group,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    delayMillis: Int = 1000,
    pixelsPerSecond: Float = 30f
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val textStyle = TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight
    )

    var containerWidth by remember { mutableStateOf(0f) }
    var textWidth by remember { mutableStateOf(0f) }

    LaunchedEffect(text, textStyle) {
        val textLayoutResult = textMeasurer.measure(text, textStyle)
        textWidth = with(density) { textLayoutResult.size.width.toDp().toPx() }
    }

    val shouldAnimate = textWidth > containerWidth && containerWidth > 0f

    val distance = if (shouldAnimate) textWidth + containerWidth else 0f
    val animationDuration = if (shouldAnimate) (distance / pixelsPerSecond * 1000).toInt() else 1000

    val infiniteTransition = rememberInfiniteTransition(label = "MarqueeTransition")

    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = if (shouldAnimate) containerWidth else 0f,
        targetValue = if (shouldAnimate) -textWidth else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = LinearEasing,
                delayMillis = delayMillis
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "MarqueeOffset"
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .drawWithContent {
                containerWidth = size.width

                if (shouldAnimate) {
                    translate(left = animatedOffset) {
                        this@drawWithContent.drawContent()
                    }
                } else {
                    drawContent()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
private fun ChannelBackground(channel: Channel, showLogo: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        if (showLogo && channel.logo.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.logo)
                    .crossfade(true)
                    .allowHardware(false)
                    .memoryCacheKey(channel.logo)
                    .diskCacheKey(channel.logo)
                    .placeholder(android.R.drawable.screen_background_dark_transparent)
                    .error(android.R.drawable.screen_background_dark_transparent)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        } else {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(
                        color = Color.White.copy(alpha = 0.8f),
                        offset = Offset(0f, 0f),
                        blurRadius = 12f
                    )
                )
            )
        }
    }
}