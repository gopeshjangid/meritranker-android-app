package com.example.meritrankerstudent.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meritrankerstudent.theme.MeritRankerColors

/**
 * Isolated Bottom Navigation Smart Tutor Icon Composable.
 *
 * Renders:
 * 1. A thin, slow, smooth rotating ring (BrandOrange / Primary sweep) when Smart Tutor
 *    has ANY active async operation (Normal Doubt Answer Streaming OR Practice Generation).
 * 2. A tiny active count badge if multiple async tasks are simultaneously running.
 * 3. A subtle green ready checkmark badge when practice/mock is ready to play.
 *
 * Isolated to prevent unnecessary recomposition of parent navigation items or active screens.
 */
@Composable
fun SmartTutorNavIcon(
    isBusy: Boolean,
    activeCount: Int,
    isReady: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val angle by if (isBusy) {
        val infiniteTransition = rememberInfiniteTransition(label = "smartTutorNavRing")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ringRotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.size(34.dp),
        contentAlignment = Alignment.Center
    ) {
        // Rotating gradient border ring during any asynchronous Smart Tutor work
        if (isBusy) {
            val ringBrush = Brush.sweepGradient(
                listOf(
                    MeritRankerColors.BrandOrange,
                    MeritRankerColors.BrandOrangeLight,
                    MeritRankerColors.BrandOrange
                )
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .rotate(angle)
                    .border(width = 1.5.dp, brush = ringBrush, shape = CircleShape)
            )
        }

        // Center AI Icon
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Smart Tutor",
            modifier = Modifier.size(22.dp)
        )

        // Multiple active tasks count badge
        if (activeCount > 1 && isBusy) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MeritRankerColors.BrandOrange),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (activeCount > 9) "9+" else activeCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 9.sp
                )
            }
        }

        // Ready indicator badge (shown only when not currently generating new work)
        if (isReady && !isBusy) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(MeritRankerColors.Success)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Ready",
                    tint = Color.White,
                    modifier = Modifier.size(8.dp)
                )
            }
        }
    }
}
