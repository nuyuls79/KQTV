package com.ibypass.tvku.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color

@Composable
fun Sidebar(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories) { category ->
                CategoryItem(
                        category = category,
                        isSelected = category == selectedCategory,
                        onClick = { onCategorySelected(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SidebarFooter()
        }
    }
}


@Composable
private fun SidebarFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ervan Kurnia • iBypass Team",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun CategoryItem(
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(
                color = Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = if (isFocused || isSelected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isSelected -> Color.White.copy(alpha = 0.8f)
                    else -> Color.Gray.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = when {
                            isFocused -> Color.White
                            isSelected -> Color.White
                            else -> Color.Gray.copy(alpha = 0.4f)
                        },
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = category,
                fontSize = 14.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isSelected && !isFocused) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            Color.White.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                Color.White,
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}
