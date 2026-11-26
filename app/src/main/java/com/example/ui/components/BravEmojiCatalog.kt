package com.example.ui.components

import org.json.JSONArray

data class BravEmojiEntry(
    val baseEmoji: String,
    val keywords: String,
    val supportsSkinTone: Boolean = false
)

enum class BravEmojiCategory(
    val id: String,
    val title: String,
    val iconEmoji: String,
    val items: List<BravEmojiEntry>
) {
    FACES_EMOTIONS(
        id = "faces_emotions",
        title = "Visages & émotions",
        iconEmoji = "😊",
        items = listOf(
            BravEmojiEntry("😀", "sourire heureux joie smile happy"),
            BravEmojiEntry("😁", "grand sourire dents grin"),
            BravEmojiEntry("😂", "eclat rire larmes lol laugh"),
            BravEmojiEntry("🤣", "fou rire rofl hilarious"),
            BravEmojiEntry("😊", "sourire doux chaleureux blush"),
            BravEmojiEntry("😇", "ange gentil innocent halo"),
            BravEmojiEntry("🙂", "leger sourire ok"),
            BravEmojiEntry("😉", "clin d oeil wink"),
            BravEmojiEntry("😌", "serenite calme apaise relieved"),
            BravEmojiEntry("😍", "admiration yeux coeur love"),
            BravEmojiEntry("🥰", "affection tendresse coeurs"),
            BravEmojiEntry("😘", "bisou baiser kiss"),
            BravEmojiEntry("😋", "miam gourmand yum"),
            BravEmojiEntry("😜", "taquin langue wink tongue"),
            BravEmojiEntry("🤪", "delirant fou zany"),
            BravEmojiEntry("😎", "charisme lunettes cool boss"),
            BravEmojiEntry("🤩", "emerveille etoiles starstruck"),
            BravEmojiEntry("🥳", "fete celebration party"),
            BravEmojiEntry("🥹", "touche emu larmes joie"),
            BravEmojiEntry("🤔", "reflexion pensee think"),
            BravEmojiEntry("🤨", "doute sourcil sceptique"),
            BravEmojiEntry("😮", "etonnement surpris wow"),
            BravEmojiEntry("🤯", "stupefait esprit souffle mindblown"),
            BravEmojiEntry("😱", "choc saisissant peur"),
            BravEmojiEntry("😢", "tristesse larme sad"),
            BravEmojiEntry("😭", "pleurs grande emotion cry"),
            BravEmojiEntry("😤", "determination fierte"),
            BravEmojiEntry("😡", "colere fache angry"),
            BravEmojiEntry("🫡", "salut respect honneur"),
            BravEmojiEntry("🫠", "charme fondu melting")
        )
    ),
    PEOPLE_GESTURES(
        id = "people_gestures",
        title = "Personnes & gestes",
        iconEmoji = "🤝",
        items = listOf(
            BravEmojiEntry("👋", "bonjour salut coucou wave", supportsSkinTone = true),
            BravEmojiEntry("🤚", "main levee stop", supportsSkinTone = true),
            BravEmojiEntry("🖐️", "cinq main ouverte", supportsSkinTone = true),
            BravEmojiEntry("✋", "pause main", supportsSkinTone = true),
            BravEmojiEntry("👌", "parfait impeccable ok", supportsSkinTone = true),
            BravEmojiEntry("🤌", "precis geste italien chef", supportsSkinTone = true),
            BravEmojiEntry("✌️", "paix victoire peace", supportsSkinTone = true),
            BravEmojiEntry("🤞", "chance doigts croises", supportsSkinTone = true),
            BravEmojiEntry("🫰", "coeur doigt snap", supportsSkinTone = true),
            BravEmojiEntry("🤟", "amour rock signe", supportsSkinTone = true),
            BravEmojiEntry("👍", "pouce approuve oui ok bravo", supportsSkinTone = true),
            BravEmojiEntry("👎", "pouce bas non", supportsSkinTone = true),
            BravEmojiEntry("✊", "unite force soutien poing", supportsSkinTone = true),
            BravEmojiEntry("👊", "check poing force", supportsSkinTone = true),
            BravEmojiEntry("👏", "applaudir bravo felicitations", supportsSkinTone = true),
            BravEmojiEntry("🙌", "celebration mains levees", supportsSkinTone = true),
            BravEmojiEntry("🫶", "mains coeur amour", supportsSkinTone = true),
            BravEmojiEntry("🤝", "poignee main accord partenariat handshake", supportsSkinTone = false),
            BravEmojiEntry("🙏", "merci gratitude priere yako respect", supportsSkinTone = true),
            BravEmojiEntry("💪", "force courage muscle", supportsSkinTone = true),
            BravEmojiEntry("✍️", "ecrire signature", supportsSkinTone = true),
            BravEmojiEntry("👑", "couronne roi reine prestige crown", supportsSkinTone = false),
            BravEmojiEntry("👸", "reine princesse couronne", supportsSkinTone = true),
            BravEmojiEntry("🤴", "prince roi couronne", supportsSkinTone = true),
            BravEmojiEntry("💃", "danse joie fete", supportsSkinTone = true),
            BravEmojiEntry("🕺", "danseur ambiance", supportsSkinTone = true)
        )
    ),
    LOVE_RELATIONSHIPS(
        id = "love_relationships",
        title = "Amour & relations",
        iconEmoji = "❤️",
        items = listOf(
            BravEmojiEntry("❤️", "coeur rouge amour love"),
            BravEmojiEntry("🧡", "coeur orange chaleur"),
            BravEmojiEntry("💛", "coeur or amitie"),
            BravEmojiEntry("💚", "coeur vert espoir"),
            BravEmojiEntry("💙", "coeur bleu confiance"),
            BravEmojiEntry("💜", "coeur violet brav"),
            BravEmojiEntry("🖤", "coeur noir chic"),
            BravEmojiEntry("🤍", "coeur blanc pur"),
            BravEmojiEntry("🤎", "coeur marron terre"),
            BravEmojiEntry("💖", "coeur etincelant"),
            BravEmojiEntry("💗", "coeur battant"),
            BravEmojiEntry("💓", "battement coeur"),
            BravEmojiEntry("💞", "coeurs tournants"),
            BravEmojiEntry("💕", "deux coeurs tendresse"),
            BravEmojiEntry("💘", "cupidon fleche amour"),
            BravEmojiEntry("💝", "cadeau coeur ruban"),
            BravEmojiEntry("❤️‍🔥", "passion flamme coeur"),
            BravEmojiEntry("💋", "bisou levres"),
            BravEmojiEntry("💍", "bague mariage alliance"),
            BravEmojiEntry("💐", "bouquet fleurs amour"),
            BravEmojiEntry("🌹", "rose fleur romantique"),
            BravEmojiEntry("🫂", "accolade calin soutien")
        )
    ),
    ANIMALS_NATURE(
        id = "animals_nature",
        title = "Animaux & nature",
        iconEmoji = "🦁",
        items = listOf(
            BravEmojiEntry("🦁", "lion roi savane afrique"),
            BravEmojiEntry("🐘", "elephant cote d ivoire force"),
            BravEmojiEntry("🐆", "leopard panthere vitesse"),
            BravEmojiEntry("🦒", "girafe savane elegance"),
            BravEmojiEntry("🦓", "zebre afrique"),
            BravEmojiEntry("🦅", "aigle vision hauteur"),
            BravEmojiEntry("🦋", "papillon beaute"),
            BravEmojiEntry("🐝", "abeille travail"),
            BravEmojiEntry("🌴", "palmier plage cocotier"),
            BravEmojiEntry("🌳", "baobab arbre nature"),
            BravEmojiEntry("🌺", "hibiscus fleur tropicale"),
            BravEmojiEntry("🌻", "tournesol soleil"),
            BravEmojiEntry("🌞", "soleil radieux"),
            BravEmojiEntry("🌙", "lune nuit croissant"),
            BravEmojiEntry("⭐", "etoile brillant"),
            BravEmojiEntry("🌟", "etoile phare succes"),
            BravEmojiEntry("✨", "etincelles eclat magie"),
            BravEmojiEntry("🔥", "feu flamme brillant"),
            BravEmojiEntry("🌊", "vague ocean mer"),
            BravEmojiEntry("🌈", "arc en ciel espoir")
        )
    ),
    FOOD_DRINK(
        id = "food_drink",
        title = "Nourriture & boissons",
        iconEmoji = "🍲",
        items = listOf(
            BravEmojiEntry("🍲", "garba alloco plat chaud repas"),
            BravEmojiEntry("🍛", "riz sauce thieboudienne yassa"),
            BravEmojiEntry("🍗", "poulet braise grille"),
            BravEmojiEntry("🍖", "viande grillade"),
            BravEmojiEntry("🐟", "poisson braise"),
            BravEmojiEntry("🍌", "banane plantain alloco"),
            BravEmojiEntry("🥭", "mangue fruit"),
            BravEmojiEntry("🍍", "ananas fruit"),
            BravEmojiEntry("🥥", "noix de coco"),
            BravEmojiEntry("🍉", "pasteque fraiche"),
            BravEmojiEntry("🥑", "avocat"),
            BravEmojiEntry("🥐", "croissant petit dejeuner"),
            BravEmojiEntry("☕", "cafe chaud matin"),
            BravEmojiEntry("🍵", "the ataya boisson"),
            BravEmojiEntry("🥂", "champagne toast celebration"),
            BravEmojiEntry("🍾", "bouteille champagne fete"),
            BravEmojiEntry("🍹", "cocktail jus bissap"),
            BravEmojiEntry("🍰", "part gateau dessert"),
            BravEmojiEntry("🎂", "gateau anniversaire")
        )
    ),
    TRAVEL_PLACES(
        id = "travel_places",
        title = "Voyages & lieux",
        iconEmoji = "🌍",
        items = listOf(
            BravEmojiEntry("🌍", "afrique terre monde globe"),
            BravEmojiEntry("🗺️", "carte voyage itineraire"),
            BravEmojiEntry("✈️", "avion voyage vol"),
            BravEmojiEntry("🛫", "decollage depart"),
            BravEmojiEntry("🛬", "atterrissage arrivee"),
            BravEmojiEntry("🚗", "voiture trajet"),
            BravEmojiEntry("🚕", "taxi woro"),
            BravEmojiEntry("🚆", "train"),
            BravEmojiEntry("🚢", "bateau lagune"),
            BravEmojiEntry("🏙️", "ville plateau abidjan dakar"),
            BravEmojiEntry("🏖️", "plage assinie bassam vacances"),
            BravEmojiEntry("🏔️", "montagne"),
            BravEmojiEntry("🏛️", "monument institution"),
            BravEmojiEntry("🕌", "mosquee"),
            BravEmojiEntry("⛪", "eglise cathedrale"),
            BravEmojiEntry("🏨", "hotel hebergement"),
            BravEmojiEntry("📍", "epingle position localisation")
        )
    ),
    ACTIVITIES_SPORTS(
        id = "activities_sports",
        title = "Activités & sports",
        iconEmoji = "⚽",
        items = listOf(
            BravEmojiEntry("⚽", "football ballon can elephants lions"),
            BravEmojiEntry("🏀", "basket sport"),
            BravEmojiEntry("🏆", "trophee coupe victoire champion"),
            BravEmojiEntry("🥇", "medaille or premier"),
            BravEmojiEntry("🥈", "medaille argent"),
            BravEmojiEntry("🥉", "medaille bronze"),
            BravEmojiEntry("🎯", "cible objectif precision"),
            BravEmojiEntry("🎉", "confettis fete"),
            BravEmojiEntry("🎊", "celebration joie"),
            BravEmojiEntry("🎶", "musique notes coupe decale afrobeat"),
            BravEmojiEntry("🥁", "tam tam djembe rythme"),
            BravEmojiEntry("🎤", "micro chant concert"),
            BravEmojiEntry("🎧", "casque ecoute musique"),
            BravEmojiEntry("🎨", "art peinture design"),
            BravEmojiEntry("🎮", "jeu video gaming"),
            BravEmojiEntry("🎭", "theatre culture")
        )
    ),
    OBJECTS(
        id = "objects",
        title = "Objets",
        iconEmoji = "💎",
        items = listOf(
            BravEmojiEntry("💎", "diamant luxe precieux"),
            BravEmojiEntry("📱", "smartphone telephone"),
            BravEmojiEntry("💻", "ordinateur travail"),
            BravEmojiEntry("📸", "appareil photo image"),
            BravEmojiEntry("🎬", "video film cinema"),
            BravEmojiEntry("🎙️", "microphone studio vocal"),
            BravEmojiEntry("📞", "appel telephone"),
            BravEmojiEntry("✉️", "message lettre courrier"),
            BravEmojiEntry("💼", "mallette travail affaires"),
            BravEmojiEntry("📚", "livres etudes savoir"),
            BravEmojiEntry("🖋️", "stylo plume ecriture"),
            BravEmojiEntry("💡", "idee ampoule inspiration"),
            BravEmojiEntry("🎁", "cadeau present"),
            BravEmojiEntry("🛍️", "achats boutique shopping"),
            BravEmojiEntry("🔐", "securite cadenas chiffre"),
            BravEmojiEntry("🔑", "cle acces"),
            BravEmojiEntry("🕰️", "horloge temps"),
            BravEmojiEntry("💰", "argent cfa succes")
        )
    ),
    SYMBOLS(
        id = "symbols",
        title = "Symboles",
        iconEmoji = "✨",
        items = listOf(
            BravEmojiEntry("💯", "cent pour cent parfait"),
            BravEmojiEntry("✅", "valide coche oui"),
            BravEmojiEntry("☑️", "case validee"),
            BravEmojiEntry("🚀", "fusee lancement croissance"),
            BravEmojiEntry("⚡", "eclair energie rapide"),
            BravEmojiEntry("⚜️", "fleur de lys noblesse"),
            BravEmojiEntry("♾️", "infini toujours"),
            BravEmojiEntry("☮️", "paix harmonie"),
            BravEmojiEntry("❗", "important attention"),
            BravEmojiEntry("❓", "question"),
            BravEmojiEntry("💬", "discussion bulle message"),
            BravEmojiEntry("🔔", "notification alerte cloche"),
            BravEmojiEntry("🛡️", "bouclier protection securite"),
            BravEmojiEntry("⚖️", "justice balance"),
            BravEmojiEntry("📈", "hausse progression"),
            BravEmojiEntry("🔝", "top meilleur"),
            BravEmojiEntry("🆗", "ok d accord"),
            BravEmojiEntry("🆕", "nouveau")
        )
    ),
    FLAGS(
        id = "flags",
        title = "Drapeaux",
        iconEmoji = "🇨🇮",
        items = listOf(
            BravEmojiEntry("🇨🇮", "cote d ivoire ci abidjan"),
            BravEmojiEntry("🇸🇳", "senegal sn dakar"),
            BravEmojiEntry("🇨🇲", "cameroun cm yaounde douala"),
            BravEmojiEntry("🇲🇱", "mali ml bamako"),
            BravEmojiEntry("🇧🇫", "burkina faso bf ouagadougou"),
            BravEmojiEntry("🇬🇳", "guinee gn conakry"),
            BravEmojiEntry("🇹🇬", "togo tg lome"),
            BravEmojiEntry("🇧🇯", "benin bj cotonou"),
            BravEmojiEntry("🇳🇪", "niger ne niamey"),
            BravEmojiEntry("🇨🇩", "rd congo kinshasa"),
            BravEmojiEntry("🇨🇬", "congo brazzaville"),
            BravEmojiEntry("🇬🇦", "gabon ga libreville"),
            BravEmojiEntry("🇬🇭", "ghana gh accra"),
            BravEmojiEntry("🇳🇬", "nigeria ng lagos abuja"),
            BravEmojiEntry("🇲🇦", "maroc ma casablanca rabat"),
            BravEmojiEntry("🇹🇳", "tunisie tn tunis"),
            BravEmojiEntry("🇩🇿", "algerie dz alger"),
            BravEmojiEntry("🇿🇦", "afrique du sud za"),
            BravEmojiEntry("🇰🇪", "kenya ke nairobi"),
            BravEmojiEntry("🇪🇹", "ethiopie et addis"),
            BravEmojiEntry("🇫🇷", "france fr paris"),
            BravEmojiEntry("🇨🇦", "canada ca"),
            BravEmojiEntry("🇺🇸", "etats unis usa"),
            BravEmojiEntry("🇬🇧", "royaume uni uk")
        )
    )
}

