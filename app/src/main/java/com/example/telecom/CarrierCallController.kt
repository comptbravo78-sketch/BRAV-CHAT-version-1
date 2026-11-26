package com.example.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import com.example.model.CallAudioRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CarrierCallUiState(
    val hasCall: Boolean = false,
    val number: String = "",
    val formattedNumber: String = "",
    val callerDisplayName: String = "",
    val avatarUrl: String = "",
    val isUnknownCaller: Boolean = false,
    val direction: CallDirection = CallDirection.UNKNOWN,
    val cityLabel: String = "",
    val localitySummary: String = "",
    val operatorLabel: String = "",
    val lineType: String = "",
    val flagEmoji: String = "",
    val isKnownContact: Boolean = false,
    val isVerifiedBrav: Boolean = false,
    val verifiedOrgName: String = "",
    val confidence: CallerConfidenceLevel = CallerConfidenceLevel.LOW,
    val safetyCategory: CallerSafetyCategory = CallerSafetyCategory.NONE,
    val communityLabel: String = "",
    val reportCount: Int = 0,
    val sourceLabel: String = "",
    val state: Int = Call.STATE_DISCONNECTED,
    val connectTimeMillis: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val audioRoute: CallAudioRoute = CallAudioRoute.EARPIECE,
    val availableAudioRoutes: Set<CallAudioRoute> = setOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER),
    val bluetoothDeviceName: String = ""
) {
    val isRinging: Boolean
        get() = state == Call.STATE_RINGING

    val isOutgoing: Boolean
        get() = direction == CallDirection.OUTGOING || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING

    val isActive: Boolean
        get() = state == Call.STATE_ACTIVE

    val isHolding: Boolean
        get() = state == Call.STATE_HOLDING

    val statusLabel: String
        get() = when (state) {
            Call.STATE_RINGING -> "Appel entrant"
            Call.STATE_DIALING -> "Numérotation sortante..."
            Call.STATE_CONNECTING -> "Connexion..."
            Call.STATE_ACTIVE -> "En communication"
            Call.STATE_HOLDING -> "En attente"
            Call.STATE_DISCONNECTING -> "Raccrochage..."
            else -> "Terminé"
        }
}

object CarrierCallController {
    private val _uiState = MutableStateFlow(CarrierCallUiState())
    val uiState: StateFlow<CarrierCallUiState> = _uiState.asStateFlow()

    @Volatile
    private var currentCall: Call? = null

    @Volatile
    private var serviceRef: BravInCallService? = null

