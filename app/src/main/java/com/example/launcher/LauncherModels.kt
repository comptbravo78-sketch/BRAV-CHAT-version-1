package com.example.launcher

import com.example.ui.components.BravPatternType
import org.json.JSONArray
import org.json.JSONObject

enum class LauncherAfricanWallpaper(
    val id: String,
    val labelFr: String,
    val pattern: BravPatternType?,
    val lightTopHex: Long,
    val lightBottomHex: Long,
    val darkTopHex: Long,
    val darkBottomHex: Long,
    val patternPrimaryLightHex: Long,
    val patternSecondaryLightHex: Long,
    val patternPrimaryDarkHex: Long,
    val patternSecondaryDarkHex: Long,
    val patternOpacityLight: Float,
    val patternOpacityDark: Float
) {
    NONE(
        id = "none",
        labelFr = "Uni minimal",
        pattern = null,
        lightTopHex = 0xFFFAF8F3L,
        lightBottomHex = 0xFFF1EEE8L,
        darkTopHex = 0xFF100D16L,
        darkBottomHex = 0xFF191423L,
        patternPrimaryLightHex = 0xFF570CA2L,
        patternSecondaryLightHex = 0xFFC6A66BL,
        patternPrimaryDarkHex = 0xFFD9BF8CL,
        patternSecondaryDarkHex = 0xFFB376FAL,
        patternOpacityLight = 0f,
        patternOpacityDark = 0f
    ),
    KENTE_ROYAL(
        id = "kente_royal",
        labelFr = "Kente Royal",
        pattern = BravPatternType.KENTE_DIAMOND,
        lightTopHex = 0xFFFAF8F3L,
        lightBottomHex = 0xFFF1EAE0L,
        darkTopHex = 0xFF140A24L,
        darkBottomHex = 0xFF0C0716L,
        patternPrimaryLightHex = 0xFF570CA2L,
        patternSecondaryLightHex = 0xFFC6A66BL,
        patternPrimaryDarkHex = 0xFFD9BF8CL,
        patternSecondaryDarkHex = 0xFFB376FAL,
        patternOpacityLight = 0.065f,
        patternOpacityDark = 0.085f
    ),
    SOLAR_ADINKRA_GOLD(
        id = "solar_adinkra",
        labelFr = "Soleil Or",
        pattern = BravPatternType.SOLAR_ADINKRA,
        lightTopHex = 0xFFFAF8F3L,
        lightBottomHex = 0xFFF5EFE3L,
        darkTopHex = 0xFF120E1AL,
        darkBottomHex = 0xFF1C1429L,
        patternPrimaryLightHex = 0xFFC6A66BL,
        patternSecondaryLightHex = 0xFF570CA2L,
        patternPrimaryDarkHex = 0xFFD9BF8CL,
        patternSecondaryDarkHex = 0xFFC084FCL,
        patternOpacityLight = 0.085f,
        patternOpacityDark = 0.09f
    ),
    IVORY_HERITAGE(
        id = "ivory_heritage",
        labelFr = "Héritage Ivoire",
        pattern = BravPatternType.HERITAGE_COVER,
        lightTopHex = 0xFFFAF8F3L,
        lightBottomHex = 0xFFEFEAE0L,
        darkTopHex = 0xFF160E24L,
        darkBottomHex = 0xFF0E0916L,
        patternPrimaryLightHex = 0xFF570CA2L,
        patternSecondaryLightHex = 0xFFC6A66BL,
        patternPrimaryDarkHex = 0xFFD9BF8CL,
        patternSecondaryDarkHex = 0xFF8B5CF6L,
        patternOpacityLight = 0.055f,
        patternOpacityDark = 0.075f
    ),
    WAX_ARTISAN(
        id = "wax_artisan",
        labelFr = "Wax Contemporain",
        pattern = BravPatternType.WAX_TEXTILE,
        lightTopHex = 0xFFFAF8F3L,
        lightBottomHex = 0xFFF3ECE4L,
        darkTopHex = 0xFF130B20L,
        darkBottomHex = 0xFF1D132EL,
        patternPrimaryLightHex = 0xFF570CA2L,
        patternSecondaryLightHex = 0xFFC6A66BL,
        patternPrimaryDarkHex = 0xFFD9BF8CL,
        patternSecondaryDarkHex = 0xFFB376FAL,
        patternOpacityLight = 0.065f,
        patternOpacityDark = 0.08f
    );

    companion object {
        fun fromId(raw: String?): LauncherAfricanWallpaper =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: KENTE_ROYAL
    }
}