object BravEmojiCatalog {
    val skinTones: List<Pair<String, String>> = listOf(
        "" to "Défaut",
        "\uD83C\uDFFB" to "Clair",
        "\uD83C\uDFFC" to "Moyen-clair",
        "\uD83C\uDFFD" to "Mat",
        "\uD83C\uDFFE" to "Foncé",
        "\uD83C\uDFFF" to "Très foncé"
    )

    val contextualSuggestions: List<BravEmojiEntry> = listOf(
        BravEmojiEntry("👑", "couronne excellence roi reine"),
        BravEmojiEntry("💎", "diamant luxe"),
        BravEmojiEntry("❤️", "amour coeur"),
        BravEmojiEntry("🔥", "feu brillant"),
        BravEmojiEntry("🙏", "merci respect", supportsSkinTone = true),
        BravEmojiEntry("👏", "bravo applaudissement", supportsSkinTone = true),
        BravEmojiEntry("😂", "rire"),
        BravEmojiEntry("✨", "etincelles"),
        BravEmojiEntry("🇨🇮", "cote d ivoire"),
        BravEmojiEntry("🌍", "afrique monde"),
        BravEmojiEntry("🤝", "accord"),
        BravEmojiEntry("🎉", "fete")
    )

    fun applySkinTone(emoji: String, toneModifier: String, supportsSkinTone: Boolean): String {
        if (!supportsSkinTone || toneModifier.isBlank()) return emoji
        val clean = emoji.replace("\uFE0F", "")
        return "$clean$toneModifier"
    }

    fun search(query: String): List<BravEmojiEntry> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return BravEmojiCategory.entries
            .flatMap { it.items }
            .filter { entry ->
                entry.keywords.lowercase().contains(q) || entry.baseEmoji == q
            }
            .distinctBy { it.baseEmoji }
    }

    fun parseListJson(raw: String): List<String> = runCatching {
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }.getOrDefault(emptyList())
}
