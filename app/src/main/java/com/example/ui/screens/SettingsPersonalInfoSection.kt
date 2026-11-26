package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.local.AppPreferencesState
import com.example.model.User
import com.example.model.UserDevice
import com.example.model.UserPrivateInfo
import com.example.network.LoginSessionEntry
import com.example.ui.components.bravOutlinedTextFieldColors
import com.example.ui.theme.BravPurple
import com.example.ui.theme.BravPurplePrimary
import com.example.ui.theme.BravRed
import com.example.ui.theme.BravTheme
import com.example.ui.theme.BravWhite
import com.example.ui.theme.StatusOnlineGreen
import com.example.util.CountryDialCodeHelper
import kotlinx.coroutines.launch

data class PersonalInfoSavePayload(
    val displayName: String,
    val username: String,
    val bio: String,
    val birthDate: String,
    val homeAddress: String,
    val locationCity: String,
    val locationDetails: String,
    val phoneNumber: String,
    val email: String
)

@Composable
fun SettingsPersonalInfoCard(
    currentUser: User?,
    privateInfo: UserPrivateInfo?,
    firebaseEmail: String,
    appPrefs: AppPreferencesState,
    onSelectCountry: (iso: String, dialCode: String) -> Unit,
    onSavePersonalInfo: suspend (PersonalInfoSavePayload) -> Result<Unit>,
    onValidationFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lux = BravTheme.colors

    var displayName by remember { mutableStateOf(currentUser?.displayName.orEmpty()) }
    var username by remember { mutableStateOf(currentUser?.username.orEmpty()) }
    var bio by remember { mutableStateOf(currentUser?.bio.orEmpty()) }
    var birthDate by remember { mutableStateOf(privateInfo?.birthDate.orEmpty()) }
    var homeAddress by remember { mutableStateOf(privateInfo?.homeAddress.orEmpty()) }
    var locationCity by remember { mutableStateOf(currentUser?.locationCity.orEmpty()) }
    var locationDetails by remember { mutableStateOf(currentUser?.locationDetails.orEmpty()) }
    var phoneNumber by remember { mutableStateOf(privateInfo?.phoneNumber.orEmpty()) }
    var email by remember { mutableStateOf(privateInfo?.email.orEmpty().ifBlank { firebaseEmail }) }

    var dirty by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser, privateInfo, firebaseEmail) {
        if (!dirty) {
            currentUser?.let {
                if (it.displayName.isNotBlank()) displayName = it.displayName
                if (it.username.isNotBlank()) username = it.username
                bio = it.bio
                locationCity = it.locationCity
                locationDetails = it.locationDetails
            }
            privateInfo?.let {
                birthDate = it.birthDate
                homeAddress = it.homeAddress
                if (it.phoneNumber.isNotBlank()) phoneNumber = it.phoneNumber
                val resolvedEmail = it.email.ifBlank { firebaseEmail }
                if (resolvedEmail.isNotBlank()) email = resolvedEmail
            }
        }
    }

    SettingsSectionCard(title = "Informations personnelles") {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; dirty = true; savedBanner = false },
            label = { Text("Nom complet") },
            leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
            singleLine = true,
            colors = bravOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_personal_name_input")
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; dirty = true; savedBanner = false },
            label = { Text("Nom d'utilisateur") },
            leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
            singleLine = true,
            colors = bravOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_personal_username_input")
        )
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it; dirty = true; savedBanner = false },
            label = { Text("Biographie") },
            minLines = 2,
            maxLines = 4,
            colors = bravOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_personal_bio_input")
        )
        OutlinedTextField(
            value = birthDate,
            onValueChange = { birthDate = it; dirty = true; savedBanner = false },
            label = { Text("Date de naissance (JJ/MM/AAAA)") },
            leadingIcon = { Icon(Icons.Rounded.Cake, contentDescription = null) },
            singleLine = true,
            colors = bravOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_personal_birthdate_input")
        )
        OutlinedTextField(
            value = homeAddress,
            onValueChange = { homeAddress = it; dirty = true; savedBanner = false },
            label = { Text("Adresse") },
            leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            singleLine = true,
            colors = bravOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_personal_address_input")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = locationCity,
                onValueChange = { locationCity = it; dirty = true; savedBanner = false },
                label = { Text("Ville") },
                leadingIcon = { Icon(Icons.Rounded.LocationCity, contentDescription = null) },
                singleLine = true,
                colors = bravOutlinedTextFieldColors(),
                modifier = Modifier.weight(1f).testTag("settings_personal_city_input")
            )
            OutlinedTextField(
                value = locationDetails,
                onValueChange = { locationDetails = it; dirty = true; savedBanner = false },
                label = { Text("Pays / Quartier") },
                leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                singleLine = true,
                colors = bravOutlinedTextFieldColors(),
                modifier = Modifier.weight(1f).testTag("settings_personal_country_input")
            )
        }
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it; dirty = true; savedBanner = false },
            label = { Text("Téléphone") },
            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
            singleLine = true,
            colors = bravOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_personal_phone_input")
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; dirty = true; savedBanner = false },
            label = { Text("Adresse e-mail") },
            leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
            singleLine = true,
            colors = bravOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("settings_personal_email_input")
        )

        val selectedCountry = remember(appPrefs.countryIso) { CountryDialCodeHelper.byIso(appPrefs.countryIso) }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = lux.translucentSubtleSurface,
            border = BorderStroke(0.75.dp, lux.violetContour),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Public, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pays & indicatif", style = MaterialTheme.typography.labelSmall, color = lux.inkSecondary)
                    Text(
                        text = selectedCountry?.label ?: if (appPrefs.dialCode.isNotBlank()) "Indicatif ${appPrefs.dialCode}" else "Non défini",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = lux.inkPrimary
                    )
                }
                TextButton(onClick = { showCountryPicker = true }) { Text("Choisir", color = lux.titlePurple) }
                IconButton(onClick = {
                    scope.launch {
                        val (detected, city) = CountryDialCodeHelper.detectWithGpsIfPermitted(context)
                        onSelectCountry(detected.iso, detected.dialCode)
                        if (locationCity.isBlank() && city.isNotBlank()) locationCity = city
                        if (locationDetails.isBlank()) locationDetails = detected.nameFr
                        dirty = true
                    }
                }) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Détecter pays automatiquement", tint = lux.titlePurple)
                }
            }
        }

        AnimatedVisibility(visible = savedBanner) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StatusOnlineGreen.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = StatusOnlineGreen, modifier = Modifier.size(18.dp))
                    Text("Informations personnelles enregistrées", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = StatusOnlineGreen)
                }
            }
        }

        Button(
            enabled = !isSaving,
            onClick = {
                if (displayName.isBlank()) {
                    onValidationFeedback("Le nom complet est requis")
                    return@Button
                }
                isSaving = true
                scope.launch {
                    val res = onSavePersonalInfo(
                        PersonalInfoSavePayload(
                            displayName = displayName,
                            username = username,
                            bio = bio,
                            birthDate = birthDate,
                            homeAddress = homeAddress,
                            locationCity = locationCity,
                            locationDetails = locationDetails,
                            phoneNumber = phoneNumber,
                            email = email
                        )
                    )
                    isSaving = false
                    if (res.isSuccess) {
                        dirty = false
                        savedBanner = true
                    } else {
                        onValidationFeedback(res.exceptionOrNull()?.localizedMessage ?: "Échec d'enregistrement")
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = BravPurplePrimary, contentColor = BravWhite),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_save_personal_info_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BravWhite, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text("Enregistrer les informations", fontWeight = FontWeight.Bold)
        }
    }

    if (showCountryPicker) {
        AlertDialog(
            onDismissRequest = { showCountryPicker = false },
            containerColor = lux.modalSurface,
            title = { Text("Sélectionner un pays") },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(CountryDialCodeHelper.countries.size) { idx ->
                        val c = CountryDialCodeHelper.countries[idx]
                        TextButton(
                            onClick = {
                                showCountryPicker = false
                                onSelectCountry(c.iso, c.dialCode)
                                if (locationDetails.isBlank()) locationDetails = c.nameFr
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = c.label,
                                modifier = Modifier.fillMaxWidth(),
                                fontWeight = if (c.iso == appPrefs.countryIso) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryPicker = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
fun SettingsFieldPrivacyCard(
    privacyMap: Map<String, String>,
    onUpdatePrivacyKey: (String, String) -> Unit
) {
    SettingsSectionCard(title = "Visibilité du profil (champ par champ)") {
        listOf(
            "showPhoto" to "Photo de profil",
            "showCover" to "Photo de couverture",
            "showInfo" to "Biographie",
            "showCity" to "Ville et pays",
            "showBirthDate" to "Date de naissance",
            "showAddress" to "Adresse",
            "showPhone" to "Numéro de téléphone",
            "showEmail" to "Adresse e-mail",
            "showPosts" to "Publications Actus",
            "showStatus" to "Statut de disponibilité",
            "showLastSeen" to "Dernière présence"
        ).forEach { (key, label) ->
            val currentVal = privacyMap[key] ?: "everyone"
            val next = when (currentVal) {
                "everyone" -> "contacts"
                "contacts" -> "nobody"
                else -> "everyone"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onUpdatePrivacyKey(key, next) },
                    modifier = Modifier.testTag("privacy_toggle_$key")
                ) {
                    Text(
                        text = when (currentVal) {
                            "contacts" -> "Contacts"
                            "nobody" -> "Personne"
                            else -> "Tout le monde"
                        },
                        color = BravPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsConnectedDevicesCard(
    devices: List<UserDevice>,
    loginHistory: List<LoginSessionEntry>,
    onRevokeDevice: (String) -> Unit
) {
    val lux = BravTheme.colors
    SettingsSectionCard(title = "Appareils connectés") {
        if (devices.isEmpty() && loginHistory.isEmpty()) {
            Text(
                text = "Seul cet appareil est actuellement enregistré.",
                style = MaterialTheme.typography.bodySmall,
                color = lux.inkSecondary
            )
        } else {
            devices.forEach { dev ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = lux.translucentSubtleSurface,
                    border = BorderStroke(0.65.dp, lux.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Devices, contentDescription = null, tint = lux.titlePurple, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dev.deviceName.ifBlank { "Appareil Android" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (!dev.revoked) "Session active" else "Session révoquée",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!dev.revoked) StatusOnlineGreen else BravRed
                            )
                        }
                        if (!dev.revoked) {
                            OutlinedButton(
                                onClick = { onRevokeDevice(dev.deviceId) },
                                modifier = Modifier.testTag("revoke_device_${dev.deviceId}")
                            ) {
                                Text("Révoquer", color = BravRed)
                            }
                        }
                    }
                }
            }
            if (loginHistory.isNotEmpty()) {
                Text(
                    text = "Historique récent (${loginHistory.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = lux.inkSecondary
                )
                loginHistory.take(4).forEach { entry ->
                    val loc = listOf(entry.city, entry.countryCode).filter { it.isNotBlank() }.joinToString(", ")
                    val desc = listOfNotNull(entry.deviceName.ifBlank { "Session BRAV" }, loc.takeIf { it.isNotBlank() }).joinToString(" • ")
                    Text(
                        text = "• $desc",
                        style = MaterialTheme.typography.labelSmall,
                        color = lux.inkSecondary
                    )
                }
            }
        }
    }
}
