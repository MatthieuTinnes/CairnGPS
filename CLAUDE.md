# CLAUDE.md

Guide de développement pour Claude Code. À lire avant toute modification du code.

## Vue d'ensemble

Application Android affichant des informations GPS/GNSS en temps réel, avec une
couche de **gamification** (succès, records) pour la rendre ludique.

Public visé : usage en extérieur (randonnée, déplacements). L'app doit rester
lisible en plein soleil et économe en batterie.

## Stack technique

- **Langage** : Kotlin
- **UI** : Jetpack Compose + Material 3
- **Thème** : sombre par défaut
- **Architecture** : MVVM
    - `Repository` : accès aux sources de données (GPS, capteurs, stockage)
    - `ViewModel` : expose l'état via `StateFlow`, ne connaît pas Android UI
    - `Composable` : observe les `StateFlow`, sans logique métier
- **minSdk** : 26 — **targetSdk** : dernière version stable
- **Asynchrone** : Coroutines + Flow. Pas de callback exposé hors des Repository.

## Contraintes GPS — IMPORTANTES

- **Utiliser `LocationManager` avec `GPS_PROVIDER`**, PAS FusedLocationProvider.
  Raison : l'app a besoin des données brutes des satellites (`GnssStatus`), que
  Fused n'expose pas. Ne jamais remplacer par Fused, même si ça paraît plus simple.
- Toujours passer par le `LocationRepository` pour les positions ; aucun accès
  direct au `LocationManager` depuis un ViewModel ou un Composable.
- **Cycle de vie** : l'écoute GPS et capteurs démarre en `onStart` et s'arrête en
  `onStop`. Rien ne doit continuer à écouter quand l'écran n'est pas visible
  (sauf le foreground service dédié, prévu plus tard).

## Permissions

- `ACCESS_FINE_LOCATION` : demande runtime, avec écran d'explication si refusée.
- Déclarer chaque permission dans le Manifest au moment où la feature l'introduit,
  pas avant.

## Modèle de données

`LocationData` (data class centrale) :
- `latitude`, `longitude` (Double)
- `altitude` en mètres
- `vitesse` en **m/s** (source) — la conversion km/h se fait à l'affichage
- `precisionHorizontale` en mètres
- `precisionVerticale` en mètres si disponible
- `timestamp`

## Conventions d'affichage

- Coordonnées : degrés décimaux (6 décimales) ET degrés/minutes/secondes.
- Vitesse affichée en km/h (1 décimale), m/s en complément discret.
- Altitude : entier, en mètres.
- Précision : indicateur couleur — vert `< 5 m`, orange `5–15 m`, rouge `> 15 m`.
- **Pas de fix disponible** : afficher des tirets `--`, jamais `0`.
- Mises à jour fluides, sans clignotement quand les valeurs changent.

## Stockage

**Phase actuelle : stockage simple.** Utiliser `DataStore` (Preferences).


## Qualité

- Gérer les cas limites : absence de fix, permission refusée, capteur indisponible.
- Commenter uniquement ce qui n'est pas évident (choix GPS_PROVIDER, calculs de
  dénivelé, conversions).