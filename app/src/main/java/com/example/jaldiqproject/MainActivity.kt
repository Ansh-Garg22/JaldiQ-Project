package com.example.jaldiqproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.jaldiqproject.data.AuthRepository
import com.example.jaldiqproject.ui.JaldiQNavHost
import com.example.jaldiqproject.ui.theme.JaldiQTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    // Register the permission request launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                android.util.Log.d("JaldiQ", "Notification permission granted")
            } else {
                android.util.Log.w("JaldiQ", "Notification permission denied by user")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission for Android 13+ (TIRAMISU)
        requestNotificationPermission()

        setContent {
            JaldiQTheme {
                JaldiQNavHost(authRepository = authRepository)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) ==
                        PackageManager.PERMISSION_GRANTED -> {
                    android.util.Log.d("JaldiQ", "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(permission) -> {
                    requestPermissionLauncher.launch(permission)
                }
                else -> {
                    requestPermissionLauncher.launch(permission)
                }
            }
        }
    }
}