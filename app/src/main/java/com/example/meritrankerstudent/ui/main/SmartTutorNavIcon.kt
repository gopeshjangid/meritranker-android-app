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
import androidx.compose.foundation.layout.padding
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
 * Renders an animated gradient ring (BrandBlue -> BrandOrange -> BrandBlue) when assessments
 * are generating, a tiny count badge when multiple generations are active, and a subtle
 * ready check when an assessment is ready.
 */
@Composable
fun SmartTutorNavIcon(
    activeCount: Int,
    isReady: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val isGenerating = activeCount > 0 && !isReady

    val angle by if (isGenerating) {
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
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.size(34.dp),
        contentAlignment = Alignment.Center
    ) {
        // Rotating gradient border ring during generation
        if (isGenerating) {
            val ringBrush = Brush.sweepGradient(
                listOf(
                    MeritRankerColors.BrandBlue,
                    MeritRankerColors.BrandOrange,
                    MeritRankerColors.BrandBlue
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

        // Multiple active count badge
        if (activeCount > 1 && isGenerating) {
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

        // Ready indicator badge
        if (isReady) {
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
