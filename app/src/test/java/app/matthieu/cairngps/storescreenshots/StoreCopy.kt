package app.matthieu.cairngps.storescreenshots

/**
 * Marketing copy of the store screenshots. Headlines are split in two so the second half can be
 * set in italic, like the website's titles. Figures must match the app (and the website, which
 * quotes the same ones): 6 constellations, 66 achievements.
 */
data class Headline(val plain: String, val accent: String, val accentFirst: Boolean = false)

data class FeatureCopy(val eyebrow: String, val headline: Headline, val subtitle: String)

data class Fact(val value: String, val label: String)

data class ClosingCopy(val headline: Headline, val facts: List<Fact>, val footer: String)

data class BannerCopy(val headline: Headline, val tagline: String)

data class StoreCopy(
    val position: FeatureCopy,
    val satellites: FeatureCopy,
    val tracks: FeatureCopy,
    val compass: FeatureCopy,
    val achievements: FeatureCopy,
    val interference: FeatureCopy,
    val closing: ClosingCopy,
    val banner: BannerCopy,
)

val FrenchCopy = StoreCopy(
    position = FeatureCopy(
        eyebrow = "01 · Position",
        headline = Headline("Vous êtes ici,\n", "à 3 mètres près."),
        subtitle = "Un code couleur lisible même en plein soleil.",
    ),
    satellites = FeatureCopy(
        eyebrow = "02 · Satellites",
        headline = Headline("Les satellites\n", "au-dessus de vous."),
        subtitle = "Globe 3D, carte du ciel et force de chaque signal.",
    ),
    tracks = FeatureCopy(
        eyebrow = "03 · Traces",
        headline = Headline("Rangez le téléphone,\n", "il garde la trace."),
        subtitle = "Distance, dénivelé et profil, même écran éteint.",
    ),
    compass = FeatureCopy(
        eyebrow = "04 · Boussole",
        headline = Headline("Retour à la voiture,\n", "ou cap sur le refuge."),
        subtitle = "Flèche, distance et cap jusqu’à vos repères.",
    ),
    achievements = FeatureCopy(
        eyebrow = "05 · Succès",
        headline = Headline(" à aller chercher.", "66 succès", accentFirst = true),
        subtitle = "Sommets, records, séries, et de l’XP à chaque sortie.",
    ),
    interference = FeatureCopy(
        eyebrow = "06 · Interférences",
        headline = Headline("Faux signal,\n", "vraie alerte."),
        subtitle = "CairnGPS repère le brouillage et le leurrage GNSS.",
    ),
    closing = ClosingCopy(
        headline = Headline("Emportez le ciel ", "avec vous."),
        facts = listOf(
            Fact("6", "constellations"),
            Fact("66", "succès"),
            Fact("0", "compte, pub ou traqueur"),
            Fact("100 %", "hors ligne"),
        ),
        footer = "Gratuit · Open source · Android 8+",
    ),
    banner = BannerCopy(
        headline = Headline("Vos données GPS et satellites ", "en direct."),
        tagline = "GNSS · Traces · Boussole · Succès",
    ),
)

val EnglishCopy = StoreCopy(
    position = FeatureCopy(
        eyebrow = "01 · Position",
        headline = Headline("You are here,\n", "give or take 3 m."),
        subtitle = "A color code you can read in full sunlight.",
    ),
    satellites = FeatureCopy(
        eyebrow = "02 · Satellites",
        headline = Headline("The satellites\n", "right above you."),
        subtitle = "3D globe, sky plot and the strength of every signal.",
    ),
    tracks = FeatureCopy(
        eyebrow = "03 · Tracks",
        headline = Headline("Pocket your phone,\n", "it keeps the track."),
        subtitle = "Distance, climb and profile, even with the screen off.",
    ),
    compass = FeatureCopy(
        eyebrow = "04 · Compass",
        headline = Headline("Back to the car,\n", "or on to the hut."),
        subtitle = "Arrow, distance and bearing to your waypoints.",
    ),
    achievements = FeatureCopy(
        eyebrow = "05 · Achievements",
        headline = Headline(" to go after.", "66 achievements", accentFirst = true),
        subtitle = "Summits, records, streaks, and XP on every outing.",
    ),
    interference = FeatureCopy(
        eyebrow = "06 · Interference",
        headline = Headline("Fake signal,\n", "real alert."),
        subtitle = "CairnGPS flags GNSS jamming and spoofing.",
    ),
    closing = ClosingCopy(
        headline = Headline("Take the sky ", "with you."),
        facts = listOf(
            Fact("6", "constellations"),
            Fact("66", "achievements"),
            Fact("0", "accounts, ads or trackers"),
            Fact("100%", "offline"),
        ),
        footer = "Free · Open source · Android 8+",
    ),
    banner = BannerCopy(
        headline = Headline("Your GPS and satellite data, ", "live."),
        tagline = "GNSS · Tracks · Compass · Achievements",
    ),
)
