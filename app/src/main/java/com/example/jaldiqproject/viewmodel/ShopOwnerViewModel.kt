package com.example.jaldiqproject.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jaldiqproject.data.ShopRepository
import com.example.jaldiqproject.model.Shop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Shop Owner Dashboard.
 */
sealed interface ShopUiState {
    data object Loading : ShopUiState
    data class Success(val shop: Shop) : ShopUiState
    data class Error(val message: String) : ShopUiState
}

/**
 * ViewModel for the Shop Owner Dashboard.
 *
 * Receives shopId via SavedStateHandle from the navigation argument.
 * Observes the shop in real-time and provides actions for Next/Skip/Pause/Close.
 */
@HiltViewModel
class ShopOwnerViewModel @Inject constructor(
    private val repository: ShopRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Get shopId from navigation argument
    private val shopId: String = savedStateHandle.get<String>("shopId") ?: ""

    private val _uiState = MutableStateFlow<ShopUiState>(ShopUiState.Loading)
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        if (shopId.isNotEmpty()) {
            observeShop()
        } else {
            _uiState.value = ShopUiState.Error("No shop ID provided")
        }
    }

    /**
     * Start observing the shop data in real-time.
     * Uses callbackFlow → StateFlow pattern for instant UI updates.
     */
    private fun observeShop() {
        viewModelScope.launch {
            repository.observeShop(shopId).collect { result ->
                _uiState.value = result.fold(
                    onSuccess = { shop -> ShopUiState.Success(shop) },
                    onFailure = { error ->
                        ShopUiState.Error(error.message ?: "Unknown error")
                    }
                )
            }
        }
    }

    /**
     * Advance the queue: atomically increment currentServingNumber
     * and mark the served token as COMPLETED.
     */
    fun onNextClicked() {
        viewModelScope.launch {
            _actionError.value = null
            val result = repository.advanceQueue(shopId)
            result.onFailure { error ->
                _actionError.value = error.message ?: "Failed to advance queue"
            }
        }
    }

    /**
     * Mark the currently serving customer as DONE (completed + analytics).
     */
    fun onDoneClicked() {
        viewModelScope.launch {
            _actionError.value = null
            val result = repository.markDone(shopId)
            result.onFailure { error ->
                _actionError.value = error.message ?: "Failed to mark done"
            }
        }
    }

    /**
     * Mark the currently serving customer as MISSED (no analytics increment).
     */
    fun onMissedClicked() {
        viewModelScope.launch {
            _actionError.value = null
            val result = repository.markMissed(shopId)
            result.onFailure { error ->
                _actionError.value = error.message ?: "Failed to mark missed"
            }
        }
    }

    /**
     * Pull the next available waiting customer when the shop is currently idle.
     */
    fun onCallNextClicked() {
        viewModelScope.launch {
            _actionError.value = null
            val result = repository.callNextCustomer(shopId)
            result.onFailure { error ->
                _actionError.value = error.message ?: "Failed to call next customer"
            }
        }
    }

    /**
     * Toggle shop status between OPEN and PAUSED.
     */
    fun onPauseClicked() {
        viewModelScope.launch {
            _actionError.value = null
            val currentState = _uiState.value
            if (currentState is ShopUiState.Success) {
                val newStatus = if (currentState.shop.status == Shop.STATUS_PAUSED) {
                    Shop.STATUS_OPEN
                } else {
                    Shop.STATUS_PAUSED
                }
                val result = repository.updateShopStatus(shopId, newStatus)
                result.onFailure { error ->
                    _actionError.value = error.message ?: "Failed to update status"
                }
            }
        }
    }

    /**
     * Open the shop for the day — resets queue, counters, sets OPEN.
     */
    fun onOpenShopClicked() {
        viewModelScope.launch {
            _actionError.value = null
            val result = repository.openShopForDay(shopId)
            result.onFailure { error ->
                _actionError.value = error.message ?: "Failed to open shop"
            }
        }
    }

    /**
     * Close the shop — sets status to CLOSED and cancels all WAITING tokens.
     */
    fun onCloseClicked() {
        viewModelScope.launch {
            _actionError.value = null
            val result = repository.closeShop(shopId)
            result.onFailure { error ->
                _actionError.value = error.message ?: "Failed to close shop"
            }
        }
    }

    /**
     * Dismiss the action error snackbar.
     */
    fun dismissError() {
        _actionError.value = null
    }
}
