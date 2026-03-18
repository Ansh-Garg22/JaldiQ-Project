package com.example.jaldiqproject.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jaldiqproject.data.TokenWithShopInfo
import com.example.jaldiqproject.model.Token
import com.example.jaldiqproject.ui.theme.QueueGreen
import com.example.jaldiqproject.ui.theme.QueueRed
import com.example.jaldiqproject.ui.theme.QueueYellow
import com.example.jaldiqproject.viewmodel.CustomerViewModel
import com.example.jaldiqproject.viewmodel.TokenUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailsScreen(
    viewModel: CustomerViewModel,
    shopId: String,
    tokenId: String,
    onBackClicked: () -> Unit,
    onLeaveQueue: () -> Unit
) {
    val tokenState by viewModel.tokenState.collectAsState()

    // Resume observation if needed
    LaunchedEffect(shopId, tokenId) {
        viewModel.resumeTokenObservation(shopId, tokenId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Token") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = tokenState) {
                is TokenUiState.Loading, is TokenUiState.Joining -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Loading token details...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                is TokenUiState.Active -> {
                    ActiveTokenContent(
                        info = state.info,
                        onLeaveQueue = onLeaveQueue
                    )
                }

                is TokenUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⚠️ ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is TokenUiState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No active token",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTokenContent(
    info: TokenWithShopInfo,
    onLeaveQueue: () -> Unit
) {
    val isCompleted = info.token.status == com.example.jaldiqproject.model.Token.STATUS_COMPLETED
    val isCancelled = info.token.status == com.example.jaldiqproject.model.Token.STATUS_CANCELLED
    
    // State C: Currently Serving (Owner has called you)
    val isYourTurn = info.token.number == info.currentServingNumber && !isCompleted && !isCancelled
    
    // State B: Next in line (You are #1, but owner is idle and hasn't called you)
    val isNextInLine = info.token.number > info.currentServingNumber && info.peopleAhead == 0 && !isCompleted && !isCancelled
    
    // Can only leave if not already at the counter being served
    val canLeave = info.token.status == com.example.jaldiqproject.model.Token.STATUS_WAITING && !isYourTurn

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shop name
        Text(
            text = info.shopName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Token Number Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCancelled -> QueueRed
                    isYourTurn -> QueueGreen
                    isNextInLine -> QueueYellow // Indicate 'Get Ready'
                    isCompleted -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle
                    else Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "YOUR TOKEN",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "#${info.token.number}",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                
                // Active State Banners
                when {
                    isYourTurn -> Text(
                        text = "🎉 IT'S YOUR TURN!\nPlease proceed to the counter.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    isNextInLine -> Text(
                        text = "You are next!\nPlease wait to be called.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    isCompleted -> Text(
                        text = "✅ COMPLETED",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    isCancelled -> Text(
                        text = "❌ CANCELLED",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isCompleted && !isCancelled) {
            // Progress toward your turn
            val totalWait = (info.peopleAhead + 1).coerceAtLeast(1)
            val progress by animateFloatAsState(
                targetValue = if (totalWait <= 1) 1f else (1f - (info.peopleAhead.toFloat() / totalWait)),
                animationSpec = tween(500),
                label = "progress"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Progress bar
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            info.peopleAhead <= 2 -> QueueGreen
                            info.peopleAhead <= 5 -> QueueYellow
                            else -> QueueRed
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            icon = Icons.Default.Groups,
                            label = "People Ahead",
                            value = "${info.peopleAhead}"
                        )
                        StatItem(
                            icon = Icons.Default.AccessTime,
                            label = "Est. Wait",
                            value = "${info.estimatedWaitMinutes} min"
                        )
                        StatItem(
                            icon = Icons.Default.ConfirmationNumber,
                            label = "Now Serving",
                            value = "#${info.currentServingNumber}"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status badge
            val statusText = when (info.token.status) {
                Token.STATUS_WAITING -> "⏳ Waiting in Queue"
                Token.STATUS_SERVING -> "🔔 Being Served"
                Token.STATUS_GRACE_PERIOD -> "⚠️ Grace Period"
                else -> info.token.status
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This screen updates automatically.\nNo need to refresh!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )

            // ─── Leave Queue Button ──────────────────────────
            if (canLeave) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onLeaveQueue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QueueRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Leave Queue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
