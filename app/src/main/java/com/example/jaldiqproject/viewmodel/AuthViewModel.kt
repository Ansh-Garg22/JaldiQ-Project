package com.example.jaldiqproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jaldiqproject.data.AuthRepository
import com.example.jaldiqproject.model.User
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Auth screen.
 */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Authenticated(
        val uid: String,
        val role: String,
        val shopId: String? = null
    ) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

/**
 * UI state for Shop Registration.
 */
sealed interface ShopRegState {
    data object Idle : ShopRegState
    data object Loading : ShopRegState
    data class Success(val shopId: String) : ShopRegState
    data class Error(val message: String) : ShopRegState
}

/**
 * ViewModel for authentication and shop onboarding.
 * Manages sign up, sign in, role detection, and shop registration.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _shopRegState = MutableStateFlow<ShopRegState>(ShopRegState.Idle)
    val shopRegState: StateFlow<ShopRegState> = _shopRegState.asStateFlow()

    init {
        checkExistingSession()
    }

    /**
     * Check if the user is already logged in (e.g., app restart).
     * If yes, load their role and navigate accordingly.
     */
    private fun checkExistingSession() {
        val currentUser: FirebaseUser? = authRepository.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                _authState.value = AuthUiState.Loading
                loadUserProfile(currentUser.uid)
            }
        }
    }

    /**
     * Sign up a new user with email, password, role, name, and pincode.
     * If they are a shop owner, also captures shopName and avg service time to create the shop atomicaly.
     */
    fun signUp(
        email: String,
        password: String,
        role: String,
        name: String,
        pincode: String,
        shopName: String? = null,
        averageServiceTimeMinutes: Int? = null
    ) {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading

            val result = authRepository.signUp(
                email = email,
                password = password,
                role = role,
                name = name,
                pincode = pincode,
                shopName = shopName,
                averageServiceTimeMinutes = averageServiceTimeMinutes
            )
            result.fold(
                onSuccess = { uid -> loadUserProfile(uid) },
                onFailure = { error ->
                    _authState.value = AuthUiState.Error(
                        error.message ?: "Sign up failed"
                    )
                }
            )
        }
    }

    /**
     * Sign in an existing user.
     */
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading

            val result = authRepository.signIn(email, password)
            result.fold(
                onSuccess = { uid -> loadUserProfile(uid) },
                onFailure = { error ->
                    _authState.value = AuthUiState.Error(
                        error.message ?: "Sign in failed"
                    )
                }
            )
        }
    }

    /**
     * Load user profile (role + shopId) after successful auth.
     */
    private suspend fun loadUserProfile(uid: String) {
        val roleResult = authRepository.getUserRole(uid)
        roleResult.fold(
            onSuccess = { role ->
                var shopId: String? = null
                if (role == User.ROLE_SHOP_OWNER) {
                    val shopResult = authRepository.getUserShopId(uid)
                    shopResult.onSuccess { id -> shopId = id }
                }
                _authState.value = AuthUiState.Authenticated(
                    uid = uid,
                    role = role,
                    shopId = shopId
                )
            },
            onFailure = { error ->
                _authState.value = AuthUiState.Error(
                    error.message ?: "Failed to load profile"
                )
            }
        )
    }


    /**
     * Sign out the current user.
     */
    fun signOut() {
        authRepository.signOut()
        _authState.value = AuthUiState.Idle
        _shopRegState.value = ShopRegState.Idle
    }

    /**
     * Clear error state back to idle.
     */
    fun clearError() {
        _authState.value = AuthUiState.Idle
    }
}
