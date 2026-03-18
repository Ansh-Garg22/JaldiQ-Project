package com.example.jaldiqproject.data

import com.example.jaldiqproject.model.Shop
import com.example.jaldiqproject.model.Token
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Repository for shop-related data operations against Firebase Realtime Database.
 */
@Singleton
class ShopRepository @Inject constructor(
    private val database: FirebaseDatabase
) {
    companion object {
        const val SHOPS_REF = "shops"
        const val USERS_REF = "users"
        const val QUEUE_REF = "queue"
        const val ANALYTICS_REF = "analytics"
    }

    private fun analyticsRef(shopId: String): com.google.firebase.database.DatabaseReference =
        database.getReference("$ANALYTICS_REF/$shopId")

    fun shopsRef() = database.getReference(SHOPS_REF)
    fun shopRef(shopId: String) = database.getReference("$SHOPS_REF/$shopId")
    fun usersRef() = database.getReference(USERS_REF)
    fun userRef(userId: String) = database.getReference("$USERS_REF/$userId")

    /**
     * Register the FCM device token for a user so the backend can send push notifications.
     */
    fun registerFcmToken(userId: String, fcmToken: String) {
        userRef(userId).child("fcmToken").setValue(fcmToken)
    }

    // ─── Phase 2: Shop Owner Operations ──────────────────────────

    /**
     * Observe a shop in real-time via callbackFlow.
     */
    fun observeShop(shopId: String): Flow<Result<Shop>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val shop = parseShopSnapshot(snapshot)
                    trySend(Result.success(shop))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }

        val ref = shopRef(shopId)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Advance the queue using Firebase runTransaction.
     * Atomically increments currentServingNumber and marks served token COMPLETED.
     */
    suspend fun advanceQueue(shopId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            shopRef(shopId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentServing =
                        currentData.child("currentServingNumber").getValue(Int::class.java) ?: 0
                    val status =
                        currentData.child("status").getValue(String::class.java) ?: "CLOSED"

                    if (status == Shop.STATUS_CLOSED) {
                        return Transaction.abort()
                    }

                    // ── Smart Next: find lowest WAITING/GRACE_PERIOD token > currentServing ──
                    val queueData = currentData.child("queue")
                    val activeStatuses = setOf(Token.STATUS_WAITING, Token.STATUS_GRACE_PERIOD)
                    var nextTokenNumber: Int? = null

                    for (tokenSnapshot in queueData.children) {
                        val tokenNumber =
                            tokenSnapshot.child("number").getValue(Int::class.java) ?: 0
                        val tokenStatus =
                            tokenSnapshot.child("status").getValue(String::class.java) ?: ""

                        if (tokenStatus in activeStatuses && tokenNumber > currentServing) {
                            if (nextTokenNumber == null || tokenNumber < nextTokenNumber) {
                                nextTokenNumber = tokenNumber
                            }
                        }
                    }

                    // Fallback: no valid token ahead → queue is empty
                    if (nextTokenNumber == null) {
                        return Transaction.abort()
                    }

                    // Mark the currently-being-served token as COMPLETED
                    for (tokenSnapshot in queueData.children) {
                        val tokenNumber =
                            tokenSnapshot.child("number").getValue(Int::class.java) ?: 0
                        if (tokenNumber == currentServing) {
                            tokenSnapshot.child("status").value = Token.STATUS_COMPLETED
                            break
                        }
                    }

                    // Jump to the next valid token
                    currentData.child("currentServingNumber").value = nextTokenNumber

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (continuation.isActive) {
                        when {
                            error != null -> continuation.resume(Result.failure(error.toException()))
                            !committed -> continuation.resume(
                                Result.failure(IllegalStateException("Queue empty or shop closed"))
                            )
                            else -> {
                                // Increment daily analytics counter
                                incrementDailyServedCount(shopId)
                                continuation.resume(Result.success(Unit))
                            }
                        }
                    }
                }
            })
        }

    /**
     * Mark the currently serving customer as DONE (COMPLETED).
     * Increments the analytics counter and auto-skips to the next WAITING token.
     */
    suspend fun markDone(shopId: String): Result<Unit> =
        completeCurrentToken(shopId, Token.STATUS_COMPLETED, incrementAnalytics = true)

    /**
     * Mark the currently serving customer as MISSED (no-show).
     * Does NOT increment analytics. Auto-skips to the next WAITING token.
     */
    suspend fun markMissed(shopId: String): Result<Unit> =
        completeCurrentToken(shopId, Token.STATUS_MISSED, incrementAnalytics = false)

    /**
     * Pulls the next available WAITING customer from the queue.
     * Used when the owner is idle (currentServingNumber = 0 or pointing to completed person)
     * but the queue is not empty.
     */
    suspend fun callNextCustomer(shopId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            shopRef(shopId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val status = currentData.child("status").getValue(String::class.java) ?: "CLOSED"
                    if (status == Shop.STATUS_CLOSED) return Transaction.abort()

                    val queueData = currentData.child("queue")
                    val currentServing = currentData.child("currentServingNumber").getValue(Int::class.java) ?: 0

                    val activeStatuses = setOf(Token.STATUS_WAITING, Token.STATUS_GRACE_PERIOD)
                    var nextTokenNumber: Int? = null

                    for (tokenSnapshot in queueData.children) {
                        val tokenNumber = tokenSnapshot.child("number").getValue(Int::class.java) ?: 0
                        val tokenStatus = tokenSnapshot.child("status").getValue(String::class.java) ?: ""

                        if (tokenStatus in activeStatuses && (currentServing == 0 || tokenNumber > currentServing)) {
                            if (nextTokenNumber == null || tokenNumber < nextTokenNumber) {
                                nextTokenNumber = tokenNumber
                            }
                        }
                    }

                    if (nextTokenNumber != null) {
                        currentData.child("currentServingNumber").value = nextTokenNumber
                    }

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (continuation.isActive) {
                        when {
                            error != null -> continuation.resume(Result.failure(error.toException()))
                            !committed -> continuation.resume(
                                Result.failure(IllegalStateException("Shop closed"))
                            )
                            else -> continuation.resume(Result.success(Unit))
                        }
                    }
                }
            })
        }

    /**
     * Shared transaction logic for Done/Missed.
     * Marks the current token with [newStatus] and finds the next WAITING token.
     */
    private suspend fun completeCurrentToken(
        shopId: String,
        newStatus: String,
        incrementAnalytics: Boolean
    ): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            shopRef(shopId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentServing =
                        currentData.child("currentServingNumber").getValue(Int::class.java) ?: 0
                    val status =
                        currentData.child("status").getValue(String::class.java) ?: "CLOSED"

                    if (status == Shop.STATUS_CLOSED) return Transaction.abort()

                    val queueData = currentData.child("queue")

                    // Mark the currently-serving token with the new status
                    for (tokenSnapshot in queueData.children) {
                        val tokenNumber =
                            tokenSnapshot.child("number").getValue(Int::class.java) ?: 0
                        if (tokenNumber == currentServing) {
                            tokenSnapshot.child("status").value = newStatus
                            break
                        }
                    }

                    // Auto-skip: find the next WAITING token > currentServing
                    val activeStatuses = setOf(Token.STATUS_WAITING, Token.STATUS_GRACE_PERIOD)
                    var nextTokenNumber: Int? = null

                    for (tokenSnapshot in queueData.children) {
                        val tokenNumber =
                            tokenSnapshot.child("number").getValue(Int::class.java) ?: 0
                        val tokenStatus =
                            tokenSnapshot.child("status").getValue(String::class.java) ?: ""

                        if (tokenStatus in activeStatuses && tokenNumber > currentServing) {
                            if (nextTokenNumber == null || tokenNumber < nextTokenNumber) {
                                nextTokenNumber = tokenNumber
                            }
                        }
                    }

                    // Update currentServingNumber (0 if queue is empty)
                    currentData.child("currentServingNumber").value = nextTokenNumber ?: 0

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (continuation.isActive) {
                        when {
                            error != null -> continuation.resume(Result.failure(error.toException()))
                            !committed -> continuation.resume(
                                Result.failure(IllegalStateException("Shop closed"))
                            )
                            else -> {
                                if (incrementAnalytics) {
                                    incrementDailyServedCount(shopId)
                                }
                                continuation.resume(Result.success(Unit))
                            }
                        }
                    }
                }
            })
        }

    /**
     * Increment /analytics/{shopId}/{YYYY-MM-DD}/customersServed by 1.
     * Called after a token is successfully marked COMPLETED.
     */
    private fun incrementDailyServedCount(shopId: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val counterRef = analyticsRef(shopId).child(today).child("customersServed")
        counterRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(Int::class.java) ?: 0
                currentData.value = current + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                // Fire and forget — logging only
                if (error != null) {
                    android.util.Log.e("ShopRepo", "Analytics increment failed", error.toException())
                }
            }
        })
    }

    /**
     * Observe today's customersServed count in real-time for the dashboard card.
     */
    fun observeTodayAnalytics(shopId: String): Flow<Int> = callbackFlow {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val ref = analyticsRef(shopId).child(today).child("customersServed")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.getValue(Int::class.java) ?: 0
                trySend(count)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observe customersServed count for a specific date in real-time.
     * Used by the standalone AnalyticsViewModel with a DatePicker.
     */
    fun observeAnalyticsForDate(shopId: String, date: String): Flow<Int> = callbackFlow {
        val ref = analyticsRef(shopId).child(date).child("customersServed")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.getValue(Int::class.java) ?: 0
                trySend(count)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Update shop status (OPEN, PAUSED, CLOSED).
     */
    suspend fun updateShopStatus(shopId: String, status: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            shopRef(shopId).child("status").setValue(status)
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
        }

    /**
     * Open shop for the day:
     * - Resets queue to empty
     * - Resets currentServingNumber and lastNumberIssued to 0
     * - Sets status to OPEN
     */
    suspend fun openShopForDay(shopId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val updates = mapOf<String, Any?>(
                "status" to Shop.STATUS_OPEN,
                "currentServingNumber" to 0,
                "lastNumberIssued" to 0,
                "queue" to null // Deletes the queue node
            )
            shopRef(shopId).updateChildren(updates)
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
        }

    /**
     * Close shop for the day:
     * - Sets status to CLOSED
     * - Cancels all WAITING tokens so customers are notified
     */
    suspend fun closeShop(shopId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            shopRef(shopId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    currentData.child("status").value = Shop.STATUS_CLOSED

                    // Cancel all WAITING tokens
                    val queueData = currentData.child("queue")
                    for (tokenSnapshot in queueData.children) {
                        val tokenStatus =
                            tokenSnapshot.child("status").getValue(String::class.java) ?: ""
                        if (tokenStatus == Token.STATUS_WAITING) {
                            tokenSnapshot.child("status").value = Token.STATUS_CANCELLED
                        }
                    }

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (continuation.isActive) {
                        when {
                            error != null -> continuation.resume(Result.failure(error.toException()))
                            !committed -> continuation.resume(
                                Result.failure(IllegalStateException("Failed to close shop"))
                            )
                            else -> continuation.resume(Result.success(Unit))
                        }
                    }
                }
            })
        }

    // ─── Phase 4: Skip / Grace Period ─────────────────────────────

    /**
     * Skip the current token — sets it to GRACE_PERIOD with a timestamp.
     * The queue advances to the next person. The skipped user has 15 minutes to return.
     *
     * Atomically:
     * 1. Finds the token matching currentServingNumber
     * 2. Sets its status to GRACE_PERIOD and records skippedAt timestamp
     * 3. Increments currentServingNumber to move to next person
     */
    suspend fun skipToken(shopId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            shopRef(shopId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentServing =
                        currentData.child("currentServingNumber").getValue(Int::class.java) ?: 0
                    val status =
                        currentData.child("status").getValue(String::class.java) ?: "CLOSED"

                    if (status == Shop.STATUS_CLOSED) {
                        return Transaction.abort()
                    }

                    // Find and mark the current token as GRACE_PERIOD
                    val queueData = currentData.child("queue")
                    for (tokenSnapshot in queueData.children) {
                        val tokenNumber =
                            tokenSnapshot.child("number").getValue(Int::class.java) ?: 0
                        if (tokenNumber == currentServing) {
                            tokenSnapshot.child("status").value = Token.STATUS_GRACE_PERIOD
                            tokenSnapshot.child("skippedAt").value = System.currentTimeMillis()
                            break
                        }
                    }

                    // ── Smart Next: find lowest WAITING token > currentServing ──
                    var nextTokenNumber: Int? = null
                    for (tokenSnapshot in queueData.children) {
                        val tokenNumber =
                            tokenSnapshot.child("number").getValue(Int::class.java) ?: 0
                        val tokenStatus =
                            tokenSnapshot.child("status").getValue(String::class.java) ?: ""

                        if (tokenStatus == Token.STATUS_WAITING && tokenNumber > currentServing) {
                            if (nextTokenNumber == null || tokenNumber < nextTokenNumber) {
                                nextTokenNumber = tokenNumber
                            }
                        }
                    }

                    if (nextTokenNumber == null) {
                        return Transaction.abort()
                    }

                    currentData.child("currentServingNumber").value = nextTokenNumber

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (continuation.isActive) {
                        when {
                            error != null -> continuation.resume(Result.failure(error.toException()))
                            !committed -> continuation.resume(
                                Result.failure(IllegalStateException("Queue empty or shop closed"))
                            )
                            else -> continuation.resume(Result.success(Unit))
                        }
                    }
                }
            })
        }

    // ─── Phase 3: Customer Operations ────────────────────────────

    /**
     * Observe all shops in real-time for the discovery screen.
     */
    fun observeAllShops(): Flow<Result<Map<String, Shop>>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val shops = mutableMapOf<String, Shop>()
                    snapshot.children.forEach { shopSnap ->
                        val shopId = shopSnap.key ?: return@forEach
                        shops[shopId] = parseShopSnapshot(shopSnap)
                    }
                    trySend(Result.success(shops))
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
            }
        }

        val ref = shopsRef()
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Join a shop's queue using Firebase runTransaction.
     *
     * Atomically:
     * 1. Reads lastNumberIssued, increments by 1
     * 2. Creates a new token in the queue with status WAITING
     * 3. Updates the user's activeTokenId
     *
     * Returns the generated tokenId on success.
     */
    suspend fun joinQueue(shopId: String, userId: String, userName: String = "", notifyThreshold: Int = 2): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val tokenId = "token_${System.currentTimeMillis()}"

            shopRef(shopId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val status =
                        currentData.child("status").getValue(String::class.java) ?: "CLOSED"
                    if (status != Shop.STATUS_OPEN) {
                        return Transaction.abort()
                    }

                    // ── Obj 2: Check if this user already has a WAITING token ──
                    val queueData = currentData.child("queue")
                    for (existingToken in queueData.children) {
                        val existingUserId = existingToken.child("userId").getValue(String::class.java) ?: ""
                        val existingStatus = existingToken.child("status").getValue(String::class.java) ?: ""
                        if (existingUserId == userId && existingStatus == Token.STATUS_WAITING) {
                            return Transaction.abort() // User already in queue
                        }
                    }

                    val lastIssued =
                        currentData.child("lastNumberIssued").getValue(Int::class.java) ?: 0
                    val newNumber = lastIssued + 1

                    // Increment lastNumberIssued
                    currentData.child("lastNumberIssued").value = newNumber

                    // Create new token in queue
                    val tokenData = currentData.child("queue").child(tokenId)
                    tokenData.child("number").value = newNumber
                    tokenData.child("userId").value = userId
                    tokenData.child("userName").value = userName
                    tokenData.child("status").value = Token.STATUS_WAITING
                    tokenData.child("notifyThreshold").value = notifyThreshold

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (continuation.isActive) {
                        when {
                            error != null -> continuation.resume(Result.failure(error.toException()))
                            !committed -> continuation.resume(
                                Result.failure(IllegalStateException("Shop is closed"))
                            )
                            else -> {
                                // Also update user's activeTokenId (non-transactional, OK here)
                                userRef(userId).child("activeTokenId").setValue(tokenId)
                                userRef(userId).child("activeShopId").setValue(shopId)
                                userRef(userId).child("role").setValue("CUSTOMER")
                                continuation.resume(Result.success(tokenId))
                            }
                        }
                    }
                }
            })
        }

    /**
     * Observe a specific token and its parent shop for reactive wait-time calculation.
     * Emits a TokenWithShopInfo every time the shop or token data changes.
     */
    fun observeTokenWithShop(shopId: String, tokenId: String): Flow<Result<TokenWithShopInfo>> =
        callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val shop = parseShopSnapshot(snapshot)
                        val token = shop.queue[tokenId]
                        if (token != null) {
                            // ── Dynamic "People Ahead" ──────────────────
                            // Count only tokens that are:
                            //   (A) WAITING or GRACE_PERIOD (not COMPLETED/CANCELLED)
                            //   (B) Have a number strictly LESS than this user's token
                            // This handles mid-queue cancellations and avoids
                            // counting the user's own token.
                            val activeStatuses = setOf(
                                Token.STATUS_WAITING,
                                Token.STATUS_GRACE_PERIOD
                            )
                            val peopleAhead = shop.queue.values.count { other ->
                                other.status in activeStatuses &&
                                        other.number < token.number
                            }
                            val estimatedWaitMinutes =
                                peopleAhead * shop.averageServiceTimeMinutes

                            trySend(
                                Result.success(
                                    TokenWithShopInfo(
                                        tokenId = tokenId,
                                        shopId = shopId,
                                        token = token,
                                        shopName = shop.name,
                                        shopStatus = shop.status,
                                        currentServingNumber = shop.currentServingNumber,
                                        peopleAhead = peopleAhead,
                                        estimatedWaitMinutes = estimatedWaitMinutes
                                    )
                                )
                            )
                        } else {
                            // Token may have been removed or completed
                            trySend(
                                Result.success(
                                    TokenWithShopInfo(
                                        tokenId = tokenId,
                                        shopId = shopId,
                                        token = Token(status = Token.STATUS_COMPLETED),
                                        shopName = shop.name,
                                        shopStatus = shop.status,
                                        currentServingNumber = shop.currentServingNumber,
                                        peopleAhead = 0,
                                        estimatedWaitMinutes = 0
                                    )
                                )
                            )
                        }
                    } catch (e: Exception) {
                        trySend(Result.failure(e))
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(Result.failure(error.toException()))
                }
            }

            val ref = shopRef(shopId)
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }

    /**
     * Leave queue — cancel the customer's token.
     * Sets token status to CANCELLED and clears the user's activeTokenId.
     */
    suspend fun leaveQueue(shopId: String, tokenId: String, userId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            shopRef(shopId).child("queue").child(tokenId).child("status")
                .setValue(Token.STATUS_CANCELLED)
                .addOnSuccessListener {
                    // Clear user's active token
                    userRef(userId).child("activeTokenId").setValue("")
                    userRef(userId).child("activeShopId").setValue("")
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
        }

    /**
     * Parse a DataSnapshot into a Shop object.
     */
    private fun parseShopSnapshot(snapshot: DataSnapshot): Shop {
        val queue = mutableMapOf<String, Token>()
        snapshot.child("queue").children.forEach { tokenSnap ->
            val tokenId = tokenSnap.key ?: return@forEach
            val token = Token(
                number = tokenSnap.child("number").getValue(Int::class.java) ?: 0,
                userId = tokenSnap.child("userId").getValue(String::class.java) ?: "",
                userName = tokenSnap.child("userName").getValue(String::class.java) ?: "",
                status = tokenSnap.child("status").getValue(String::class.java)
                    ?: Token.STATUS_WAITING,
                skippedAt = tokenSnap.child("skippedAt").getValue(Long::class.java) ?: 0
            )
            queue[tokenId] = token
        }

        return Shop(
            name = snapshot.child("name").getValue(String::class.java) ?: "",
            ownerUid = snapshot.child("ownerUid").getValue(String::class.java) ?: "",
            location = snapshot.child("location").getValue(String::class.java) ?: "",
            status = snapshot.child("status").getValue(String::class.java) ?: "CLOSED",
            currentServingNumber = snapshot.child("currentServingNumber").getValue(Int::class.java)
                ?: 0,
            lastNumberIssued = snapshot.child("lastNumberIssued").getValue(Int::class.java) ?: 0,
            averageServiceTimeMinutes = snapshot.child("averageServiceTimeMinutes")
                .getValue(Int::class.java) ?: 15,
            queue = queue
        )
    }
}

/**
 * Combined token + shop info for the Token Details screen.
 */
data class TokenWithShopInfo(
    val tokenId: String,
    val shopId: String,
    val token: Token,
    val shopName: String,
    val shopStatus: String,
    val currentServingNumber: Int,
    val peopleAhead: Int,
    val estimatedWaitMinutes: Int
)
