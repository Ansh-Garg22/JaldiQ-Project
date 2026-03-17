package com.example.jaldiqproject.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jaldiqproject.model.Shop
import com.example.jaldiqproject.model.Token
import com.example.jaldiqproject.ui.theme.QueueGreen
import com.example.jaldiqproject.ui.theme.QueueRed
import com.example.jaldiqproject.ui.theme.QueueYellow
import com.example.jaldiqproject.viewmodel.ShopOwnerViewModel
import com.example.jaldiqproject.viewmodel.ShopUiState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShopOwnerDashboardScreen(
    viewModel: ShopOwnerViewModel = hiltViewModel(),
    onLogoutClicked: () -> Unit,
    onProfileClicked: () -> Unit = {},
    onAnalyticsClicked: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar
    LaunchedEffect(actionError) {
        actionError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shop Dashboard") },
                actions = {
                    IconButton(onClick = onAnalyticsClicked) {
                        Icon(Icons.Default.BarChart, contentDescription = "Analytics")
                    }
                    IconButton(onClick = onProfileClicked) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = onLogoutClicked) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ShopUiState.Loading -> {
                    LoadingState()
                }
                is ShopUiState.Error -> {
                    ErrorState(message = state.message)
                }
                is ShopUiState.Success -> {
                    DashboardContent(
                        shop = state.shop,
                        onNextClicked = viewModel::onNextClicked,
                        onSkipClicked = viewModel::onSkipClicked,
                        onPauseClicked = viewModel::onPauseClicked,
                        onCloseClicked = viewModel::onCloseClicked,
                        onOpenShopClicked = viewModel::onOpenShopClicked
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    shop: Shop,
    onNextClicked: () -> Unit,
    onSkipClicked: () -> Unit,
    onPauseClicked: () -> Unit,
    onCloseClicked: () -> Unit,
    onOpenShopClicked: () -> Unit
) {
    val activeTokens = shop.queue.values.count { it.status == "WAITING" || it.status == "SERVING" }
    val isClosed = shop.status == Shop.STATUS_CLOSED

    // Sort queue tokens by number for display
    val sortedQueue = shop.queue.entries
        .sortedBy { it.value.number }
        .filter { it.value.status != Token.STATUS_COMPLETED && it.value.status != Token.STATUS_CANCELLED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ─── Header: Shop Name + Status ─────────────────────────
        ShopHeader(name = shop.name, status = shop.status)

        Spacer(modifier = Modifier.height(24.dp))

        if (isClosed) {
            // ─── CLOSED: Show "Open Shop" UI ────────────────────
            ClosedShopContent(onOpenShopClicked = onOpenShopClicked)
        } else {
            // ─── OPEN / PAUSED: Show operational UI ─────────────
            NowServingCard(servingNumber = shop.currentServingNumber)

            Spacer(modifier = Modifier.height(20.dp))

            StatsRow(
                queueLength = activeTokens,
                lastNumberIssued = shop.lastNumberIssued
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Real-Time Queue List ───────────────────────────
            Text(
                text = "Queue (${sortedQueue.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            if (sortedQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No customers in queue yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = sortedQueue,
                        key = { it.key }
                    ) { (tokenId, token) ->
                        QueueItemCard(
                            token = token,
                            isCurrentlyServing = token.number == shop.currentServingNumber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Action Buttons ────────────────────────────────
            ActionButtons(
                shopStatus = shop.status,
                onNextClicked = onNextClicked,
                onSkipClicked = onSkipClicked,
                onPauseClicked = onPauseClicked,
                onCloseClicked = onCloseClicked
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Individual queue item card — shows token number, customer name, and status with color coding.
 */
@Composable
private fun QueueItemCard(token: Token, isCurrentlyServing: Boolean) {
    val statusColor = when (token.status) {
        Token.STATUS_WAITING -> QueueGreen
        Token.STATUS_SERVING -> Color(0xFF2196F3) // Blue
        Token.STATUS_GRACE_PERIOD -> QueueYellow
        Token.STATUS_COMPLETED -> Color.Gray
        Token.STATUS_CANCELLED -> QueueRed
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyServing)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Token number
                Text(
                    text = "#${token.number}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isCurrentlyServing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Customer name + NOW indicator
                Column {
                    Text(
                        text = token.userName.ifEmpty { "Customer" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    if (isCurrentlyServing) {
                        Text(
                            text = "← NOW SERVING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = token.status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun ShopHeader(name: String, status: String) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            Shop.STATUS_OPEN -> QueueGreen
            Shop.STATUS_PAUSED -> QueueYellow
            else -> QueueRed
        },
        animationSpec = tween(300),
        label = "statusColor"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Shop Dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        // Status badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = "  $status",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun NowServingCard(servingNumber: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NOW SERVING",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "#$servingNumber",
                fontSize = 96.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatsRow(queueLength: Int, lastNumberIssued: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "In Queue",
            value = "$queueLength",
            color = MaterialTheme.colorScheme.secondary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Last Issued",
            value = "#$lastNumberIssued",
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun ActionButtons(
    shopStatus: String,
    onNextClicked: () -> Unit,
    onSkipClicked: () -> Unit,
    onPauseClicked: () -> Unit,
    onCloseClicked: () -> Unit
) {
    val isPaused = shopStatus == Shop.STATUS_PAUSED

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── NEXT + SKIP: Only shown when OPEN (not PAUSED) ───
        if (!isPaused) {
            Button(
                onClick = onNextClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QueueGreen,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "  NEXT",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = onSkipClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QueueYellow.copy(alpha = 0.85f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PersonOff,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "  SKIP (15 min grace)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── PAUSE / RESUME Button ─────────────────────────
            Button(
                onClick = onPauseClicked,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QueueYellow,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = if (isPaused) " RESUME" else " PAUSE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── CLOSE Button ──────────────────────────────────
            Button(
                onClick = onCloseClicked,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QueueRed,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = " CLOSE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Shown when shop is CLOSED. Prominent "Open Shop for the Day" button.
 */
@Composable
private fun ClosedShopContent(onOpenShopClicked: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Storefront,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Shop is Closed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Text(
            text = "Open your shop to start accepting customers",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onOpenShopClicked,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = QueueGreen,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "  Open Shop",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading dashboard...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⚠️",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
