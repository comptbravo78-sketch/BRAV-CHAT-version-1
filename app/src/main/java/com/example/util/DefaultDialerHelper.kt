package com.example.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

object DefaultDialerHelper {

    fun isDefaultDialerSupported(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        } else {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        }
    }

    fun isBravDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            val roleHeld = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            val telecomHeld = runCatching {
                context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
            }.getOrDefault(false)
            roleHeld || telecomHeld
        } else {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom?.defaultDialerPackage == context.packageName
        }
    }

    fun createRequestDefaultDialerIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                createDefaultAppsSettingsIntent()
            }
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun isCallScreeningSupported(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            false
        }
    }

    fun isBravCallScreeningHeld(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            false
        }
    }

    fun createRequestCallScreeningIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            } else {
                createDefaultAppsSettingsIntent()
            }
        } else {
            null
        }
    }

    fun createDefaultAppsSettingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    fun extractDialNumberFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action
        val data = intent.data ?: return null
        if (data.scheme != "tel") return null
        if (action != Intent.ACTION_DIAL && action != Intent.ACTION_VIEW) return null
        val raw = Uri.decode(data.schemeSpecificPart.orEmpty())
        val cleaned = sanitizeDialInput(raw)
        return cleaned.ifBlank { null }
    }

    fun sanitizeDialInput(input: String): String {
        val sb = StringBuilder()
        input.forEachIndexed { idx, c ->
            when {
                c.isDigit() -> sb.append(c)
                c == '+' && idx == 0 -> sb.append(c)
                c == '*' || c == '#' -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun hasCallPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun placeCarrierCall(context: Context, rawNumber: String): Boolean {
        val cleaned = sanitizeDialInput(rawNumber)
        if (cleaned.isBlank()) return false
        if (!hasCallPhonePermission(context)) return false
        val uri = Uri.fromParts("tel", cleaned, null)
        return runCatching {
            val telecom = context.getSystemService(TelecomManager::class.java)
            if (telecom != null) {
                telecom.placeCall(uri, Bundle())
                true
            } else {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
        }.getOrElse {
            runCatching {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }
}
