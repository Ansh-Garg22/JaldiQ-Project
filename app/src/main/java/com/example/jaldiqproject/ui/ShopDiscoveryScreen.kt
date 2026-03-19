package com.example.jaldiqproject.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jaldiqproject.model.Shop
import com.example.jaldiqproject.ui.theme.QueueGreen
import com.example.jaldiqproject.ui.theme.QueueRed
import com.example.jaldiqproject.ui.theme.QueueYellow
import com.example.jaldiqproject.viewmodel.CustomerViewModel
import com.example.jaldiqproject.viewmodel.ShopListUiState
import com.example.jaldiqproject.viewmodel.TokenUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDiscoveryScreen(
    viewModel: CustomerViewModel,
    onLogoutClicked: () -> Unit,
    onTokenObtained: (shopId: String, tokenId: String) -> Unit,
    onViewActiveToken: (shopId: String, tokenId: String) -> Unit,
    onProfileClicked: () -> Unit = {}
) {
    val shopListState by viewModel.shopListState.collectAsState()
    val tokenState by viewModel.tokenState.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Notification threshold dialog state
    var showNotifyDialog by remember { mutableStateOf(false) }
    var pendingShopId by remember { mutableStateOf("") }
    var pendingShopName by remember { mutableStateOf("") }
    var notifySliderValue by remember { mutableFloatStateOf(2f) }

    // Navigate to token details when token is obtained
    LaunchedEffect(Unit) {
        viewModel.navigateToToken.collect { pair ->
            onTokenObtained(pair.first, pair.second)
        }
    }

    LaunchedEffect(actionError) {
        actionError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find a Shop") },
                actions = {
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
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ─── Pincode Search Bar ─────────────────────────────────
            val currentPincode by viewModel.currentPincode.collectAsState()
            var searchPincode by remember(currentPincode) { mutableStateOf(currentPincode) }

            androidx.compose.material3.OutlinedTextField(
                value = searchPincode,
                onValueChange = { searchPincode = it.take(6) },
                placeholder = { Text("Current Area Pincode") },
                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = "Area") },
                trailingIcon = {
                    if (searchPincode != currentPincode && searchPincode.length >= 4) {
                        IconButton(onClick = { viewModel.setAreaPincode(searchPincode) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // ─── Main Content Area ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = shopListState) {
                    is ShopListUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Discovering nearby shops...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    is ShopListUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚠️ ${state.message}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is ShopListUiState.Success -> {
                        val isJoining = tokenState is TokenUiState.Joining

                        if (state.shops.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No shops available right now.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.shops.entries.toList()) { (shopId, shop) ->
                                    val hasActiveToken = viewModel.hasActiveTokenInShop(shop)
                                    val activeTokenId = viewModel.findActiveTokenId(shop)

                                    ShopCard(
                                        shopId = shopId,
                                        shop = shop,
                                        isJoining = isJoining,
                                        hasActiveToken = hasActiveToken,
                                        onJoinClicked = {
                                            pendingShopId = shopId
                                            pendingShopName = shop.name
                                            notifySliderValue = 2f
                                            showNotifyDialog = true
                                        },
                                        onViewActiveToken = {
                                            if (activeTokenId != null) {
                                                onViewActiveToken(shopId, activeTokenId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Notification Threshold Dialog ─────────────────────────
    if (showNotifyDialog) {
        AlertDialog(
            onDismissRequest = { showNotifyDialog = false },
            title = {
                Text(
                    text = "When should we notify you?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Alert me when ${notifySliderValue.toInt()} ${if (notifySliderValue.toInt() == 1) "person is" else "people are"} ahead",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = notifySliderValue,
                        onValueChange = { notifySliderValue = it },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1", style = MaterialTheme.typography.labelSmall)
                        Text("2", style = MaterialTheme.typography.labelSmall)
                        Text("3", style = MaterialTheme.typography.labelSmall)
                        Text("4", style = MaterialTheme.typography.labelSmall)
                        Text("5", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotifyDialog = false
                        viewModel.joinQueue(pendingShopId, pendingShopName, notifySliderValue.toInt())
                    }
                ) {
                    Text("Join Queue", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ShopCard(
    shopId: String,
    shop: Shop,
    isJoining: Boolean,
    hasActiveToken: Boolean,
    onJoinClicked: () -> Unit,
    onViewActiveToken: () -> Unit
) {
    // Phase 11 Fix: Actively count WAITING + GRACE_PERIOD tokens instead of subtracting
    val activeQueueLength = shop.queue.values.count { 
        it.status == com.example.jaldiqproject.model.Token.STATUS_WAITING || 
        it.status == com.example.jaldiqproject.model.Token.STATUS_GRACE_PERIOD 
    }
    val isOpen = shop.status == Shop.STATUS_OPEN
    val statusColor = when (shop.status) {
        Shop.STATUS_OPEN -> QueueGreen
        Shop.STATUS_PAUSED -> QueueYellow
        else -> QueueRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Traffic Light Indicator color
            val busyColor = when {
                activeQueueLength < 5 -> QueueGreen
                activeQueueLength <= 10 -> QueueYellow
                else -> QueueRed
            }
            val busyLabel = when {
                activeQueueLength < 5 -> "Low"
                activeQueueLength <= 10 -> "Moderate"
                else -> "Busy"
            }

            // Shop name + status + traffic light
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shop.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Traffic light dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(busyColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = busyLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = busyColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = shop.status,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Queue length
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$activeQueueLength in queue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Average wait
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "~${activeQueueLength * shop.averageServiceTimeMinutes} min wait",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Join Queue or View Active Token button
            if (hasActiveToken) {
                Button(
                    onClick = onViewActiveToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QueueGreen
                    )
                ) {
                    Text(
                        text = "View Active Token",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onJoinClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isOpen && !isJoining,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isJoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Joining...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            text = if (isOpen) "Get Token & Join Queue" else "Shop ${shop.status}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
