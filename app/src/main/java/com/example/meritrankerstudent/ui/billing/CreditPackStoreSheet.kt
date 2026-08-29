package com.example.meritrankerstudent.ui.billing

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meritrankerstudent.data.billing.BillingState
import com.example.meritrankerstudent.data.billing.CreditPackItemState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditPackStoreSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreditStoreViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val billingState by viewModel.billingState.collectAsStateWithLifecycle()
    val packItems by viewModel.packItemStates.collectAsStateWithLifecycle()
    val userCredits by viewModel.userCredits.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header with current balance indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Buy Credits",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "One-time packs for tests, mocks & practice",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current Balance Strip
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current balance:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${userCredits?.creditsBalance ?: 0} Credits",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Credit Packs List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(packItems, key = { it.config.localPlanId }) { item ->
                    CreditPackCard(
                        itemState = item,
                        isLoading = billingState is BillingState.Connecting || billingState is BillingState.ProductLoading,
                        isLaunching = billingState is BillingState.Launching &&
                                (billingState as BillingState.Launching).localPlanId == item.config.localPlanId,
                        isVerifying = billingState is BillingState.Verifying &&
                                (billingState as BillingState.Verifying).payload.localPlanId == item.config.localPlanId,
                        onBuyClick = {
                            if (activity != null) {
                                viewModel.launchBuy(activity, item.config)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Google Play Security & Payment Methods Footnote
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Secure Google Play checkout. Eligible UPI (GPay, PhonePe, Paytm), Cards & NetBanking supported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    // ==================== STATE MACHINE OUTCOME DIALOGS ====================

    when (val state = billingState) {
        is BillingState.Verifying -> {
            AlertDialog(
                onDismissRequest = {},
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text("Verifying purchase…", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Connecting to MeritRanker backend to grant your credits securely."
                    )
                },
                confirmButton = {}
            )
        }

        is BillingState.Success -> {
            AlertDialog(
                onDismissRequest = {
                    viewModel.dismissState()
                    onDismiss()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(40.dp)
                    )
                },
                title = {
                    Text("Credits Added!", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Successfully added ${state.creditsGranted} credits. Your new balance is ${state.updatedBalance} credits."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissState()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        is BillingState.PurchasedUnverified -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissState() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text("Purchase received", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text("Google Play completed transaction. Verifying credits…")
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissState() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        is BillingState.Pending -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissState() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text("Payment pending", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "We'll add your credits once Google confirms the payment."
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.dismissState() }) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        is BillingState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissState() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text("Payment Notice", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(state.userMessage)
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissState() }) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        else -> {}
    }
}

@Composable
private fun CreditPackCard(
    itemState: CreditPackItemState,
    isLoading: Boolean,
    isLaunching: Boolean,
    isVerifying: Boolean,
    onBuyClick: () -> Unit
) {
    val config = itemState.config
    val googleDetails = itemState.googleProductDetails
    val isReadyForPurchase = itemState.isConfigured && itemState.isAvailable

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (config.isPopular)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (config.isPopular) 1.5.dp else 1.dp,
            color = if (config.isPopular) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Credit details and badge
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = config.displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (config.badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = config.badgeText,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Price display from Google Play or configuration state
                if (googleDetails != null) {
                    Text(
                        text = googleDetails.formattedPrice,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!itemState.isConfigured) {
                    Text(
                        text = "Coming Soon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Loading price...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = "Temporarily unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: Buy CTA Button
            Button(
                onClick = onBuyClick,
                enabled = isReadyForPurchase && !isLaunching && !isVerifying,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (isLaunching || isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (isReadyForPurchase) "Buy" else "Preview",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
