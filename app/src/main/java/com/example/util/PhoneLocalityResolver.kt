package com.example.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

data class PhoneLocalityInfo(
    val normalizedNumber: String,
    val countryName: String,
    val countryIso: String,
    val flagEmoji: String,
    val lineType: String,
    val localityHint: String,
    val operatorHint: String,
    val cityName: String = "",
    val regionName: String = ""
) {
    val fixedCityLabel: String
        get() = when {
            cityName.isNotBlank() && regionName.isNotBlank() && !cityName.equals(regionName, ignoreCase = true) ->
                "$cityName ($regionName)"
            cityName.isNotBlank() -> cityName
            localityHint.isNotBlank() -> localityHint
            else -> countryName
        }

    val compactBadge: String
        get() = listOfNotNull(
            "$flagEmoji $countryName".takeIf { countryName.isNotBlank() },
            localityHint.takeIf { it.isNotBlank() },
            operatorHint.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
}

object PhoneLocalityResolver {

    @JvmOverloads
    fun resolve(rawNumber: String, defaultCountryIso: String = "CI"): PhoneLocalityInfo {
        val trimmed = rawNumber.trim()
        if (trimmed.isBlank()) {
            return PhoneLocalityInfo("", "", "", "", "", "", "")
        }

        // Handle short emergency/operator service codes cleanly before full normalization
        val rawDigits = trimmed.filter { it.isDigit() }
        if (!trimmed.startsWith("+") && rawDigits.length in 3..4 && !trimmed.startsWith("00")) {
            resolveShortCode(rawDigits, defaultCountryIso)?.let { return it }
        }

        val norm = PhoneUtils.normalize(trimmed, defaultCountryIso)
        if (norm.isBlank()) {
            return PhoneLocalityInfo("", "", "", "", "", "", "")
        }
        val digits = norm.filter { it.isDigit() }

        // Côte d'Ivoire (+225)
        if (norm.startsWith("+225") && digits.length >= 11) {
            return resolveIvoryCoast(norm, digits.removePrefix("225"))
        }

        // France (+33)
        if (norm.startsWith("+33") && digits.length >= 10) {
            return resolveFrance(norm, digits.removePrefix("33"))
        }

        // Sénégal (+221)
        if (norm.startsWith("+221")) {
            return resolveSenegal(norm, digits.removePrefix("221"))
        }

        // Cameroun (+237)
        if (norm.startsWith("+237")) {
            return resolveCameroon(norm, digits.removePrefix("237"))
        }

        // Mali (+223)
        if (norm.startsWith("+223")) {
            return resolveMali(norm, digits.removePrefix("223"))
        }

        // Burkina Faso (+226)
        if (norm.startsWith("+226")) {
            return resolveBurkina(norm, digits.removePrefix("226"))
        }

        // Bénin (+229)
        if (norm.startsWith("+229")) {
            return resolveBenin(norm, digits.removePrefix("229"))
        }

        // Togo (+228)
        if (norm.startsWith("+228")) {
            val local = digits.removePrefix("228")
            val (type, city, region, op) = when {
                local.startsWith("22") -> Quad("Fixe", "Lomé", "Maritime", "Togocom Fixe")
                local.startsWith("23") -> Quad("Fixe", "Tsévié / Atakpamé", "Plateaux", "Togocom Fixe")
                local.startsWith("24") -> Quad("Fixe", "Kpalimé", "Plateaux", "Togocom Fixe")
                local.startsWith("25") -> Quad("Fixe", "Sokodé", "Centrale", "Togocom Fixe")
                local.startsWith("26") -> Quad("Fixe", "Kara", "Kara", "Togocom Fixe")
                local.startsWith("27") -> Quad("Fixe", "Dapaong", "Savanes", "Togocom Fixe")
                local.startsWith("90") || local.startsWith("91") || local.startsWith("92") || local.startsWith("93") ->
                    Quad("Mobile", "Togo", "National", "Togocel")
                local.startsWith("96") || local.startsWith("97") || local.startsWith("98") || local.startsWith("99") ->
                    Quad("Mobile", "Togo", "National", "Moov Africa TG")
                else -> Quad("Mobile/Fixe", "Togo", "", "")
            }
            val loc = if (type == "Fixe") "$city · $region" else city
            return PhoneLocalityInfo(norm, "Togo", "TG", "\uD83C\uDDF9\uD83C\uDDEC", type, loc, op, city, region)
        }

        // Maroc (+212)
        if (norm.startsWith("+212")) {
            val local = digits.removePrefix("212")
            val (type, city, region, op) = when {
                local.startsWith("522") -> Quad("Fixe", "Casablanca", "Grand Casablanca", "Maroc Télécom")
                local.startsWith("523") -> Quad("Fixe", "Mohammédia / Settat", "Casablanca-Settat", "Fixe MA")
                local.startsWith("524") -> Quad("Fixe", "Marrakech", "Marrakech-Safi", "Fixe MA")
                local.startsWith("528") -> Quad("Fixe", "Agadir", "Souss-Massa", "Fixe MA")
                local.startsWith("535") -> Quad("Fixe", "Fès / Meknès", "Fès-Meknès", "Fixe MA")
                local.startsWith("537") -> Quad("Fixe", "Rabat / Salé", "Rabat-Salé-Kénitra", "Fixe MA")
                local.startsWith("539") -> Quad("Fixe", "Tanger / Tétouan", "Tanger-Tétouan", "Fixe MA")
                local.startsWith("6") || local.startsWith("7") -> Quad("Mobile", "Maroc", "National", "")
                else -> Quad("Mobile/Fixe", "Maroc", "", "")
            }
            val loc = if (type == "Fixe") "$city · $region" else city
            return PhoneLocalityInfo(norm, "Maroc", "MA", "\uD83C\uDDF2\uD83C\uDDE6", type, loc, op, city, region)
        }

        // Belgique (+32)
        if (norm.startsWith("+32")) {
            val local = digits.removePrefix("32")
            val (type, city, region) = when {
                local.startsWith("2") -> Triple("Fixe", "Bruxelles", "Bruxelles-Capitale")
                local.startsWith("3") -> Triple("Fixe", "Anvers", "Flandre")
                local.startsWith("4") -> Triple("Fixe", "Liège", "Wallonie")
                local.startsWith("9") -> Triple("Fixe", "Gand", "Flandre-Orientale")
                local.startsWith("71") -> Triple("Fixe", "Charleroi", "Hainaut")
                local.startsWith("81") -> Triple("Fixe", "Namur", "Wallonie")
                local.startsWith("47") || local.startsWith("48") || local.startsWith("49") -> Triple("Mobile", "Belgique", "National")
                else -> Triple("Mobile/Fixe", "Belgique", "")
            }
            val loc = if (region.isNotBlank() && type == "Fixe") "$city · $region" else city
            return PhoneLocalityInfo(norm, "Belgique", "BE", "\uD83C\uDDE7\uD83C\uDDEA", type, loc, "", city, region)
        }

        // Suisse (+41)
        if (norm.startsWith("+41")) {
            val local = digits.removePrefix("41")
            val (type, city, region) = when {
                local.startsWith("21") -> Triple("Fixe", "Lausanne", "Vaud")
                local.startsWith("22") -> Triple("Fixe", "Genève", "Genève")
                local.startsWith("26") -> Triple("Fixe", "Fribourg", "Fribourg")
                local.startsWith("31") -> Triple("Fixe", "Berne", "Berne")
                local.startsWith("32") -> Triple("Fixe", "Neuchâtel / Bienne", "Arc jurassien")
                local.startsWith("44") || local.startsWith("43") -> Triple("Fixe", "Zurich", "Zurich")
                local.startsWith("61") -> Triple("Fixe", "Bâle", "Bâle-Ville")
                local.startsWith("91") -> Triple("Fixe", "Lugano", "Tessin")
                local.startsWith("76") || local.startsWith("77") || local.startsWith("78") || local.startsWith("79") ->
                    Triple("Mobile", "Suisse", "National")
                else -> Triple("Mobile/Fixe", "Suisse", "")
            }
            val loc = if (region.isNotBlank() && type == "Fixe") "$city · $region" else city
            return PhoneLocalityInfo(norm, "Suisse", "CH", "\uD83C\uDDE8\uD83C\uDDED", type, loc, "", city, region)
        }

        // Royaume-Uni (+44)
        if (norm.startsWith("+44")) {
            val local = digits.removePrefix("44")
            val (type, city, region) = when {
                local.startsWith("20") -> Triple("Fixe", "Londres", "Greater London")
                local.startsWith("161") -> Triple("Fixe", "Manchester", "Angleterre")
                local.startsWith("121") -> Triple("Fixe", "Birmingham", "Midlands")
                local.startsWith("131") -> Triple("Fixe", "Édimbourg", "Écosse")
                local.startsWith("141") -> Triple("Fixe", "Glasgow", "Écosse")
                local.startsWith("7") -> Triple("Mobile", "Royaume-Uni", "National")
                else -> Triple("Mobile/Fixe", "Royaume-Uni", "")
            }
            val loc = if (region.isNotBlank() && type == "Fixe") "$city · $region" else city
            return PhoneLocalityInfo(norm, "Royaume-Uni", "GB", "\uD83C\uDDEC\uD83C\uDDE7", type, loc, "", city, region)
        }

        // Ghana (+233), Nigéria (+234), Guinée (+224), RDC (+243), Gabon (+241), Tunisie (+216), Algérie (+213)
        return when {
            norm.startsWith("+224") -> {
                val local = digits.removePrefix("224")
                val (type, city) = when {
                    local.startsWith("30") -> "Fixe" to "Conakry"
                    else -> "Mobile" to "Guinée"
                }
                PhoneLocalityInfo(norm, "Guinée", "GN", "\uD83C\uDDEC\uD83C\uDDF3", type, city, "", city)
            }
            norm.startsWith("+243") -> {
                val local = digits.removePrefix("243")
                val (type, city) = when {
                    local.startsWith("12") -> "Fixe" to "Kinshasa"
                    else -> "Mobile" to "RDC"
                }
                PhoneLocalityInfo(norm, "RD Congo", "CD", "\uD83C\uDDE8\uD83C\uDDE9", type, city, "", city)
            }
            norm.startsWith("+241") -> {
                val local = digits.removePrefix("241")
                val (type, city) = when {
                    local.startsWith("01") || local.startsWith("11") -> "Fixe" to "Libreville"
                    else -> "Mobile" to "Gabon"
                }
                PhoneLocalityInfo(norm, "Gabon", "GA", "\uD83C\uDDEC\uD83C\uDDE6", type, city, "", city)
            }
            norm.startsWith("+216") -> {
                val local = digits.removePrefix("216")
                val (type, city) = when {
                    local.startsWith("71") -> "Fixe" to "Tunis"
                    local.startsWith("73") -> "Fixe" to "Sousse / Monastir"
                    local.startsWith("74") -> "Fixe" to "Sfax"
                    else -> "Mobile" to "Tunisie"
                }
                PhoneLocalityInfo(norm, "Tunisie", "TN", "\uD83C\uDDF9\uD83C\uDDF3", type, city, "", city)
            }
            norm.startsWith("+213") -> {
                val local = digits.removePrefix("213")
                val (type, city) = when {
                    local.startsWith("21") || local.startsWith("23") -> "Fixe" to "Alger"
                    local.startsWith("31") -> "Fixe" to "Constantine"
                    local.startsWith("41") -> "Fixe" to "Oran"
                    else -> "Mobile" to "Algérie"
                }
                PhoneLocalityInfo(norm, "Algérie", "DZ", "\uD83C\uDDE9\uD83C\uDDFF", type, city, "", city)
            }
            norm.startsWith("+233") -> {
                val local = digits.removePrefix("233")
                val (type, city) = when {
                    local.startsWith("30") -> "Fixe" to "Accra"
                    local.startsWith("32") -> "Fixe" to "Kumasi"
                    else -> "Mobile" to "Ghana"
                }
                PhoneLocalityInfo(norm, "Ghana", "GH", "\uD83C\uDDEC\uD83C\uDDED", type, city, "", city)
            }
            norm.startsWith("+234") -> {
                val local = digits.removePrefix("234")
                val (type, city) = when {
                    local.startsWith("1") -> "Fixe" to "Lagos"
                    local.startsWith("9") -> "Fixe" to "Abuja"
                    else -> "Mobile" to "Nigéria"
                }
                PhoneLocalityInfo(norm, "Nigéria", "NG", "\uD83C\uDDF3\uD83C\uDDEC", type, city, "", city)
            }
            norm.startsWith("+1") -> resolveNorthAmerica(norm, digits.removePrefix("1"))
            else -> resolveWithLibPhoneNumber(norm, defaultCountryIso)
        }
    }

    private fun resolveWithLibPhoneNumber(norm: String, defaultCountryIso: String): PhoneLocalityInfo {
        val util = PhoneNumberUtil.getInstance()
        val parsed = runCatching { util.parse(norm, defaultCountryIso.uppercase().ifBlank { "CI" }) }.getOrNull()
        val iso = parsed?.let { util.getRegionCodeForNumber(it).orEmpty() }
            ?: PhoneUtils.regionCodeForNumber(norm, defaultCountryIso)
        val countryName = if (iso.length == 2) {
            Locale("", iso).getDisplayCountry(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
        } else {
            "International"
        }
        val flag = isoToFlagEmoji(iso)
        val lineType = when (parsed?.let { util.getNumberType(it) }) {
            PhoneNumberUtil.PhoneNumberType.MOBILE -> "Mobile"
            PhoneNumberUtil.PhoneNumberType.FIXED_LINE -> "Fixe"
            PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Mobile/Fixe"
            PhoneNumberUtil.PhoneNumberType.VOIP -> "VoIP"
            PhoneNumberUtil.PhoneNumberType.TOLL_FREE -> "Numéro vert"
            else -> "Standard"
        }
        return PhoneLocalityInfo(
            normalizedNumber = norm,
            countryName = countryName,
            countryIso = iso,
            flagEmoji = flag,
            lineType = lineType,
            localityHint = if (iso.length == 2) countryName else "",
            operatorHint = "",
            cityName = if (iso.length == 2) countryName else ""
        )
    }

    private fun isoToFlagEmoji(iso: String): String {
        val clean = iso.uppercase()
        if (clean.length != 2 || !clean.all { it in 'A'..'Z' }) return "\uD83C\uDF10"
        val first = Character.codePointAt(clean, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(clean, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private fun resolveShortCode(code: String, defaultCountryIso: String): PhoneLocalityInfo? {
        if (defaultCountryIso.equals("CI", ignoreCase = true)) {
            val label = when (code) {
                "100" -> "Service Client Orange CI" to "Orange CI"
                "101", "110", "111" -> "Police / Secours CI" to "Urgences Côte d'Ivoire"
                "170" -> "Gendarmerie Nationale CI" to "Urgences Côte d'Ivoire"
                "180" -> "Sapeurs-Pompiers (GSPM)" to "Urgences Abidjan / CI"
                "185" -> "SAMU Côte d'Ivoire" to "Urgences Santé"
                "179" -> "CIE Dépannage" to "Côte d'Ivoire"
                "175" -> "SODECI Service Client" to "Côte d'Ivoire"
                else -> null
            }
            if (label != null) {
                return PhoneLocalityInfo(
                    normalizedNumber = code,
                    countryName = "Côte d'Ivoire",
                    countryIso = "CI",
                    flagEmoji = "\uD83C\uDDE8\uD83C\uDDEE",
                    lineType = "Numéro court",
                    localityHint = label.first,
                    operatorHint = label.second,
                    cityName = "Côte d'Ivoire",
                    regionName = label.second
                )
            }
        }
        return null
    }

    private fun resolveIvoryCoast(norm: String, local: String): PhoneLocalityInfo {
        if (local.startsWith("27") || local.startsWith("25") || local.startsWith("21")) {
            val op = when {
                local.startsWith("27") -> "Orange Fixe"
                local.startsWith("25") -> "MTN Fixe"
                else -> "Moov Fixe"
            }
            // Strip 2-digit operator prefix (27 / 25 / 21) -> remaining zone starts with 20..36
            val geo = local.drop(2)
            val (city, area) = when {
                geo.startsWith("20") -> "Abidjan" to "Plateau / Adjamé / Attécoubé"
                geo.startsWith("2130") -> "Grand-Bassam" to "Sud-Comoé"
                geo.startsWith("2131") -> "Aboisso / Bonoua" to "Sud-Comoé"
                geo.startsWith("21") -> "Abidjan" to "Marcory / Treichville / Koumassi / Port-Bouët"
                geo.startsWith("224") || geo.startsWith("225") || geo.startsWith("22") ->
                    "Abidjan" to "Cocody / Angré / Riviera / Deux-Plateaux"
                geo.startsWith("235") -> "Dabou / Jacqueville" to "Grands-Ponts"
                geo.startsWith("23") -> "Abidjan" to "Yopougon / Songon"
                geo.startsWith("24") -> "Abidjan" to "Abobo / Anyama"
                geo.startsWith("3062") -> "Toumodi" to "Bélier"
                geo.startsWith("3063") -> "Dimbokro" to "N'Zi"
                geo.startsWith("3064") -> "Tiébissou" to "Bélier"
                geo.startsWith("30") -> "Yamoussoukro" to "District Autonome des Lacs"
                geo.startsWith("3166") -> "Katiola" to "Hambol"
                geo.startsWith("3165") -> "Béoumi" to "Gbêkê"
                geo.startsWith("31") -> "Bouaké" to "Vallée du Bandama"
                geo.startsWith("3277") -> "Gagnoa" to "Gôh"
                geo.startsWith("3272") -> "Soubré" to "Nawa"
                geo.startsWith("3279") -> "Issia" to "Haut-Sassandra"
                geo.startsWith("32") -> "Daloa" to "Haut-Sassandra"
                geo.startsWith("3376") -> "Danané" to "Tonkpi"
                geo.startsWith("3370") -> "Guiglo / Duékoué" to "Cavally / Guémon"
                geo.startsWith("3371") -> "Odienné" to "Kabadougou"
                geo.startsWith("33") -> "Man" to "Tonkpi / Montagnes"
                geo.startsWith("3472") -> "Sassandra" to "Gbôklé"
                geo.startsWith("3473") -> "Tabou" to "Bas-Sassandra"
                geo.startsWith("34") -> "San-Pédro" to "Bas-Sassandra"
                geo.startsWith("3592") -> "Bondoukou" to "Gontougo"
                geo.startsWith("3593") -> "Adzopé" to "La Mé"
                geo.startsWith("3594") -> "Agboville" to "Agnéby-Tiassa"
                geo.startsWith("3590") -> "Agnibilékrou" to "Indénié-Djuablin"
                geo.startsWith("35") -> "Abengourou" to "Indénié-Djuablin"
                geo.startsWith("3685") -> "Ferkessédougou" to "Tchologo"
                geo.startsWith("3684") -> "Boundiali" to "Bagoué"
                geo.startsWith("36") -> "Korhogo" to "Poro / Savanes"
                else -> "Côte d'Ivoire" to "Ligne fixe nationale"
            }
            return PhoneLocalityInfo(
                normalizedNumber = norm,
                countryName = "Côte d'Ivoire",
                countryIso = "CI",
                flagEmoji = "\uD83C\uDDE8\uD83C\uDDEE",
                lineType = "Fixe",
                localityHint = "$city · $area",
                operatorHint = op,
                cityName = city,
                regionName = area
            )
        }

        return when {
            local.startsWith("07") -> PhoneLocalityInfo(
                norm, "Côte d'Ivoire", "CI", "\uD83C\uDDE8\uD83C\uDDEE",
                "Mobile", "Mobile national · Orange CI", "Orange CI",
                cityName = "Côte d'Ivoire", regionName = "Réseau Orange"
            )
            local.startsWith("05") -> PhoneLocalityInfo(
                norm, "Côte d'Ivoire", "CI", "\uD83C\uDDE8\uD83C\uDDEE",
                "Mobile", "Mobile national · MTN CI", "MTN CI",
                cityName = "Côte d'Ivoire", regionName = "Réseau MTN"
            )
            local.startsWith("01") -> PhoneLocalityInfo(
                norm, "Côte d'Ivoire", "CI", "\uD83C\uDDE8\uD83C\uDDEE",
                "Mobile", "Mobile national · Moov Africa CI", "Moov Africa CI",
                cityName = "Côte d'Ivoire", regionName = "Réseau Moov"
            )
            else -> PhoneLocalityInfo(
                norm, "Côte d'Ivoire", "CI", "\uD83C\uDDE8\uD83C\uDDEE",
                "Standard", "Côte d'Ivoire", "",
                cityName = "Côte d'Ivoire"
            )
        }
    }

    private fun resolveFrance(norm: String, local: String): PhoneLocalityInfo {
        if (local.startsWith("6") || local.startsWith("7")) {
            return PhoneLocalityInfo(
                norm, "France", "FR", "\uD83C\uDDEB\uD83C\uDDF7",
                "Mobile", "France métropolitaine", "",
                cityName = "France", regionName = "Mobile"
            )
        }
        if (local.startsWith("9")) {
            return PhoneLocalityInfo(
                norm, "France", "FR", "\uD83C\uDDEB\uD83C\uDDF7",
                "VoIP", "Box Internet France", "",
                cityName = "France", regionName = "VoIP"
            )
        }
        val p3 = local.take(3)
        val (city, region) = when {
            p3 in setOf("140", "141", "142", "143", "144", "145", "146", "147", "148", "149", "153", "155", "156", "158", "170", "171", "172", "173", "174", "175", "176", "177", "178", "179", "180", "181", "182", "183", "184", "185", "186", "187", "188", "189") ->
                "Paris" to "Île-de-France"
            p3 in setOf("130", "134", "139") -> "Versailles / Cergy" to "Île-de-France"
            p3 in setOf("160", "164", "169") -> "Évry / Melun" to "Île-de-France"
            local.startsWith("1") -> "Paris" to "Île-de-France"
            p3 == "231" -> "Caen" to "Normandie"
            p3 == "232" || p3 == "235" -> "Rouen" to "Normandie"
            p3 == "238" -> "Orléans" to "Centre-Val de Loire"
            p3 == "240" || p3 == "251" -> "Nantes" to "Pays de la Loire"
            p3 == "241" -> "Angers" to "Pays de la Loire"
            p3 == "243" -> "Le Mans" to "Pays de la Loire"
            p3 == "247" -> "Tours" to "Centre-Val de Loire"
            p3 == "298" -> "Brest" to "Bretagne"
            p3 == "299" -> "Rennes" to "Bretagne"
            local.startsWith("2") -> "Rennes / Nantes" to "Nord-Ouest"
            p3 == "320" || p3 == "359" -> "Lille" to "Hauts-de-France"
            p3 == "326" -> "Reims" to "Grand Est"
            p3 == "380" -> "Dijon" to "Bourgogne-Franche-Comté"
            p3 == "383" -> "Nancy" to "Grand Est"
            p3 == "387" -> "Metz" to "Grand Est"
            p3 == "388" || p3 == "390" -> "Strasbourg" to "Grand Est"
            local.startsWith("3") -> "Lille / Strasbourg" to "Nord-Est"
            p3 == "442" -> "Aix-en-Provence" to "Provence-Alpes-Côte d'Azur"
            p3 == "467" -> "Montpellier" to "Occitanie"
            p3 == "472" || p3 == "478" || p3 == "426" -> "Lyon" to "Auvergne-Rhône-Alpes"
            p3 == "473" -> "Clermont-Ferrand" to "Auvergne-Rhône-Alpes"
            p3 == "476" -> "Grenoble" to "Auvergne-Rhône-Alpes"
            p3 == "491" -> "Marseille" to "Provence-Alpes-Côte d'Azur"
            p3 == "493" -> "Nice" to "Côte d'Azur"
            p3 == "494" -> "Toulon" to "Var"
            local.startsWith("4") -> "Lyon / Marseille" to "Sud-Est"
            p3 == "549" -> "Poitiers" to "Nouvelle-Aquitaine"
            p3 == "555" -> "Limoges" to "Nouvelle-Aquitaine"
            p3 == "556" || p3 == "557" -> "Bordeaux" to "Nouvelle-Aquitaine"
            p3 == "559" -> "Pau / Bayonne" to "Pyrénées-Atlantiques"
            p3 == "561" || p3 == "534" -> "Toulouse" to "Occitanie"
            local.startsWith("5") -> "Bordeaux / Toulouse" to "Sud-Ouest"
            else -> "France" to ""
        }
        val loc = if (region.isNotBlank()) "$city · $region" else city
        return PhoneLocalityInfo(
            normalizedNumber = norm,
            countryName = "France",
            countryIso = "FR",
            flagEmoji = "\uD83C\uDDEB\uD83C\uDDF7",
            lineType = if (local.startsWith("1") || local.startsWith("2") || local.startsWith("3") || local.startsWith("4") || local.startsWith("5")) "Fixe" else "Standard",
            localityHint = loc,
            operatorHint = "",
            cityName = city,
            regionName = region
        )
    }

    private fun resolveSenegal(norm: String, local: String): PhoneLocalityInfo {
        return when {
            local.startsWith("338") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Fixe", "Dakar · Centre & Presqu'île", "Sonatel Fixe",
                cityName = "Dakar", regionName = "Région de Dakar"
            )
            local.startsWith("3395") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Fixe", "Thiès", "Sonatel Fixe",
                cityName = "Thiès", regionName = "Thiès"
            )
            local.startsWith("3396") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Fixe", "Saint-Louis", "Sonatel Fixe",
                cityName = "Saint-Louis", regionName = "Nord"
            )
            local.startsWith("3394") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Fixe", "Kaolack", "Sonatel Fixe",
                cityName = "Kaolack", regionName = "Centre"
            )
            local.startsWith("3399") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Fixe", "Ziguinchor", "Sonatel Fixe",
                cityName = "Ziguinchor", regionName = "Casamance"
            )
            local.startsWith("33") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Fixe", "Dakar / Régions", "Sonatel Fixe",
                cityName = "Dakar"
            )
            local.startsWith("77") || local.startsWith("78") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Mobile", "Sénégal", "Orange SN",
                cityName = "Sénégal"
            )
            local.startsWith("76") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Mobile", "Sénégal", "Free SN",
                cityName = "Sénégal"
            )
            local.startsWith("70") -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Mobile", "Sénégal", "Expresso",
                cityName = "Sénégal"
            )
            else -> PhoneLocalityInfo(
                norm, "Sénégal", "SN", "\uD83C\uDDF8\uD83C\uDDF3",
                "Standard", "Sénégal", "",
                cityName = "Sénégal"
            )
        }
    }

    private fun resolveCameroon(norm: String, local: String): PhoneLocalityInfo {
        return when {
            local.startsWith("222") -> PhoneLocalityInfo(
                norm, "Cameroun", "CM", "\uD83C\uDDE8\uD83C\uDDF2",
                "Fixe", "Yaoundé · Centre", "Camtel Fixe",
                cityName = "Yaoundé", regionName = "Centre"
            )
            local.startsWith("233") -> PhoneLocalityInfo(
                norm, "Cameroun", "CM", "\uD83C\uDDE8\uD83C\uDDF2",
                "Fixe", "Douala · Littoral", "Camtel Fixe",
                cityName = "Douala", regionName = "Littoral"
            )
            local.startsWith("242") || local.startsWith("243") -> PhoneLocalityInfo(
                norm, "Cameroun", "CM", "\uD83C\uDDE8\uD83C\uDDF2",
                "Fixe", "Bafoussam / Ouest & Nord", "Camtel Fixe",
                cityName = "Bafoussam"
            )
            local.startsWith("69") || local.startsWith("655") || local.startsWith("656") ||
                local.startsWith("657") || local.startsWith("658") || local.startsWith("659") ->
                PhoneLocalityInfo(
                    norm, "Cameroun", "CM", "\uD83C\uDDE8\uD83C\uDDF2",
                    "Mobile", "Cameroun", "Orange CM",
                    cityName = "Cameroun"
                )
            local.startsWith("67") || local.startsWith("68") || local.startsWith("650") ||
                local.startsWith("651") || local.startsWith("652") || local.startsWith("653") || local.startsWith("654") ->
                PhoneLocalityInfo(
                    norm, "Cameroun", "CM", "\uD83C\uDDE8\uD83C\uDDF2",
                    "Mobile", "Cameroun", "MTN CM",
                    cityName = "Cameroun"
                )
            else -> PhoneLocalityInfo(
                norm, "Cameroun", "CM", "\uD83C\uDDE8\uD83C\uDDF2",
                "Mobile", "Cameroun", "",
                cityName = "Cameroun"
            )
        }
    }

    private fun resolveMali(norm: String, local: String): PhoneLocalityInfo {
        return when {
            local.startsWith("202") || local.startsWith("207") -> PhoneLocalityInfo(
                norm, "Mali", "ML", "\uD83C\uDDF2\uD83C\uDDF1",
                "Fixe", "Bamako · District", "Sotelma-Malitel",
                cityName = "Bamako", regionName = "District de Bamako"
            )
            local.startsWith("216") -> PhoneLocalityInfo(
                norm, "Mali", "ML", "\uD83C\uDDF2\uD83C\uDDF1",
                "Fixe", "Sikasso", "Fixe ML",
                cityName = "Sikasso"
            )
            local.startsWith("214") -> PhoneLocalityInfo(
                norm, "Mali", "ML", "\uD83C\uDDF2\uD83C\uDDF1",
                "Fixe", "Kayes", "Fixe ML",
                cityName = "Kayes"
            )
            local.startsWith("217") -> PhoneLocalityInfo(
                norm, "Mali", "ML", "\uD83C\uDDF2\uD83C\uDDF1",
                "Fixe", "Ségou", "Fixe ML",
                cityName = "Ségou"
            )
            local.startsWith("7") || local.startsWith("9") -> PhoneLocalityInfo(
                norm, "Mali", "ML", "\uD83C\uDDF2\uD83C\uDDF1",
                "Mobile", "Mali", "Orange ML",
                cityName = "Mali"
            )
            local.startsWith("6") -> PhoneLocalityInfo(
                norm, "Mali", "ML", "\uD83C\uDDF2\uD83C\uDDF1",
                "Mobile", "Mali", "Malitel",
                cityName = "Mali"
            )
            else -> PhoneLocalityInfo(norm, "Mali", "ML", "\uD83C\uDDF2\uD83C\uDDF1", "Mobile/Fixe", "Mali", "", "Mali")
        }
    }

    private fun resolveBurkina(norm: String, local: String): PhoneLocalityInfo {
        return when {
            local.startsWith("253") || local.startsWith("254") -> PhoneLocalityInfo(
                norm, "Burkina Faso", "BF", "\uD83C\uDDE7\uD83C\uDDEB",
                "Fixe", "Ouagadougou · Kadiogo", "Onatel Fixe",
                cityName = "Ouagadougou", regionName = "Centre"
            )
            local.startsWith("209") -> PhoneLocalityInfo(
                norm, "Burkina Faso", "BF", "\uD83C\uDDE7\uD83C\uDDEB",
                "Fixe", "Bobo-Dioulasso · Houet", "Onatel Fixe",
                cityName = "Bobo-Dioulasso", regionName = "Hauts-Bassins"
            )
            local.startsWith("244") -> PhoneLocalityInfo(
                norm, "Burkina Faso", "BF", "\uD83C\uDDE7\uD83C\uDDEB",
                "Fixe", "Koudougou / Centre-Ouest", "Onatel Fixe",
                cityName = "Koudougou"
            )
            local.startsWith("7") || local.startsWith("54") || local.startsWith("55") || local.startsWith("56") || local.startsWith("57") ->
                PhoneLocalityInfo(
                    norm, "Burkina Faso", "BF", "\uD83C\uDDE7\uD83C\uDDEB",
                    "Mobile", "Burkina Faso", "Orange BF",
                    cityName = "Burkina Faso"
                )
            else -> PhoneLocalityInfo(
                norm, "Burkina Faso", "BF", "\uD83C\uDDE7\uD83C\uDDEB",
                "Mobile/Fixe", "Burkina Faso", "",
                cityName = "Burkina Faso"
            )
        }
    }

    private fun resolveBenin(norm: String, local: String): PhoneLocalityInfo {
        val core = if (local.length == 10 && local.startsWith("01")) local.drop(2) else local
        return when {
            core.startsWith("21") -> PhoneLocalityInfo(
                norm, "Bénin", "BJ", "\uD83C\uDDE7\uD83C\uDDEF",
                "Fixe", "Cotonou · Littoral", "Bénin Télécoms",
                cityName = "Cotonou", regionName = "Littoral"
            )
            core.startsWith("20") -> PhoneLocalityInfo(
                norm, "Bénin", "BJ", "\uD83C\uDDE7\uD83C\uDDEF",
                "Fixe", "Porto-Novo · Ouémé", "Bénin Télécoms",
                cityName = "Porto-Novo", regionName = "Ouémé"
            )
            core.startsWith("22") -> PhoneLocalityInfo(
                norm, "Bénin", "BJ", "\uD83C\uDDE7\uD83C\uDDEF",
                "Fixe", "Abomey / Bohicon", "Bénin Télécoms",
                cityName = "Abomey"
            )
            core.startsWith("23") -> PhoneLocalityInfo(
                norm, "Bénin", "BJ", "\uD83C\uDDE7\uD83C\uDDEF",
                "Fixe", "Parakou · Borgou", "Bénin Télécoms",
                cityName = "Parakou", regionName = "Borgou"
            )
            else -> PhoneLocalityInfo(
                norm, "Bénin", "BJ", "\uD83C\uDDE7\uD83C\uDDEF",
                "Mobile", "Bénin", "",
                cityName = "Bénin"
            )
        }
    }

    private fun resolveNorthAmerica(norm: String, local: String): PhoneLocalityInfo {
        val npa = local.take(3)
        val (city, country, iso, flag) = when (npa) {
            "514", "438", "263" -> Quad("Montréal, QC", "Canada", "CA", "\uD83C\uDDE8\uD83C\uDDE6")
            "418", "581", "367" -> Quad("Québec, QC", "Canada", "CA", "\uD83C\uDDE8\uD83C\uDDE6")
            "450", "579" -> Quad("Laval / Longueuil, QC", "Canada", "CA", "\uD83C\uDDE8\uD83C\uDDE6")
            "819", "873" -> Quad("Gatineau / Sherbrooke, QC", "Canada", "CA", "\uD83C\uDDE8\uD83C\uDDE6")
            "613", "343" -> Quad("Ottawa, ON", "Canada", "CA", "\uD83C\uDDE8\uD83C\uDDE6")
            "416", "647", "437" -> Quad("Toronto, ON", "Canada", "CA", "\uD83C\uDDE8\uD83C\uDDE6")
            "604", "778", "236" -> Quad("Vancouver, BC", "Canada", "CA", "\uD83C\uDDE8\uD83C\uDDE6")
            "403", "587" -> Quad("Calgary, AB", "Canada", "CA", "\uD83C\uDDE8\uD83C\uDDE6")
            "212", "646", "917", "718", "347" -> Quad("New York, NY", "États-Unis", "US", "\uD83C\uDDFA\uD83C\uDDF8")
            "305", "786" -> Quad("Miami, FL", "États-Unis", "US", "\uD83C\uDDFA\uD83C\uDDF8")
            "202" -> Quad("Washington, DC", "États-Unis", "US", "\uD83C\uDDFA\uD83C\uDDF8")
            "404", "470", "678" -> Quad("Atlanta, GA", "États-Unis", "US", "\uD83C\uDDFA\uD83C\uDDF8")
            "312", "773" -> Quad("Chicago, IL", "États-Unis", "US", "\uD83C\uDDFA\uD83C\uDDF8")
            "213", "310", "424" -> Quad("Los Angeles, CA", "États-Unis", "US", "\uD83C\uDDFA\uD83C\uDDF8")
            "415", "628" -> Quad("San Francisco, CA", "États-Unis", "US", "\uD83C\uDDFA\uD83C\uDDF8")
            "713", "281", "832" -> Quad("Houston, TX", "États-Unis", "US", "\uD83C\uDDFA\uD83C\uDDF8")
            else -> Quad("Amérique du Nord", "Amérique du Nord", "US", "\uD83C\uDDFA\uD83C\uDDF8")
        }
        return PhoneLocalityInfo(
            normalizedNumber = norm,
            countryName = country,
            countryIso = iso,
            flagEmoji = flag,
            lineType = "Standard",
            localityHint = city,
            operatorHint = "",
            cityName = city
        )
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: String)
}
