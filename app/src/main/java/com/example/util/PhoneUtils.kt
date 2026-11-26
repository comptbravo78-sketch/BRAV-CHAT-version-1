package com.example.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.security.MessageDigest

object PhoneUtils {

    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    @JvmOverloads
    fun normalize(rawNumber: String, defaultCountryIso: String = "CI"): String {
        val trimmed = rawNumber.trim()
        if (trimmed.isBlank()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""

        // Keep short service/emergency codes untouched (e.g. 111, 180, 185)
        if (!hasPlus && !digits.startsWith("00") && digits.length in 3..4) {
            return digits
        }

        // Preserve explicit Côte d'Ivoire 10-digit numbering plan & 8-digit legacy plan
        if (!hasPlus && !digits.startsWith("00") && defaultCountryIso.equals("CI", ignoreCase = true)) {
            if (digits.length == 10 && (
                    digits.startsWith("07") ||
                        digits.startsWith("05") ||
                        digits.startsWith("01") ||
                        digits.startsWith("27") ||
                        digits.startsWith("25") ||
                        digits.startsWith("21")
                    )
            ) {
                return "+225$digits"
            }
            if (digits.length == 8 && (
                    digits.startsWith("0") ||
                        digits.startsWith("2") ||
                        digits.startsWith("4") ||
                        digits.startsWith("5") ||
                        digits.startsWith("7")
                    )
            ) {
                return "+225$digits"
            }
        }

        val region = defaultCountryIso.uppercase().ifBlank { "CI" }
        val libNormalized = runCatching {
            val candidate = if (digits.startsWith("00") && digits.length > 4) {
                "+${digits.substring(2)}"
            } else {
                trimmed
            }
            val parsed = phoneUtil.parse(candidate, region)
            if (phoneUtil.isPossibleNumber(parsed)) {
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                null
            }
        }.getOrNull()
        if (!libNormalized.isNullOrBlank()) return libNormalized

        if (hasPlus) return "+$digits"
        if (digits.startsWith("00") && digits.length > 4) {
            return "+${digits.substring(2)}"
        }

        return when (region) {
            "CI" -> digits
            "FR" -> when {
                digits.length == 10 && digits.startsWith("0") && digits[1] in '1'..'9' ->
                    "+33${digits.substring(1)}"
                else -> digits
            }
            "SN" -> when {
                digits.length == 9 && (
                    digits.startsWith("77") ||
                        digits.startsWith("78") ||
                        digits.startsWith("76") ||
                        digits.startsWith("70") ||
                        digits.startsWith("75") ||
                        digits.startsWith("33")
                    ) -> "+221$digits"
                else -> digits
            }
            "CM" -> when {
                digits.length == 9 && (digits.startsWith("6") || digits.startsWith("2")) ->
                    "+237$digits"
                else -> digits
            }
            "ML" -> when {
                digits.length == 8 && (
                    digits.startsWith("6") || digits.startsWith("7") ||
                        digits.startsWith("20") || digits.startsWith("21") || digits.startsWith("44")
                    ) -> "+223$digits"
                else -> digits
            }
            "BF" -> when {
                digits.length == 8 && (
                    digits.startsWith("5") || digits.startsWith("6") ||
                        digits.startsWith("7") || digits.startsWith("2")
                    ) -> "+226$digits"
                else -> digits
            }
            "MA" -> when {
                digits.length == 10 && digits.startsWith("0") && (digits[1] == '5' || digits[1] == '6' || digits[1] == '7') ->
                    "+212${digits.substring(1)}"
                else -> digits
            }
            "BE" -> when {
                digits.length in 9..10 && digits.startsWith("0") -> "+32${digits.substring(1)}"
                else -> digits
            }
            "CH" -> when {
                digits.length == 10 && digits.startsWith("0") -> "+41${digits.substring(1)}"
                else -> digits
            }
            "US", "CA" -> when {
                digits.length == 10 -> "+1$digits"
                digits.length == 11 && digits.startsWith("1") -> "+$digits"
                else -> digits
            }
            else -> digits
        }
    }

    @JvmOverloads
    fun regionCodeForNumber(rawNumber: String, defaultCountryIso: String = "CI"): String {
        val norm = normalize(rawNumber, defaultCountryIso)
        if (!norm.startsWith("+")) return ""
        return runCatching {
            val parsed = phoneUtil.parse(norm, defaultCountryIso.uppercase().ifBlank { "CI" })
            phoneUtil.getRegionCodeForNumber(parsed).orEmpty()
        }.getOrDefault("")
    }

    fun matches(phoneA: String, phoneB: String): Boolean {
        val normA = normalize(phoneA)
        val normB = normalize(phoneB)
        if (normA.isEmpty() || normB.isEmpty()) return false
        if (normA == normB) return true
        val digitsA = normA.filter { it.isDigit() }
        val digitsB = normB.filter { it.isDigit() }
        if (digitsA == digitsB) return true
        val minLen = minOf(digitsA.length, digitsB.length)
        if (minLen >= 8 && digitsA.takeLast(8) == digitsB.takeLast(8)) return true
        return false
    }

    @JvmOverloads
    fun formatDisplay(phoneNumber: String, defaultCountryIso: String = "CI"): String {
        val norm = normalize(phoneNumber, defaultCountryIso)
        return when {
            norm.startsWith("+225") && norm.length == 14 -> {
                "${norm.substring(0, 4)} ${norm.substring(4, 6)} ${norm.substring(6, 8)} ${norm.substring(8, 10)} ${norm.substring(10, 12)} ${norm.substring(12, 14)}"
            }
            norm.startsWith("+33") && norm.length == 12 -> {
                "${norm.substring(0, 3)} ${norm.substring(3, 4)} ${norm.substring(4, 6)} ${norm.substring(6, 8)} ${norm.substring(8, 10)} ${norm.substring(10, 12)}"
            }
            norm.startsWith("+221") && norm.length == 13 -> {
                "${norm.substring(0, 4)} ${norm.substring(4, 6)} ${norm.substring(6, 9)} ${norm.substring(9, 11)} ${norm.substring(11, 13)}"
            }
            norm.startsWith("+1") && norm.length == 12 -> {
                "+1 (${norm.substring(2, 5)}) ${norm.substring(5, 8)}-${norm.substring(8, 12)}"
            }
            norm.startsWith("+") -> runCatching {
                val parsed = phoneUtil.parse(norm, defaultCountryIso.uppercase().ifBlank { "CI" })
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            }.getOrDefault(phoneNumber)
            else -> phoneNumber
        }
    }

    fun maskPhone(phoneNumber: String): String {
        val norm = normalize(phoneNumber)
        if (norm.length <= 4) return ""
        return "${norm.take(4)} •••• ${norm.takeLast(4)}"
    }

    fun sha256LookupHash(input: String): String {
        if (input.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.trim().lowercase().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
