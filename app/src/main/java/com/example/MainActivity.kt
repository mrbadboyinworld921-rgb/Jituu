package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.configureAppCheck
import com.example.security.BiometricVaultHelper
import com.example.ui.screens.JarvisHudScreen
import com.example.ui.theme.JarvisTheme
import com.example.viewmodel.JarvisViewModel

class MainActivity : FragmentActivity() {
    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before rendering Compose UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                JarvisHudScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBiometricUnlockClick = { triggerBiometricUnlock() },
                    onBiometricEnrollClick = { triggerBiometricEnrollment() }
                )
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.touchActivity()
    }

    private fun triggerBiometricUnlock() {
        val blob = viewModel.getBiometricBlob()
        if (blob == null) {
            viewModel.showLockError("Biometric key not bound. Enter 6-digit PIN.")
            return
        }
        val (iv, wrappedKey) = blob
        BiometricVaultHelper.authenticateAndUnwrapVaultKey(
            activity = this,
            iv = iv,
            wrappedKey = wrappedKey,
            onSuccess = { keyBytes -> viewModel.unlockWithBiometricBytes(keyBytes) },
            onError = { msg -> viewModel.showLockError(msg) }
        )
    }

    private fun triggerBiometricEnrollment() {
        val keyBytes = viewModel.getActiveAesKeyBytesForBiometric()
        if (keyBytes == null) {
            viewModel.onBiometricEnrollFailed("Unlock vault with PIN first.")
            return
        }
        BiometricVaultHelper.authenticateAndWrapVaultKey(
            activity = this,
            vaultKeyBytes = keyBytes,
            onSuccess = { iv, wrapped -> viewModel.onBiometricEnrolled(iv, wrapped) },
            onError = { msg -> viewModel.onBiometricEnrollFailed(msg) }
        )
    }
}
