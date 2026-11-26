package com.example.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

data class CountryDialInfo(
    val iso: String,
    val nameFr: String,
    val dialCode: String,
    val flagEmoji: String
) {
    val label: String get() = "$flagEmoji $nameFr ($dialCode)"
}

object CountryDialCodeHelper {
    val countries: List<CountryDialInfo> = listOf(
        CountryDialInfo("CI", "Côte d'Ivoire", "+225", "\uD83C\uDDE8\uD83C\uDDEE"),
        CountryDialInfo("FR", "France", "+33", "\uD83C\uDDEB\uD83C\uDDF7"),
        CountryDialInfo("SN", "Sénégal", "+221", "\uD83C\uDDF8\uD83C\uDDF3"),
        CountryDialInfo("CM", "Cameroun", "+237", "\uD83C\uDDE8\uD83C\uDDF2"),
        CountryDialInfo("ML", "Mali", "+223", "\uD83C\uDDF2\uD83C\uDDF1"),
        CountryDialInfo("BF", "Burkina Faso", "+226", "\uD83C\uDDE7\uD83C\uDDEB"),
        CountryDialInfo("BJ", "Bénin", "+229", "\uD83C\uDDE7\uD83C\uDDEF"),
        CountryDialInfo("TG", "Togo", "+228", "\uD83C\uDDF9\uD83C\uDDEC"),
        CountryDialInfo("GN", "Guinée", "+224", "\uD83C\uDDEC\uD83C\uDDF3"),
        CountryDialInfo("CD", "RD Congo", "+243", "\uD83C\uDDE8\uD83C\uDDE9"),
        CountryDialInfo("GA", "Gabon", "+241", "\uD83C\uDDEC\uD83C\uDDE6"),
        CountryDialInfo("MA", "Maroc", "+212", "\uD83C\uDDF2\uD83C\uDDE6"),
        CountryDialInfo("TN", "Tunisie", "+216", "\uD83C\uDDF9\uD83C\uDDF3"),
        CountryDialInfo("DZ", "Algérie", "+213", "\uD83C\uDDE9\uD83C\uDDFF"),
        CountryDialInfo("BE", "Belgique", "+32", "\uD83C\uDDE7\uD83C\uDDEA"),
        CountryDialInfo("CH", "Suisse", "+41", "\uD83C\uDDE8\uD83C\uDDED"),
        CountryDialInfo("CA", "Canada", "+1", "\uD83C\uDDE8\uD83C\uDDE6"),
        CountryDialInfo("US", "États-Unis", "+1", "\uD83C\uDDFA\uD83C\uDDF8"),
        CountryDialInfo("GB", "Royaume-Uni", "+44", "\uD83C\uDDEC\uD83C\uDDE7"),
        CountryDialInfo("GH", "Ghana", "+233", "\uD83C\uDDEC\uD83C\uDDED"),
        CountryDialInfo("NG", "Nigéria", "+234", "\uD83C\uDDF3\uD83C\uDDEC")
    )

    fun byIso(iso: String): CountryDialInfo? =
        countries.firstOrNull { it.iso.equals(iso.trim(), ignoreCase = true) }

    fun detectFromDevice(context: Context): CountryDialInfo {
        val tm = runCatching { context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager }.getOrNull()
        val candidates = listOfNotNull(
            tm?.networkCountryIso?.uppercase(),
            tm?.simCountryIso?.uppercase(),
            Locale.getDefault().country.uppercase()
        )
        for (iso in candidates) {
            if (iso.length == 2) {
                byIso(iso)?.let { return it }
            }
        }
        return countries.first()
    }

    @SuppressLint("MissingPermission")
    suspend fun detectWithGpsIfPermitted(context: Context): Pair<CountryDialInfo, String> =
        withContext(Dispatchers.IO) {
            val base = detectFromDevice(context)
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                return@withContext base to ""
            }
            val lm = runCatching { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }.getOrNull()
                ?: return@withContext base to ""
            val loc = runCatching {
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }.getOrNull() ?: return@withContext base to ""

            return@withContext runCatching {
                @Suppress("DEPRECATION")
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Geocoder(context, Locale.FRENCH).getFromLocation(loc.latitude, loc.longitude, 1)
                } else {
                    Geocoder(context, Locale.FRENCH).getFromLocation(loc.latitude, loc.longitude, 1)
                }
                val addr = addresses?.firstOrNull()
                val iso = addr?.countryCode?.uppercase().orEmpty()
                val city = addr?.locality ?: addr?.adminArea.orEmpty()
                val country = byIso(iso) ?: base
                country to city
            }.getOrElse { base to "" }
        }
}
