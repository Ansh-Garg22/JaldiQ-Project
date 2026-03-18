package com.example.jaldiqproject.model

/**
 * Represents a queue token issued to a customer.
 * Maps directly to the /shops/{shopId}/queue/{tokenId} node in Firebase.
 */
data class Token(
    val number: Int = 0,
    val userId: String = "",
    val userName: String = "",
    val status: String = STATUS_WAITING,
    val skippedAt: Long = 0,
    val notifyThreshold: Int = 2
) {
    companion object {
        const val STATUS_WAITING = "WAITING"
        const val STATUS_SERVING = "SERVING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_SKIPPED = "SKIPPED"
        const val STATUS_GRACE_PERIOD = "GRACE_PERIOD"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_MISSED = "MISSED"
    }
}
