package com.example.meritrankerstudent.ui.auth

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meritrankerstudent.R
import com.example.meritrankerstudent.util.LegalConstants

// Intentional brand accent color
private val BrandOrange = Color(0xFFF97316)

@Composable
fun LoginScreen(
    onSignInClick: (Activity) -> Unit,
    error: String?,
    isSigningIn: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isDark = isSystemInDarkTheme()

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Ultra-subtle ambient depth behind upper hero
                    val centerX = size.width * 0.5f
                    val centerY = size.height * 0.26f
                    val ambientRadius = size.width * 0.85f

                    if (isDark) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.08f),
                                    BrandOrange.copy(alpha = 0.02f),
                                    Color.Transparent
                                ),
                                center = Offset(centerX, centerY),
                                radius = ambientRadius
                            ),
                            radius = ambientRadius,
                            center = Offset(centerX, centerY)
                        )
                    } else {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.05f),
                                    BrandOrange.copy(alpha = 0.02f),
                                    Color.Transparent
                                ),
                                center = Offset(centerX, centerY),
                                radius = ambientRadius
                            ),
                            radius = ambientRadius,
                            center = Offset(centerX, centerY)
                        )
                    }
                }
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val minHeight = maxHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = minHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top breathing room
                Spacer(modifier = Modifier.weight(0.35f).heightIn(min = 16.dp))

                // ================= 1. Brand Hero Section =================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Subtle Logo Emblem
                    Surface(
                        color = surfaceColor,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            width = 0.5.dp,
                            color = outlineVariantColor.copy(alpha = 0.5f)
                        ),
                        shadowElevation = if (isDark) 2.dp else 1.dp,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_short),
                                contentDescription = "MeritRanker Logo",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Brand Title: "Merit" + "Ranker"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Merit",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "Ranker",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandOrange,
                            letterSpacing = (-0.3).sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tagline
                    Text(
                        text = "Learn smarter. Practice with purpose.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurfaceVariantColor,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Exam Coverage Line
                    Text(
                        text = "Built for SSC • Railway • Banking • State Exams",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceVariantColor.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Single Minimal Capability Surface (Consistent icons & subtle styling)
                    Surface(
                        color = surfaceVariantColor.copy(alpha = if (isDark) 0.5f else 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = 0.5.dp,
                            color = outlineVariantColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Item 1: Ask Doubts
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Ask Doubts",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurfaceColor
                                )
                            }

                            // Divider dot
                            Text(
                                text = "•",
                                fontSize = 10.sp,
                                color = outlineColor.copy(alpha = 0.4f)
                            )

                            // Item 2: Practice
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Practice",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurfaceColor
                                )
                            }

                            // Divider dot
                            Text(
                                text = "•",
                                fontSize = 10.sp,
                                color = outlineColor.copy(alpha = 0.4f)
                            )

                            // Item 3: Track Progress
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Progress",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = FontWeight.Medium,
                                    color = onSurfaceColor
                                )
                            }
                        }
                    }
                }

                // Middle flexible breathing room
                Spacer(modifier = Modifier.weight(1f).heightIn(min = 28.dp))

                // ================= 2. Action & Footer Section =================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (error != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // Dominant Google Sign-In Button (Stands cleanly on its own)
                    Button(
                        onClick = { activity?.let { onSignInClick(it) } },
                        enabled = !isSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = surfaceColor,
                            contentColor = onSurfaceColor
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = outlineVariantColor
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (isDark) 2.dp else 1.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                color = primaryColor,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google),
                                    contentDescription = "Google Logo",
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continue with Google",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurfaceColor,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Legal Terms & Privacy Policy Copy (~12sp with bodySmall)
                    val legalAnnotatedString = buildAnnotatedString {
                        append("By continuing, you agree to the ")
                        pushStringAnnotation(tag = "TERMS", annotation = LegalConstants.TERMS_OF_SERVICE_URL)
                        withStyle(
                            style = SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("Terms of Service")
                        }
                        pop()
                        append(" and acknowledge the\n")
                        pushStringAnnotation(tag = "PRIVACY", annotation = LegalConstants.PRIVACY_POLICY_URL)
                        withStyle(
                            style = SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("Privacy Policy")
                        }
                        pop()
                        append(".")
                    }

                    ClickableText(
                        text = legalAnnotatedString,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = onSurfaceVariantColor.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onClick = { offset ->
                            legalAnnotatedString.getStringAnnotations(tag = "TERMS", start = offset, end = offset).firstOrNull()?.let {
                                LegalConstants.openUrl(context, it.item)
                            }
                            legalAnnotatedString.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset).firstOrNull()?.let {
                                LegalConstants.openUrl(context, it.item)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Company Identity Footer (No v1.0)
                    Text(
                        text = LegalConstants.OPERATING_ENTITY,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariantColor.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
