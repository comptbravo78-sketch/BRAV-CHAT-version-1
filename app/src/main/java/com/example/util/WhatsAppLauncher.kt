package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Direct external handoff to WhatsApp via official wa.me links.
 * Falls back cleanly to a browser when WhatsApp / WhatsApp Business is not installed.
 */
object WhatsAppLauncher {

    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun hasValidPhoneDigits(rawPhoneOrContact: String): Boolean {
        val formatted = formatDigitsForWaMe(rawPhoneOrContact)
        return formatted.length in 7..15
    }

    fun formatDigitsForWaMe(
        rawPhoneOrContact: String,
        defaultCountryIso: String = "CI"
    ): String {
        val raw = rawPhoneOrContact.trim()
        if (raw.isBlank()) return ""

        // Extract likely phone candidate from mixed contact strings (e.g. "Tel: +225 07 00 00 01")
        val phoneCandidate = Regex("""(\+?\d[\d\s.\-()]{5,}\d)""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?: raw

        val normalized = PhoneUtils.normalize(phoneCandidate, defaultCountryIso)
        val digitsOnly = normalized.filter { it.isDigit() }
        return if (digitsOnly.length in 7..15) digitsOnly else ""
    }

    fun buildWaMeUrl(
        rawPhone: String,
        prefilledMessage: String = "",
        defaultCountryIso: String = "CI"
    ): String? {
        val digits = formatDigitsForWaMe(rawPhone, defaultCountryIso)
        if (digits.isBlank()) return null
        val trimmedMessage = prefilledMessage.trim()
        return if (trimmedMessage.isNotEmpty()) {
            "https://wa.me/$digits?text=${Uri.encode(trimmedMessage)}"
        } else {
            "https://wa.me/$digits"
        }
    }

    fun resolveInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        val candidates = listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)
        for (pkg in candidates) {
            val installed = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
            if (installed) return pkg
        }
        return null
    }

    fun isWhatsAppInstalled(context: Context): Boolean =
        resolveInstalledWhatsAppPackage(context) != null

    fun openWhatsAppChat(
        context: Context,
        rawPhone: String,
        prefilledMessage: String = "",
        defaultCountryIso: String = "CI"
    ): Result<Boolean> {
        val url = buildWaMeUrl(rawPhone, prefilledMessage, defaultCountryIso)
            ?: return Result.failure(IllegalArgumentException("Numéro de téléphone WhatsApp invalide"))

        val uri = Uri.parse(url)
        val installedPkg = resolveInstalledWhatsAppPackage(context)
        if (installedPkg != null) {
            val targeted = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(installedPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val startedDirect = runCatching {
                context.startActivity(targeted)
                true
            }.getOrDefault(false)
            if (startedDirect) {
                return Result.success(true)
            }
        }

        // Fallback to standard browser/handler with official wa.me link
        val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(fallback)
            Result.success(false)
        }.getOrElse {
            Result.failure(IllegalStateException("Impossible d'ouvrir WhatsApp ou le navigateur sur cet appareil"))
        }
    }
}
