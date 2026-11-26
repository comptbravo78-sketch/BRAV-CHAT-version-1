package com.example.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.io.File
import java.io.FileOutputStream

object LauncherRepository {

    fun isLauncherComponentEnabled(context: Context): Boolean {
        val cn = ComponentName(context, BravLauncherActivity::class.java)
        val state = runCatching { context.packageManager.getComponentEnabledSetting(cn) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setLauncherComponentEnabled(context: Context, enabled: Boolean) {
        val cn = ComponentName(context, BravLauncherActivity::class.java)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            context.packageManager.setComponentEnabledSetting(cn, newState, PackageManager.DONT_KILL_APP)
        }
    }

    fun isBravDefaultLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm?.isRoleHeld(RoleManager.ROLE_HOME) == true) return true
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun createRequestDefaultLauncherIntent(context: Context): Intent {
        if (!isLauncherComponentEnabled(context)) {
            setLauncherComponentEnabled(context, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun queryInstalledLauncherApps(context: Context): List<LauncherAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        return resolved
            .mapNotNull { info ->
                val act = info.activityInfo ?: return@mapNotNull null
                val pkg = act.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
                LauncherAppInfo(
                    packageName = pkg,
                    activityName = act.name.orEmpty(),
                    label = label,
                    isBravBuiltIn = pkg == context.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<LauncherAppInfo> { !it.isBravBuiltIn }.thenBy { it.label.lowercase() })
    }

    fun launchApp(context: Context, app: LauncherAppInfo): Boolean {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(app.packageName, app.activityName)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openAppSystemDetails(context: Context, packageName: String) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun loadAppIconBitmap(
        context: Context,
        packageName: String,
        customIconPath: String? = null
    ): Bitmap? {
        if (!customIconPath.isNullOrBlank()) {
            val customFile = File(customIconPath)
            if (customFile.exists()) {
                val bmp = runCatching { BitmapFactory.decodeFile(customFile.absolutePath) }.getOrNull()
                if (bmp != null) return bmp
            }
        }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val size = 144
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bmp
            }
        }.getOrNull()
    }

    fun saveCustomAppIconFromUri(context: Context, packageName: String, uri: Uri): String? {
        return runCatching {
            val dir = File(context.filesDir, "custom_icons").apply { mkdirs() }
            val safeName = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val dest = File(dir, "$safeName.png")
            val rawBmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null
            val targetSize = 192
            val scaled = if (rawBmp.width > targetSize || rawBmp.height > targetSize) {
                Bitmap.createScaledBitmap(rawBmp, targetSize, targetSize, true)
            } else {
                rawBmp
            }
            FileOutputStream(dest).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            if (scaled !== rawBmp) scaled.recycle()
            rawBmp.recycle()
            dest.absolutePath
        }.getOrNull()
    }

    fun removeCustomAppIcon(context: Context, packageName: String) {
        val dir = File(context.filesDir, "custom_icons")
        val safeName = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        runCatching { File(dir, "$safeName.png").delete() }
    }

    fun clearAllCustomIcons(context: Context) {
        runCatching {
            File(context.filesDir, "custom_icons").listFiles()?.forEach { it.delete() }
        }
    }

    fun saveWallpaperFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            val dir = File(context.filesDir, "launcher_wallpapers").apply { mkdirs() }
            val dest = File(dir, "default_home_wallpaper.jpg")
            val rawBmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null
            val maxDim = 1600
            val scale = minOf(maxDim.toFloat() / rawBmp.width.coerceAtLeast(1), maxDim.toFloat() / rawBmp.height.coerceAtLeast(1), 1f)
            val targetW = (rawBmp.width * scale).toInt().coerceAtLeast(1)
            val targetH = (rawBmp.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(rawBmp, targetW, targetH, true) else rawBmp
            FileOutputStream(dest).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (scaled !== rawBmp) scaled.recycle()
            rawBmp.recycle()
            dest.absolutePath
        }.getOrNull()
    }

    fun loadWallpaperBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun clearWallpaper(context: Context, path: String? = null) {
        runCatching {
            if (!path.isNullOrBlank()) File(path).delete()
            File(context.filesDir, "launcher_wallpapers").listFiles()?.forEach { it.delete() }
        }
    }

    fun loadAppShortcuts(context: Context, packageName: String): List<LauncherShortcutInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return emptyList()
        return runCatching {
            val la = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
            if (!la.hasShortcutHostPermission()) return emptyList()
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            la.getShortcuts(query, Process.myUserHandle()).orEmpty().mapNotNull { s ->
                val label = s.shortLabel?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LauncherShortcutInfo(id = s.id, packageName = s.`package`, shortLabel = label)
            }
        }.getOrDefault(emptyList())
    }

    fun startAppShortcut(context: Context, shortcut: LauncherShortcutInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        return runCatching {
            val la = context.getSystemService(LauncherApps::class.java) ?: return false
            if (!la.hasShortcutHostPermission()) return false
            la.startShortcut(shortcut.packageName, shortcut.id, null, null, Process.myUserHandle())
            true
        }.getOrDefault(false)
    }
}
