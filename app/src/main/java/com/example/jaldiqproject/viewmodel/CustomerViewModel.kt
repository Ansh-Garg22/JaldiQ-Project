package com.example.jaldiqproject.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jaldiqproject.data.ShopRepository
import com.example.jaldiqproject.data.TokenWithShopInfo
import com.example.jaldiqproject.model.Shop
import com.example.jaldiqproject.model.Token
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Shop Discovery screen.
 */
sealed interface ShopListUiState {
    data object Loading : ShopListUiState
    data class Success(val shops: Map<String, Shop>) : ShopListUiState
    data class Error(val message: String) : ShopListUiState
}

/**
 * UI state for the Token Details screen.
 */
sealed interface TokenUiState {
    data object Idle : TokenUiState
    data object Loading : TokenUiState
    data class Joining(val shopName: String) : TokenUiState
    data class Active(val info: TokenWithShopInfo) : TokenUiState
    data class Error(val message: String) : TokenUiState
}

/**
 * ViewModel for Customer flows — shop discovery and token tracking.
 * Uses FirebaseAuth for real user ID.
 */
@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val repository: ShopRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    /** Real Firebase Auth UID — no more hardcoded IDs. */
    private val userId: String get() = auth.currentUser?.uid ?: ""
    private val userEmail: String get() = auth.currentUser?.email ?: ""

    private val _shopListState = MutableStateFlow<ShopListUiState>(ShopListUiState.Loading)
    val shopListState: StateFlow<ShopListUiState> = _shopListState.asStateFlow()

    private val _tokenState = MutableStateFlow<TokenUiState>(TokenUiState.Idle)
    val tokenState: StateFlow<TokenUiState> = _tokenState.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // One-time events for navigation
    private val _navigateToToken = MutableSharedFlow<Pair<String, String>>()
    val navigateToToken = _navigateToToken.asSharedFlow()

    private val _currentPincode = MutableStateFlow("")
    val currentPincode: StateFlow<String> = _currentPincode.asStateFlow()

    private var shopListJob: Job? = null
    private var tokenObserverJob: Job? = null

    init {
        loadDefaultPincode()
        registerFcmToken()
    }

    /**
     * Register the FCM device token for push notifications.
     */
    private fun registerFcmToken() {
        if (userId.isEmpty()) return
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            android.util.Log.d("JaldiQ-FCM", "FCM token registered for user $userId")
            repository.registerFcmToken(userId, token)
        }.addOnFailureListener { e ->
            android.util.Log.e("JaldiQ-FCM", "Failed to get FCM token", e)
        }
    }

    /**
     * Fetch the user's saved pincode and initialize the shop list for that area.
     */
    private fun loadDefaultPincode() {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            repository.getUserPincode(userId).onSuccess { pincode ->
                if (pincode.isNotBlank()) {
                    setAreaPincode(pincode)
                } else {
                    _shopListState.value = ShopListUiState.Success(emptyMap())
                }
            }
        }
    }

    /**
     * Change the currently viewed area and dynamically load its shops.
     */
    fun setAreaPincode(pincode: String) {
        _currentPincode.value = pincode
        shopListJob?.cancel()
        _shopListState.value = ShopListUiState.Loading
        shopListJob = viewModelScope.launch {
            repository.observeShopsByPincode(pincode).collect { result ->
                _shopListState.value = result.fold(
                    onSuccess = { shops -> ShopListUiState.Success(shops) },
                    onFailure = { error ->
                        ShopListUiState.Error(error.message ?: "Failed to load shops")
                    }
                )
            }
        }
    }

    /**
     * Check if the current user already has a WAITING token in a given shop.
     */
    fun hasActiveTokenInShop(shop: Shop): Boolean {
        return shop.queue.values.any {
            it.userId == userId && it.status == Token.STATUS_WAITING
        }
    }

    /**
     * Find the tokenId of the user's active token in a shop.
     */
    fun findActiveTokenId(shop: Shop): String? {
        return shop.queue.entries.firstOrNull {
            it.value.userId == userId && it.value.status == Token.STATUS_WAITING
        }?.key
    }

    /**
     * Join a shop's queue.
     * Uses Firebase transaction to atomically create a token.
     * Passes userName for shop owner display.
     */
    fun joinQueue(shopId: String, shopName: String, notifyThreshold: Int = 2) {
        viewModelScope.launch {
            _tokenState.value = TokenUiState.Joining(shopName)
            _actionError.value = null

            // Use email prefix as display name (or could be a dedicated field)
            val displayName = userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

            val result = repository.joinQueue(shopId, userId, displayName, notifyThreshold)
            result.fold(
                onSuccess = { tokenId ->
                    // Start observing the token for reactive updates
                    observeToken(shopId, tokenId)
                    // Fire one-time navigation event
                    _navigateToToken.emit(shopId to tokenId)
                },
                onFailure = { error ->
                    _tokenState.value = TokenUiState.Idle
                    _actionError.value = error.message ?: "Failed to join queue"
                }
            )
        }
    }

    /**
     * Leave the queue — cancel the customer's token.
     * Instantly reflects on the owner's dashboard via callbackFlow.
     */
    fun leaveQueue(shopId: String, tokenId: String) {
        viewModelScope.launch {
            _actionError.value = null
            val result = repository.leaveQueue(shopId, tokenId, userId)
            result.fold(
                onSuccess = {
                    tokenObserverJob?.cancel()
                    _tokenState.value = TokenUiState.Idle
                },
                onFailure = { error ->
                    _actionError.value = error.message ?: "Failed to leave queue"
                }
            )
        }
    }

    /**
     * Start observing a specific token for reactive wait-time updates.
     */
    private fun observeToken(shopId: String, tokenId: String) {
        tokenObserverJob?.cancel()
        tokenObserverJob = viewModelScope.launch {
            repository.observeTokenWithShop(shopId, tokenId).collect { result ->
                _tokenState.value = result.fold(
                    onSuccess = { info -> TokenUiState.Active(info) },
                    onFailure = { error ->
                        TokenUiState.Error(error.message ?: "Failed to track token")
                    }
                )
            }
        }
    }

    /**
     * Resume observing an existing token (e.g., after navigating back).
     */
    fun resumeTokenObservation(shopId: String, tokenId: String) {
        if (_tokenState.value !is TokenUiState.Active) {
            _tokenState.value = TokenUiState.Loading
            observeToken(shopId, tokenId)
        }
    }

    fun dismissError() {
        _actionError.value = null
    }

    fun resetToken() {
        tokenObserverJob?.cancel()
        _tokenState.value = TokenUiState.Idle
    }
}
