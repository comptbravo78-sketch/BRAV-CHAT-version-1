package com.example.launcher

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

sealed interface LauncherWeatherState {
    data class Loading(val cityName: String) : LauncherWeatherState
    data class Available(
        val cityName: String,
        val temperatureCelsius: Int,
        val conditionLabelFr: String,
        val weatherCode: Int
    ) : LauncherWeatherState

    data class Unavailable(
        val cityName: String,
        val message: String = "Météo indisponible"
    ) : LauncherWeatherState
}

object LauncherWeatherClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    fun mapWmoCodeToFr(code: Int): String = when (code) {
        0 -> "Ciel dégagé"
        1, 2 -> "Peu nuageux"
        3 -> "Nuageux"
        45, 48 -> "Brouillard"
        51, 53, 55, 56, 57 -> "Bruine"
        61, 63, 65, 66, 67 -> "Pluie"
        71, 73, 75, 77 -> "Neige"
        80, 81, 82 -> "Averses"
        85, 86 -> "Averses de neige"
        95, 96, 99 -> "Orage"
        else -> "Météo actuelle"
    }

    fun weatherIconForCode(code: Int): ImageVector = when (code) {
        0, 1 -> Icons.Rounded.WbSunny
        2, 3, 45, 48 -> Icons.Rounded.Cloud
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.WaterDrop
        71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
        95, 96, 99 -> Icons.Rounded.Thunderstorm
        else -> Icons.Rounded.Cloud
    }

    suspend fun fetchCurrentWeather(city: LauncherWeatherCity): LauncherWeatherState =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    "https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}&current=temperature_2m,weather_code&timezone=auto"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext LauncherWeatherState.Unavailable(city.name)
                    }
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext LauncherWeatherState.Unavailable(city.name)
                    }
                    val json = JSONObject(body)
                    val current = json.optJSONObject("current")
                        ?: return@withContext LauncherWeatherState.Unavailable(city.name)
                    if (!current.has("temperature_2m")) {
                        return@withContext LauncherWeatherState.Unavailable(city.name)
                    }
                    val temp = current.getDouble("temperature_2m").roundToInt()
                    val code = current.optInt("weather_code", 2)
                    LauncherWeatherState.Available(
                        cityName = city.name,
                        temperatureCelsius = temp,
                        conditionLabelFr = mapWmoCodeToFr(code),
                        weatherCode = code
                    )
                }
            }.getOrElse {
                LauncherWeatherState.Unavailable(city.name)
            }
        }

    suspend fun searchCities(query: String): List<LauncherWeatherCity> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) {
            return@withContext LauncherWeatherCity.presets
        }
        val presetMatches = LauncherWeatherCity.presets.filter {
            it.name.contains(clean, ignoreCase = true)
        }
        val remote = runCatching {
            val url =
                "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(clean)}&count=6&language=fr&format=json"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return@use emptyList()
                val arr = JSONObject(raw).optJSONArray("results") ?: return@use emptyList()
                (0 until arr.length()).mapNotNull { idx ->
                    val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                    val name = o.optString("name").trim()
                    val country = o.optString("country").trim()
                    if (name.isBlank() || !o.has("latitude") || !o.has("longitude")) return@mapNotNull null
                    val label = if (country.isNotBlank()) "$name, $country" else name
                    LauncherWeatherCity(
                        name = label,
                        latitude = o.getDouble("latitude"),
                        longitude = o.getDouble("longitude")
                    )
                }
            }
        }.getOrDefault(emptyList())

        (remote + presetMatches).distinctBy { it.name.lowercase() }
    }
}

@Composable
internal fun LauncherClockWeatherHeader(
    clockStyle: LauncherClockStyle,
    clockText: String,
    dateText: String,
    weatherState: LauncherWeatherState,
    homeTextColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    hasUserWallpaper: Boolean,
    onOpenWeatherCityPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (clockStyle) {
        LauncherClockStyle.EDITORIAL -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = clockText.ifBlank { "12:00" },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = homeTextColor,
                    modifier = Modifier.testTag("launcher_clock_editorial")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (hasUserWallpaper) Color.White.copy(alpha = 0.88f) else accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    WeatherCompactPill(
                        weatherState = weatherState,
                        homeTextColor = homeTextColor,
                        accentColor = accentColor,
                        cardBgColor = cardBgColor,
                        onClick = onOpenWeatherCityPicker
                    )
                }
            }
        }

        LauncherClockStyle.MINIMAL -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = clockText.ifBlank { "12:00" },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    color = homeTextColor,
                    modifier = Modifier.testTag("launcher_clock_minimal")
                )
                Column {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = homeTextColor.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    WeatherInlineSummary(
                        weatherState = weatherState,
                        textColor = accentColor,
                        onClick = onOpenWeatherCityPicker
                    )
                }
            }
        }

        LauncherClockStyle.BOLD_CARD -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardBgColor,
                border = BorderStroke(0.75.dp, homeTextColor.copy(alpha = 0.12f)),
                modifier = modifier.testTag("launcher_clock_bold_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = clockText.ifBlank { "12:00" },
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = homeTextColor
                        )
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasUserWallpaper) Color.White.copy(alpha = 0.85f) else accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    WeatherCompactPill(
                        weatherState = weatherState,
                        homeTextColor = homeTextColor,
                        accentColor = accentColor,
                        cardBgColor = homeTextColor.copy(alpha = 0.08f),
                        onClick = onOpenWeatherCityPicker
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherCompactPill(
    weatherState: LauncherWeatherState,
    homeTextColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = cardBgColor,
        modifier = Modifier.testTag("launcher_weather_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when (weatherState) {
                is LauncherWeatherState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.6.dp,
                        color = accentColor
                    )
                    Text(
                        text = weatherState.cityName,
                        style = MaterialTheme.typography.labelSmall,
                        color = homeTextColor,
                        maxLines = 1
                    )
                }
                is LauncherWeatherState.Available -> {
                    Icon(
                        imageVector = LauncherWeatherClient.weatherIconForCode(weatherState.weatherCode),
                        contentDescription = weatherState.conditionLabelFr,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${weatherState.temperatureCelsius}° · ${weatherState.cityName.substringBefore(",")}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = homeTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                is LauncherWeatherState.Unavailable -> {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = homeTextColor.copy(alpha = 0.65f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = weatherState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = homeTextColor.copy(alpha = 0.72f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherInlineSummary(
    weatherState: LauncherWeatherState,
    textColor: Color,
    onClick: () -> Unit
) {
    val label = when (weatherState) {
        is LauncherWeatherState.Loading -> "${weatherState.cityName}…"
        is LauncherWeatherState.Available -> "${weatherState.temperatureCelsius}°C • ${weatherState.conditionLabelFr}"
        is LauncherWeatherState.Unavailable -> weatherState.message
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("launcher_weather_inline")
    )
}

@Composable
fun LauncherWeatherCityPickerDialog(
    currentCity: LauncherWeatherCity,
    onSelectCity: (LauncherWeatherCity) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var cities by remember { mutableStateOf(LauncherWeatherCity.presets) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.length < 2) {
            cities = LauncherWeatherCity.presets
            searching = false
            return@LaunchedEffect
        }
        delay(320L)
        searching = true
        cities = LauncherWeatherClient.searchCities(clean)
        searching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ville météo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rechercher une ville (Open-Meteo)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("launcher_weather_city_search")
                )
                if (searching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Recherche...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(cities, key = { "${it.name}_${it.latitude}_${it.longitude}" }) { city ->
                        val selected = city.name.equals(currentCity.name, ignoreCase = true)
                        Surface(
                            onClick = { onSelectCity(city) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = city.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
