package com.example.jaldiqproject.data

import com.example.jaldiqproject.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for authentication and user profile operations.
 * Uses Firebase Auth for email/password authentication and
 * Firebase Realtime Database for user profile storage.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) {
    private fun usersRef() = database.getReference("users")
    private fun userRef(uid: String) = database.getReference("users/$uid")
    private fun shopsRef() = database.getReference("shops")

    /** Current Firebase user (null if not signed in). */
    val currentUser: FirebaseUser? get() = auth.currentUser

    /**
     * Observe authentication state changes in real-time.
     * Emits the current FirebaseUser (or null) whenever auth state changes.
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Sign up with email/password and write user profile to database.
     *
     * @param email User's email
     * @param password User's password
     * @param role User.ROLE_CUSTOMER or User.ROLE_SHOP_OWNER
     * @param name User's display name
     * @return The created FirebaseUser's UID
     */
    suspend fun signUp(email: String, password: String, role: String, name: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw IllegalStateException("User creation failed")

            // Write user profile to database
            val user = User(
                role = role,
                email = email,
                displayName = name
            )
            userRef(uid).setValue(user).await()

            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign in with email/password.
     *
     * @return The FirebaseUser's UID
     */
    suspend fun signIn(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw IllegalStateException("Sign in failed")
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the user's role from the database.
     */
    suspend fun getUserRole(uid: String): Result<String> {
        return try {
            val snapshot = userRef(uid).child("role").get().await()
            val role = snapshot.getValue(String::class.java)
                ?: throw IllegalStateException("No role found for user")
            Result.success(role)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the user's shopId from the database.
     */
    suspend fun getUserShopId(uid: String): Result<String?> {
        return try {
            val snapshot = userRef(uid).child("shopId").get().await()
            val shopId = snapshot.getValue(String::class.java)
            Result.success(shopId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register a new shop for a shop owner.
     * Creates the shop in /shops/{shopId} and links it to the user.
     *
     * @return The generated shopId
     */
    suspend fun registerShop(
        ownerUid: String,
        ownerName: String,
        shopName: String,
        location: String,
        averageServiceTimeMinutes: Int
    ): Result<String> {
        return try {
            val shopId = "shop_${System.currentTimeMillis()}"

            val shopData = mapOf(
                "name" to shopName,
                "ownerUid" to ownerUid,
                "ownerName" to ownerName,
                "location" to location,
                "status" to "CLOSED",
                "averageServiceTimeMinutes" to averageServiceTimeMinutes,
                "currentServingNumber" to 0,
                "lastNumberIssued" to 0
            )

            // Create the shop
            shopsRef().child(shopId).setValue(shopData).await()

            // Link shop to user
            userRef(ownerUid).child("shopId").setValue(shopId).await()

            Result.success(shopId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the full user profile from the database.
     */
    suspend fun getUserProfile(uid: String): Result<User> {
        return try {
            val snapshot = userRef(uid).get().await()
            val user = snapshot.getValue(User::class.java)
                ?: throw IllegalStateException("User profile not found")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user profile fields.
     */
    suspend fun updateUserProfile(uid: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            userRef(uid).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update shop profile fields.
     */
    suspend fun updateShopProfile(shopId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            shopsRef().child(shopId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        auth.signOut()
    }
}
