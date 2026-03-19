package com.example.jaldiqproject.model

/**
 * Represents a shop/business in the JaldiQ system.
 * Maps directly to the /shops/{shopId} node in Firebase Realtime Database.
 */
data class Shop(
    val name: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val pincode: String = "",
    val status: String = STATUS_CLOSED,
    val currentServingNumber: Int = 0,
    val lastNumberIssued: Int = 0,
    val averageServiceTimeMinutes: Int = 15,
    val queue: Map<String, Token> = emptyMap()
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_CLOSED = "CLOSED"
    }
}