    @Volatile
    private var lastScreenedIdentity: CallerIdentity? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            publish(call, state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                call.details.state
            } else {
                @Suppress("DEPRECATION")
                call.state
            }
            publish(call, state)
        }
    }

    internal fun attachService(service: BravInCallService) {
        serviceRef = service
    }

    internal fun detachService(service: BravInCallService) {
        if (serviceRef === service) {
            serviceRef = null
        }
    }

    internal fun onCallScreened(identity: CallerIdentity) {
        lastScreenedIdentity = identity
        val cur = _uiState.value
        if (cur.hasCall && (cur.number.isBlank() || identity.rawNumber == cur.number)) {
            _uiState.value = cur.copy(
                formattedNumber = identity.formattedNumber,
                callerDisplayName = cur.callerDisplayName.ifBlank { identity.displayName },
                avatarUrl = cur.avatarUrl.ifBlank { identity.avatarUrl },
                isUnknownCaller = cur.isUnknownCaller && identity.isUnknownCaller,
                direction = if (cur.direction == CallDirection.UNKNOWN) identity.direction else cur.direction,
                cityLabel = identity.cityBadge,
                localitySummary = identity.fullLocationSummary,
                operatorLabel = identity.locality.operatorHint,
                lineType = identity.locality.lineType,
                flagEmoji = identity.locality.flagEmoji,
                isKnownContact = identity.isKnownContact,
                isVerifiedBrav = identity.isVerifiedBrav,
                verifiedOrgName = identity.verifiedOrgName,
                confidence = identity.confidence,
                safetyCategory = identity.safetyCategory,
                communityLabel = identity.communityLabel,
                reportCount = identity.reportCount,
                sourceLabel = identity.sourceLabel
            )
        }
    }

    internal fun onCallAdded(call: Call) {
        currentCall?.unregisterCallback(callback)
        currentCall = call
        call.registerCallback(callback)
        val initialState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
        publish(call, initialState)
    }

    internal fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        if (currentCall === call) {
            currentCall = null
            lastScreenedIdentity = null
            _uiState.value = CarrierCallUiState()
        }
    }

    internal fun onAudioStateChanged(
        isMuted: Boolean,
        route: Int,
        supportedRouteMask: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
        bluetoothName: String = ""
    ) {
        val currentRoute = when {
            route and CallAudioState.ROUTE_BLUETOOTH != 0 -> CallAudioRoute.BLUETOOTH
            route and CallAudioState.ROUTE_SPEAKER != 0 -> CallAudioRoute.SPEAKER
            else -> CallAudioRoute.EARPIECE
        }
        val available = linkedSetOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER)
        if ((supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0 || currentRoute == CallAudioRoute.BLUETOOTH) {
            available.add(CallAudioRoute.BLUETOOTH)
        }
        _uiState.value = _uiState.value.copy(
            isMuted = isMuted,
            isSpeakerOn = currentRoute == CallAudioRoute.SPEAKER,
            audioRoute = currentRoute,
            availableAudioRoutes = available,
            bluetoothDeviceName = bluetoothName
        )
    }

    fun answer() {
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun rejectOrHangup() {
        val call = currentCall ?: return
        val state = _uiState.value.state
        if (state == Call.STATE_RINGING) {
            call.reject(false, null)
        } else {
            call.disconnect()
        }
    }

    fun toggleHold() {
        val call = currentCall ?: return
        if (_uiState.value.isHolding) call.unhold() else call.hold()
    }

    fun toggleMute() {
        val next = !_uiState.value.isMuted
        serviceRef?.setMuted(next)
        _uiState.value = _uiState.value.copy(isMuted = next)
    }

    fun toggleSpeaker() {
        val nextRoute = if (_uiState.value.audioRoute == CallAudioRoute.SPEAKER) {
            if (CallAudioRoute.BLUETOOTH in _uiState.value.availableAudioRoutes) CallAudioRoute.BLUETOOTH
            else CallAudioRoute.EARPIECE
        } else {
            CallAudioRoute.SPEAKER
        }
        selectAudioRoute(nextRoute)
    }

    fun selectAudioRoute(route: CallAudioRoute) {
        val telecomRoute = when (route) {
            CallAudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallAudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            CallAudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_WIRED_HEADSET
        }
        @Suppress("DEPRECATION")
        serviceRef?.setAudioRoute(telecomRoute)
        _uiState.value = _uiState.value.copy(
            audioRoute = route,
            isSpeakerOn = route == CallAudioRoute.SPEAKER
        )
    }

    fun playDtmf(digit: Char) {
        val call = currentCall ?: return
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }

    private fun publish(call: Call, state: Int) {
        if (state == Call.STATE_DISCONNECTED) {
            _uiState.value = CarrierCallUiState()
            return
        }
        val details = call.details
        val handleNumber = details?.handle?.schemeSpecificPart.orEmpty()
        val telecomDisplayName = details?.callerDisplayName.orEmpty().ifBlank {
            details?.contactDisplayName.orEmpty()
        }
        val direction = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                details?.callDirection == Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                details?.callDirection == Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
            state == Call.STATE_RINGING -> CallDirection.INCOMING
            state == Call.STATE_DIALING || state == Call.STATE_CONNECTING -> CallDirection.OUTGOING
            else -> _uiState.value.direction
        }

        val ctx = serviceRef
        val identity = if (ctx != null && handleNumber.isNotBlank()) {
            CallerIdResolver.resolveWithSystemContacts(
                context = ctx,
                rawNumber = handleNumber,
                telecomDisplayName = telecomDisplayName,
                direction = direction
            )
        } else {
            CallerIdResolver.resolveFromKnownData(
                rawNumber = handleNumber,
                telecomDisplayName = telecomDisplayName,
                direction = direction
            )
        }

        val screened = lastScreenedIdentity?.takeIf {
            it.rawNumber == handleNumber || it.normalizedNumber == identity.normalizedNumber
        }
        val connectTime = details?.connectTimeMillis ?: 0L
        val current = _uiState.value
        _uiState.value = current.copy(
            hasCall = true,
            number = handleNumber,
            formattedNumber = identity.formattedNumber.ifBlank { handleNumber },
            callerDisplayName = if (identity.isKnownContact) identity.displayName
            else screened?.takeIf { it.isKnownContact }?.displayName ?: telecomDisplayName.ifBlank { identity.displayName },
            avatarUrl = identity.avatarUrl.ifBlank { screened?.avatarUrl.orEmpty() },
            isUnknownCaller = identity.isUnknownCaller && (screened?.isUnknownCaller ?: true) && telecomDisplayName.isBlank(),
            direction = direction,
            cityLabel = identity.cityBadge.ifBlank { screened?.cityBadge.orEmpty() },
            localitySummary = identity.fullLocationSummary.ifBlank { screened?.fullLocationSummary.orEmpty() },
            operatorLabel = identity.locality.operatorHint.ifBlank { screened?.locality?.operatorHint.orEmpty() },
            lineType = identity.locality.lineType.ifBlank { screened?.locality?.lineType.orEmpty() },
            flagEmoji = identity.locality.flagEmoji.ifBlank { screened?.locality?.flagEmoji.orEmpty() },
            isKnownContact = identity.isKnownContact || (screened?.isKnownContact == true),
            isVerifiedBrav = identity.isVerifiedBrav || (screened?.isVerifiedBrav == true),
            verifiedOrgName = identity.verifiedOrgName.ifBlank { screened?.verifiedOrgName.orEmpty() },
            confidence = maxOf(identity.confidence, screened?.confidence ?: identity.confidence),
            safetyCategory = if (identity.safetyCategory != CallerSafetyCategory.NONE) identity.safetyCategory
            else screened?.safetyCategory ?: CallerSafetyCategory.NONE,
            communityLabel = identity.communityLabel.ifBlank { screened?.communityLabel.orEmpty() },
            reportCount = maxOf(identity.reportCount, screened?.reportCount ?: 0),
            sourceLabel = identity.sourceLabel.ifBlank { screened?.sourceLabel.orEmpty() },
            state = state,
            connectTimeMillis = connectTime
        )
    }
}
