package com.example.jaldiqproject.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jaldiqproject.data.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerProfileScreen(
    authRepository: AuthRepository,
    shopId: String,
    onBackClicked: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var ownerName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var avgServiceTime by remember { mutableStateOf("15") }
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Load user + shop profiles
    LaunchedEffect(uid, shopId) {
        val userResult = authRepository.getUserProfile(uid)
        userResult.onSuccess { user ->
            ownerName = user.displayName
            email = user.email
        }
        // Load shop data
        try {
            val shopSnap = FirebaseDatabase.getInstance()
                .getReference("shops/$shopId").get().await()
            shopName = shopSnap.child("name").getValue(String::class.java) ?: ""
            pincode = shopSnap.child("pincode").getValue(String::class.java) ?: ""
            avgServiceTime = (shopSnap.child("averageServiceTimeMinutes")
                .getValue(Int::class.java) ?: 15).toString()
        } catch (_: Exception) {}
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(
                            if (isEditing) Icons.Default.Save else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Save" else "Edit"
                        )
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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Shop Owner",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Owner Name
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { if (isEditing) ownerName = it },
                    label = { Text("Owner Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isEditing && !isSaving
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Email (read-only)
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = false
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Shop Name
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { if (isEditing) shopName = it },
                    label = { Text("Shop Name") },
                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isEditing && !isSaving
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Pincode
                OutlinedTextField(
                    value = pincode,
                    onValueChange = { if (isEditing) pincode = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("Area Pincode") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isEditing && !isSaving
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Avg Service Time
                OutlinedTextField(
                    value = avgServiceTime,
                    onValueChange = { if (isEditing) avgServiceTime = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Avg. Service Time (min)") },
                    leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isEditing && !isSaving
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Save button
                if (isEditing) {
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                // Update user profile
                                val userUpdates = mapOf<String, Any>(
                                    "displayName" to ownerName.trim(),
                                    "pincode" to pincode.trim()
                                )
                                authRepository.updateUserProfile(uid, userUpdates)

                                // Update shop profile
                                val shopUpdates = mapOf<String, Any>(
                                    "name" to shopName.trim(),
                                    "ownerName" to ownerName.trim(),
                                    "pincode" to pincode.trim(),
                                    "averageServiceTimeMinutes" to
                                            (avgServiceTime.toIntOrNull() ?: 15)
                                )
                                val result = authRepository.updateShopProfile(shopId, shopUpdates)
                                isSaving = false
                                result.fold(
                                    onSuccess = {
                                        isEditing = false
                                        snackbarHostState.showSnackbar("Profile updated!")
                                    },
                                    onFailure = { error ->
                                        snackbarHostState.showSnackbar(
                                            error.message ?: "Update failed"
                                        )
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving && ownerName.isNotBlank() && shopName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
