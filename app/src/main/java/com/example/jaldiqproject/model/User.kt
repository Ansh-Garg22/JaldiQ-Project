package com.example.jaldiqproject.model

/**
 * Represents a user in the JaldiQ system.
 * Maps directly to the /users/{uid} node in Firebase Realtime Database.
 */
data class User(
    val role: String = ROLE_CUSTOMER,
    val email: String = "",
    val displayName: String = "",
    val pincode: String = "",
    val shopId: String = "",
    val activeTokenId: String = "",
    val fcmToken: String = ""
) {
    companion object {
        const val ROLE_CUSTOMER = "CUSTOMER"
        const val ROLE_SHOP_OWNER = "SHOP_OWNER"
    }
}
