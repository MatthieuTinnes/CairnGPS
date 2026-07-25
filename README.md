# CairnGPS

![Logo CairnGPS](app/src/main/ic_launcher-playstore.png)

Application Android affichant des informations GPS/GNSS en temps réel, avec une
couche de gamification (succès, records, niveaux) pour la rendre ludique.

Conçue pour un usage en extérieur (randonnée, déplacements) : lisible en plein
soleil, économe en batterie, et fonctionnelle sans connexion réseau.

## Fonctionnalités

- **Position** : coordonnées GPS (décimales et DMS), altitude, vitesse,
  précision horizontale/verticale, mises à jour en temps réel.
- **Boussole** : cap magnétique et vrai (déclinaison calculée localement),
  navigation vers un waypoint.
- **Satellites** : statut GNSS brut (`GnssStatus`) — satellites vus/utilisés,
  ciel en 2D (sky plot) et en 3D, infos sur les constellations (GPS, GLONASS,
  Galileo, BeiDou, QZSS, IRNSS).
- **Carnet** : waypoints (avec icônes) et traces enregistrées (sessions),
  profils d'altitude et de vitesse, export GPX.
- **Enregistrement de trace** : suivi en arrière-plan via un service au premier
  plan (distance, durée, vitesse moyenne/max, dénivelé D+/D−).
- **Gamification** : succès à débloquer, records personnels, niveaux basés sur
  l'XP cumulé.
- **Sauvegarde/restauration** : export/import de toutes les données de l'app
  dans un seul fichier.
- Thèmes clair et sombre, français et anglais.

## Stack technique

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Architecture MVVM** : `Repository` (accès aux données) → `ViewModel`
  (état exposé via `StateFlow`) → `Composable` (affichage, sans logique métier)
- **Coroutines + Flow** pour l'asynchrone
- **Room** (via KSP) pour les données métier persistées (waypoints, traces,
  succès, records), **DataStore Preferences** pour les réglages utilisateur
- **LocationManager / `GPS_PROVIDER`** — volontairement pas FusedLocationProvider,
  car l'app a besoin des données brutes satellites (`GnssStatus`)
- minSdk 26 · targetSdk 37 · compileSdk 37

## Prérequis

- Android Studio récent (AGP 9.3, Kotlin 2.4)
- JDK 17+ (le JBR fourni avec Android Studio convient)
- Un appareil ou émulateur avec puce GPS pour tester les fonctionnalités de
  localisation (un émulateur peut simuler une position/route fictive)

## Build

```bash
./gradlew assembleDebug
```

Sous Windows (PowerShell), s'assurer que `JAVA_HOME` pointe vers le JBR
d'Android Studio avant d'appeler `gradlew` :

```powershell
$env:JAVA_HOME = "<chemin vers Android Studio>\jbr"
.\gradlew.bat assembleDebug
```

## Tests

```bash
./gradlew test
```

## Structure du projet

```
app/src/main/java/app/matthieu/cairngps/
├── data/         Repositories, entités Room, DAOs, modèles de données
├── domain/       Logique métier pure (géodésie, formatage, gamification)
├── service/      Service au premier plan pour l'enregistrement de traces
└── ui/           Écrans Compose et ViewModels, par fonctionnalité
    ├── location/     Écran Position (accueil)
    ├── compass/      Boussole
    ├── satellites/   Statut GNSS, globe 3D, infos constellations
    ├── history/      Carnet (waypoints, traces)
    ├── waypoints/
    ├── gamification/ Succès, records, niveaux
    ├── profile/      Hub profil
    ├── settings/
    └── permission/   Écran de demande de permission de localisation
```

La base Room (`AppDatabase`, fichier `cairn.db`) et les repositories sont
instanciés une seule fois et exposés par `CairnApplication`.
