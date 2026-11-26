package com.example.viewmodel

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.local.AppPreferencesRepository
import com.example.network.WorkerApiClient
import com.example.network.WorkerRealtimeClient
import com.example.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object Unauthenticated : AuthUiState
    data class Authenticated(val firebaseUser: FirebaseUser) : AuthUiState
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class AuthViewModel(
    private val userRepository: UserRepository,
    private val prefsRepository: AppPreferencesRepository,
    private val workerApiClient: WorkerApiClient,
    private val realtimeClient: WorkerRealtimeClient
) : ViewModel() {

    private val auth = Firebase.auth

    private val _authState = MutableStateFlow<AuthUiState>(
        auth.currentUser?.let { AuthUiState.Authenticated(it) } ?: AuthUiState.Unauthenticated
    )
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    @Volatile
    private var lastBootstrappedUid: String? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            _authState.value = AuthUiState.Authenticated(user)
            _isSigningIn.value = false
            _authError.value = null
            if (lastBootstrappedUid != user.uid) {
                lastBootstrappedUid = user.uid
                onUserAuthenticated(user)
            }
        } else {
            lastBootstrappedUid = null
            realtimeClient.disconnect()
            _authState.value = AuthUiState.Unauthenticated
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.let { existing ->
            if (lastBootstrappedUid != existing.uid) {
                lastBootstrappedUid = existing.uid
                onUserAuthenticated(existing)
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _authError.value = null
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val signedUser = result.user ?: auth.currentUser
                if (signedUser != null) {
                    _authState.value = AuthUiState.Authenticated(signedUser)
                    if (lastBootstrappedUid != signedUser.uid) {
                        lastBootstrappedUid = signedUser.uid
                        onUserAuthenticated(signedUser)
                    }
                } else {
                    _authError.value = "Session Firebase introuvable après authentification."
                }
            } catch (e: FirebaseAuthException) {
                Log.e(TAG, "FirebaseAuthException (${e.errorCode}): ${e.message}", e)
                _authError.value = "Erreur Firebase (${e.errorCode}) : ${e.localizedMessage ?: "Connexion refusée."}"
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Google sign-in failed", e)
                _authError.value = e.localizedMessage ?: "Échec de connexion Google."
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun setLoading(loading: Boolean) {
        _isSigningIn.value = loading
    }

    fun setAuthError(message: String?) {
        _authError.value = message
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                val fcmToken = withTimeoutOrNull(4_000L) {
                    FirebaseMessaging.getInstance().token.await()
                }
                if (!fcmToken.isNullOrBlank()) {
                    workerApiClient.unregisterDeviceToken(fcmToken)
                }
            }
            lastBootstrappedUid = null
            realtimeClient.disconnect()
            auth.signOut()
            _authState.value = AuthUiState.Unauthenticated
        }
    }

    private fun onUserAuthenticated(user: FirebaseUser) {
        viewModelScope.launch {
            val deviceId = prefsRepository.getOrCreateDeviceId()
            val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            supervisorScope {
                val configDeferred = launch {
                    val config = withTimeoutOrNull(6_000L) {
                        workerApiClient.getOrFetchConfig(forceRefresh = true)
                    }
                    if (config == null || config.features.realtimeHub) {
                        realtimeClient.connect(deviceId)
                    }
                }
                launch {
                    val profile = withTimeoutOrNull(12_000L) {
                        userRepository.ensureUserProfileExists(user, deviceId).getOrNull()
                    }
                    if (profile != null) {
                        workerApiClient.syncUserDirectoryToD1(
                            userId = profile.userId,
                            displayName = profile.displayName,
                            username = profile.username,
                            city = profile.locationCity,
                            avatarUrl = profile.avatarUrl
                        )
                    }
                    workerApiClient.recordLoginSession(
                        deviceId = deviceId,
                        deviceName = deviceName
                    )
                }
                launch {
                    runCatching {
                        val fcmToken = withTimeoutOrNull(5_000L) {
                            FirebaseMessaging.getInstance().token.await()
                        }
                        if (!fcmToken.isNullOrBlank()) {
                            workerApiClient.registerDeviceToken(fcmToken)
                        }
                    }
                }
                configDeferred.join()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    companion object {
        private const val TAG = "BravAuthVM"
    }
}
