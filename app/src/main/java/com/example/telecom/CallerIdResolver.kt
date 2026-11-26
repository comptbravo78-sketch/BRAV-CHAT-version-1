package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.model.CallerDirectoryEntry
import com.example.model.CallerReport
import com.example.model.CallerReportCategory
import com.example.model.Contact
import com.example.model.User
import com.example.util.PhoneLocalityInfo
import com.example.util.PhoneLocalityResolver
import com.example.util.PhoneUtils
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

enum class CallDirection(val labelFr: String) {
    INCOMING("Appel entrant"),
    OUTGOING("Appel sortant"),
    UNKNOWN("Appel téléphonique")
}

enum class CallerConfidenceLevel(val labelFr: String) {
    HIGH("Fiabilité élevée"),
    MEDIUM("Fiabilité moyenne"),
    LOW("Indicatif régional")
}

enum class CallerSafetyCategory(val labelFr: String, val isWarning: Boolean) {
    NONE("", false),
    BUSINESS("Entreprise identifiée", false),
    SAFE("Numéro sûr", false),
    TELEMARKETING("Démarchage", true),
    SPAM("Spam signalé", true),
    SCAM("Fraude signalée", true)
}

data class CallerIdentity(
    val rawNumber: String,
    val normalizedNumber: String,
    val formattedNumber: String,
    val displayName: String,
    val direction: CallDirection,
    val locality: PhoneLocalityInfo,
    val isKnownContact: Boolean = false,
    val isBravUser: Boolean = false,
    val bravUserId: String = "",
    val avatarUrl: String = "",
    val isVerifiedBrav: Boolean = false,
    val verifiedOrgName: String = "",
    val confidence: CallerConfidenceLevel = CallerConfidenceLevel.LOW,
    val safetyCategory: CallerSafetyCategory = CallerSafetyCategory.NONE,
    val communityLabel: String = "",
    val reportCount: Int = 0,
    val sourceLabel: String = "",
    val isUnknownCaller: Boolean = false
) {
    val cityBadge: String
        get() = locality.fixedCityLabel.ifBlank { locality.countryName }

    val fullLocationSummary: String
        get() = locality.compactBadge
}

private data class RuntimeCallerIdSnapshot(
    val users: List<User> = emptyList(),
    val approvedReports: List<CallerReport> = emptyList(),
    val callerDirectory: List<CallerDirectoryEntry> = emptyList(),
    val callerIdEnabled: Boolean = true,
    val spamWarningsEnabled: Boolean = true
)

object CallerIdResolver {

    private val runtimeSnapshot = AtomicReference(RuntimeCallerIdSnapshot())

    fun updateRuntimeDirectory(
        users: List<User>,
        approvedReports: List<CallerReport>,
        callerIdEnabled: Boolean,
        spamWarningsEnabled: Boolean,
        callerDirectory: List<CallerDirectoryEntry> = runtimeSnapshot.get().callerDirectory
    ) {
        runtimeSnapshot.set(
            RuntimeCallerIdSnapshot(
                users = users,
                approvedReports = approvedReports,
                callerDirectory = callerDirectory,
                callerIdEnabled = callerIdEnabled,
                spamWarningsEnabled = spamWarningsEnabled
            )
        )
    }

