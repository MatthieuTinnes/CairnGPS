# CLAUDE.md

Utilise codebase-memory-mcp pour explorer et rechercher dans la codebase

## Vue d'ensemble

Application Android affichant des informations GPS/GNSS en temps réel, avec une
couche de **gamification** (succès, records) pour la rendre ludique.

Public visé : usage en extérieur (randonnée, déplacements). L'app doit rester
lisible en plein soleil et économe en batterie.
L'app fonctionne en theme sombre et clair.
Ne pas lancer l'appli après les modif, uniquement vérifier que le build gradle passe. 
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

Deux mécanismes complémentaires, chacun pour un usage précis :

- **Room** : LA base de données de l'app pour toutes les **données métier persistées**
    - Base unique `AppDatabase` (singleton, construite via `AppDatabase.getInstance`), exposée
      par `CairnApplication`. Nom de fichier : `cairn.db`.
    - Une entité par table (`@Entity`), un DAO par entité (méthodes `suspend` pour les écritures,
      `Flow` pour les lectures observables), et un `Repository` par domaine qui expose le DAO.
      Les ViewModels/Composables ne touchent jamais au DAO ni à `AppDatabase` directement.
    - Ajouter une nouvelle feature persistée = ajouter son entité + DAO à `AppDatabase`, incrémenter
      `version` et fournir une migration. Ne pas créer de base séparée.
    - Processeur d'annotations : **KSP** (pas kapt).
- **DataStore (Preferences)** : réservé aux **préférences utilisateur** simples (ex. format des
  coordonnées), pas aux données métier.


## Qualité

- Gérer les cas limites : absence de fix, permission refusée, capteur indisponible.
- Commenter uniquement ce qui n'est pas évident (choix GPS_PROVIDER, calculs de
  dénivelé, conversions). Ne pas ajouter de commentaire en cas modif pour correction