enum class LauncherThemePalette(
    val displayName: String,
    val primaryHex: Long,
    val iconContainerHex: Long,
    val iconTintHex: Long,
    val wallpaperTopHex: Long,
    val wallpaperBottomHex: Long,
    val textHex: Long
) {
    BRAV_VIOLET(
        displayName = "BRAV Violet",
        primaryHex = 0xFF6D28D9,
        iconContainerHex = 0xFF2E1065,
        iconTintHex = 0xFFF5F3FF,
        wallpaperTopHex = 0xFF140A2E,
        wallpaperBottomHex = 0xFF090517,
        textHex = 0xFFF8FAFC
    ),
    OBSIDIAN_DARK(
        displayName = "Obsidienne Pro",
        primaryHex = 0xFF8B5CF6,
        iconContainerHex = 0xFF18181B,
        iconTintHex = 0xFFE4E4E7,
        wallpaperTopHex = 0xFF09090B,
        wallpaperBottomHex = 0xFF18181B,
        textHex = 0xFFFAFAFA
    ),
    ROYAL_INDIGO(
        displayName = "Indigo Royal",
        primaryHex = 0xFF3B82F6,
        iconContainerHex = 0xFF172554,
        iconTintHex = 0xFFDBEAFE,
        wallpaperTopHex = 0xFF0F172A,
        wallpaperBottomHex = 0xFF020617,
        textHex = 0xFFF1F5F9
    ),
    EMERALD_GLASS(
        displayName = "Émeraude",
        primaryHex = 0xFF10B981,
        iconContainerHex = 0xFF064E3B,
        iconTintHex = 0xFFD1FAE5,
        wallpaperTopHex = 0xFF022C22,
        wallpaperBottomHex = 0xFF041410,
        textHex = 0xFFECFDF5
    ),
    CRIMSON_PRO(
        displayName = "Rubis BRAV",
        primaryHex = 0xFFEF4444,
        iconContainerHex = 0xFF450A0A,
        iconTintHex = 0xFFFEE2E2,
        wallpaperTopHex = 0xFF1F0808,
        wallpaperBottomHex = 0xFF0B0404,
        textHex = 0xFFFEF2F2
    ),
    BRAV_PEARL(
        displayName = "Perle Lumière",
        primaryHex = 0xFF570CA2,
        iconContainerHex = 0xFFFFFFFF,
        iconTintHex = 0xFF570CA2,
        wallpaperTopHex = 0xFFF4F0FF,
        wallpaperBottomHex = 0xFFE6EEFF,
        textHex = 0xFF0F172A
    );

    companion object {
        fun fromId(raw: String?): LauncherThemePalette =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BRAV_VIOLET
    }
}

enum class LauncherClockStyle(val labelFr: String) {
    EDITORIAL("Éditorial"),
    MINIMAL("Minimaliste"),
    BOLD_CARD("Carte Verre");

    companion object {
        fun fromId(raw: String?): LauncherClockStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: EDITORIAL
    }
}

data class LauncherWeatherCity(
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("lat", latitude)
        put("lon", longitude)
    }.toString()

    companion object {
        val presets: List<LauncherWeatherCity> = listOf(
            LauncherWeatherCity("Abidjan", 5.35995, -4.00825),
            LauncherWeatherCity("Dakar", 14.71668, -17.46769),
            LauncherWeatherCity("Paris", 48.85661, 2.35222),
            LauncherWeatherCity("Yaoundé", 3.84803, 11.50208),
            LauncherWeatherCity("Kinshasa", -4.44193, 15.26629),
            LauncherWeatherCity("Casablanca", 33.57311, -7.58984),
            LauncherWeatherCity("Bamako", 12.63923, -8.00289),
            LauncherWeatherCity("Lomé", 6.13194, 1.22278),
            LauncherWeatherCity("Cotonou", 6.37029, 2.39124)
        )

        fun fromJson(raw: String): LauncherWeatherCity {
            if (raw.isBlank()) return presets.first()
            return runCatching {
                val obj = JSONObject(raw)
                LauncherWeatherCity(
                    name = obj.optString("name", "Abidjan").ifBlank { "Abidjan" },
                    latitude = obj.optDouble("lat", 5.35995),
                    longitude = obj.optDouble("lon", -4.00825)
                )
            }.getOrDefault(presets.first())
        }
    }
}

enum class LauncherIconShape(val displayName: String, val cornerPercent: Int) {
    SQUIRCLE("Squircle", 28),
    CIRCLE("Cercle", 50),
    ROUNDED_SQUARE("Carré doux", 18),
    BADGE("Écusson", 12);

    companion object {
        fun fromId(raw: String?): LauncherIconShape =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SQUIRCLE
    }
}

data class LauncherAppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isBravBuiltIn: Boolean = false
)

data class LauncherFolder(
    val id: String,
    val name: String,
    val packageNames: List<String>
)

data class LauncherShortcutInfo(
    val id: String,
    val packageName: String,
    val shortLabel: String
)

object LauncherJsonCodec {
    fun parseFolders(raw: String): List<LauncherFolder> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                val name = o.optString("name", "Dossier")
                val pkgsArr = o.optJSONArray("packageNames") ?: JSONArray()
                val pkgs = (0 until pkgsArr.length()).mapNotNull { j ->
                    pkgsArr.optString(j).takeIf { it.isNotBlank() }
                }
                LauncherFolder(id = id, name = name, packageNames = pkgs)
            }
        }.getOrDefault(emptyList())
    }

    fun serializeFolders(folders: List<LauncherFolder>): String {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("packageNames", JSONArray(f.packageNames))
            })
        }
        return arr.toString()
    }

    fun parseStringMap(raw: String): Map<String, String> {
        if (raw.isBlank() || raw == "{}") return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val v = obj.optString(key)
                    if (key.isNotBlank() && v.isNotBlank()) put(key, v)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun serializeStringMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun parseStringList(raw: String): List<String> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    fun serializeStringList(items: List<String>): String = JSONArray(items).toString()
}