    fun hashPhoneForLookup(rawNumber: String, defaultCountryIso: String = "CI"): String {
        val norm = PhoneUtils.normalize(rawNumber, defaultCountryIso)
        if (norm.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun resolveFromKnownData(
        rawNumber: String,
        telecomDisplayName: String = "",
        direction: CallDirection = CallDirection.UNKNOWN,
        savedUsers: List<User> = emptyList(),
        phoneContacts: List<Contact> = emptyList(),
        approvedReports: List<CallerReport> = emptyList(),
        callerIdEnabled: Boolean = true,
        spamWarningsEnabled: Boolean = true,
        defaultCountryIso: String = "CI",
        callerDirectory: List<CallerDirectoryEntry> = emptyList()
    ): CallerIdentity {
        val norm = PhoneUtils.normalize(rawNumber, defaultCountryIso)
        val formatted = if (rawNumber.isNotBlank()) PhoneUtils.formatDisplay(rawNumber, defaultCountryIso) else ""
        val locality = PhoneLocalityResolver.resolve(rawNumber, defaultCountryIso)

        if (!callerIdEnabled) {
            val isUnresolved = telecomDisplayName.isBlank()
            return CallerIdentity(
                rawNumber = rawNumber,
                normalizedNumber = norm,
                formattedNumber = formatted,
                displayName = telecomDisplayName.ifBlank { formatted.ifBlank { "Numéro inconnu" } },
                direction = direction,
                locality = locality,
                isKnownContact = telecomDisplayName.isNotBlank(),
                confidence = CallerConfidenceLevel.LOW,
                sourceLabel = if (locality.countryName.isNotBlank()) "Indicatif réseau" else "",
                isUnknownCaller = isUnresolved
            )
        }

        val phoneHash = hashPhoneForLookup(rawNumber, defaultCountryIso)

        // Priority 1: Device Contacts
        val matchedContact = phoneContacts.firstOrNull { c ->
            PhoneUtils.matches(c.rawPhoneNumber, rawNumber) || PhoneUtils.matches(c.normalizedPhoneNumber, norm)
        }

        // Priority 2: BRAV Users (strictly honoring allowPhoneLookup unless already in device contacts)
        val matchedBravUser = matchedContact?.bravUser ?: savedUsers.firstOrNull { u ->
            u.allowPhoneLookup && (
                (phoneHash.isNotBlank() && u.phoneLookupHash == phoneHash) ||
                    (u.phoneMasked.isNotBlank() && PhoneUtils.matches(u.phoneMasked, rawNumber))
                )
        }
        val verifiedActive = matchedBravUser?.isVerifiedActive() == true

        // Priority 3: Verified Business Directory + Approved Community Caller Reports
        val matchedDirectoryEntry = if (phoneHash.isNotBlank()) {
            callerDirectory.firstOrNull { it.verified && it.phoneLookupHash == phoneHash }
        } else {
            null
        }
        val matchedReports = if (phoneHash.isNotBlank()) {
            approvedReports.filter { it.status == "approved" && it.phoneLookupHash == phoneHash }
        } else {
            emptyList()
        }
        val totalReportCount = matchedReports.sumOf { it.reportCount.coerceAtLeast(1) }
        val dominantReport = matchedReports.maxByOrNull { reportPriority(CallerReportCategory.fromRaw(it.category)) }
        val reportSafety = dominantReport?.let { toSafetyCategory(CallerReportCategory.fromRaw(it.category)) }
            ?: CallerSafetyCategory.NONE
        val directorySafety = matchedDirectoryEntry?.let { toDirectorySafety(it.category) }
            ?: CallerSafetyCategory.NONE
        val rawSafety = if (reportSafety != CallerSafetyCategory.NONE) reportSafety else directorySafety
        val effectiveSafety = if (!spamWarningsEnabled && rawSafety.isWarning) {
            CallerSafetyCategory.NONE
        } else {
            rawSafety
        }
        val approvedReportLabel = if (effectiveSafety != CallerSafetyCategory.NONE) {
            dominantReport?.label?.trim().orEmpty()
        } else ""
        val verifiedDirectoryLabel = matchedDirectoryEntry?.businessName?.trim().orEmpty()

        // Priority 4: Unknown caller fallback — strictly honest, NEVER invent an arbitrary name
        val explicitCandidateName = listOf(
            matchedContact?.preferredDisplayName,
            matchedBravUser?.displayName,
            verifiedDirectoryLabel.takeIf { it.isNotBlank() },
            approvedReportLabel.takeIf { !effectiveSafety.isWarning },
            telecomDisplayName
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        val isUnknown = explicitCandidateName.isBlank()
        val resolvedName = explicitCandidateName.ifBlank {
            formatted.ifBlank { "Numéro inconnu" }
        }

        val confidence = when {
            matchedContact != null || verifiedActive || matchedBravUser != null || matchedDirectoryEntry != null ->
                CallerConfidenceLevel.HIGH
            matchedReports.isNotEmpty() && totalReportCount >= 3 -> CallerConfidenceLevel.HIGH
            matchedReports.isNotEmpty() || telecomDisplayName.isNotBlank() -> CallerConfidenceLevel.MEDIUM
            else -> CallerConfidenceLevel.LOW
        }

        val sourceLabel = when {
            verifiedActive -> "Compte BRAV certifié"
            matchedContact != null -> "Répertoire personnel"
            matchedBravUser != null -> "Membre BRAV"
            matchedDirectoryEntry != null -> "Annuaire officiel BRAV"
            matchedReports.isNotEmpty() && effectiveSafety != CallerSafetyCategory.NONE ->
                "${effectiveSafety.labelFr} · Communauté BRAV"
            telecomDisplayName.isNotBlank() -> "Opérateur"
            locality.fixedCityLabel.isNotBlank() || locality.countryName.isNotBlank() -> "Numéro inconnu · Indicatif"
            else -> "Numéro inconnu"
        }

        return CallerIdentity(
            rawNumber = rawNumber,
            normalizedNumber = norm,
            formattedNumber = formatted,
            displayName = resolvedName,
            direction = direction,
            locality = locality,
            isKnownContact = matchedContact != null || matchedBravUser != null || matchedDirectoryEntry != null || telecomDisplayName.isNotBlank(),
            isBravUser = matchedBravUser != null,
            bravUserId = matchedBravUser?.userId.orEmpty(),
            avatarUrl = matchedBravUser?.avatarUrl.orEmpty(),
            isVerifiedBrav = verifiedActive,
            verifiedOrgName = if (verifiedActive) matchedBravUser?.verifiedOrgName.orEmpty() else "",
            confidence = confidence,
            safetyCategory = effectiveSafety,
            communityLabel = approvedReportLabel.ifBlank { verifiedDirectoryLabel },
            reportCount = totalReportCount,
            sourceLabel = sourceLabel,
            isUnknownCaller = isUnknown
        )
    }

    fun resolveWithSystemContacts(
        context: Context,
        rawNumber: String,
        telecomDisplayName: String = "",
        direction: CallDirection = CallDirection.UNKNOWN,
        defaultCountryIso: String = "CI"
    ): CallerIdentity {
        val snap = runtimeSnapshot.get()
        val contactMatchName = if (snap.callerIdEnabled) {
            lookupContactNameInDevice(context, rawNumber).orEmpty()
        } else ""
        val bestName = contactMatchName.ifBlank { telecomDisplayName }
        val resolved = resolveFromKnownData(
            rawNumber = rawNumber,
            telecomDisplayName = bestName,
            direction = direction,
            savedUsers = snap.users,
            approvedReports = snap.approvedReports,
            callerIdEnabled = snap.callerIdEnabled,
            spamWarningsEnabled = snap.spamWarningsEnabled,
            defaultCountryIso = defaultCountryIso,
            callerDirectory = snap.callerDirectory
        )
        return if (contactMatchName.isNotBlank()) {
            resolved.copy(
                isKnownContact = true,
                isUnknownCaller = false,
                confidence = CallerConfidenceLevel.HIGH,
                sourceLabel = if (resolved.isVerifiedBrav) "Compte BRAV certifié" else "Répertoire personnel"
            )
        } else {
            resolved
        }
    }

    private fun reportPriority(category: CallerReportCategory): Int = when (category) {
        CallerReportCategory.SCAM -> 5
        CallerReportCategory.SPAM -> 4
        CallerReportCategory.TELEMARKETING -> 3
        CallerReportCategory.BUSINESS -> 2
        CallerReportCategory.SAFE -> 1
    }

    private fun toDirectorySafety(category: String): CallerSafetyCategory = when (category.lowercase()) {
        "safe" -> CallerSafetyCategory.SAFE
        else -> CallerSafetyCategory.BUSINESS
    }

    private fun toSafetyCategory(category: CallerReportCategory): CallerSafetyCategory = when (category) {
        CallerReportCategory.TELEMARKETING -> CallerSafetyCategory.TELEMARKETING
        CallerReportCategory.SPAM -> CallerSafetyCategory.SPAM
        CallerReportCategory.SCAM -> CallerSafetyCategory.SCAM
        CallerReportCategory.BUSINESS -> CallerSafetyCategory.BUSINESS
        CallerReportCategory.SAFE -> CallerSafetyCategory.SAFE
    }

    private fun lookupContactNameInDevice(context: Context, rawNumber: String): String? {
        if (rawNumber.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
