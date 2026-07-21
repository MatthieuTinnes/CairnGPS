# Succès restant à câbler — 6 stubs verrouillés

Suite à l'implémentation de `succes.md` (72 succès), 6 succès sont déclarés dans
`Achievements.kt` mais ne se débloquent jamais (`oneShotCheck = { false }`, tagués `// STUB`).
Ils restent visibles et verrouillés dans l'écran Succès plutôt qu'absents, pour que le total
affiché (72) soit correct dès maintenant. Ce document liste, pour chacun, ce qu'il reste à
construire.

Le mécanisme de déblocage final est déjà en place pour tous : une fois la condition calculée
quelque part, il suffit soit d'exposer un nouveau champ sur `GamificationMetrics` (pour une
condition scalaire/booléenne dérivée de données déjà persistées), soit de poser un flag via
`GamificationFlagsRepository.set(key)` (pour une condition ponctuelle détectée en direct), puis de
remplacer le `oneShotCheck = { false }` correspondant dans
`app/src/main/java/app/matthieu/cairngps/domain/gamification/Achievements.kt` par le vrai
prédicat. Aucun changement d'architecture n'est nécessaire — seulement la donnée manquante.

---

## 1. `speed_still` — Contemplation

**Condition (spec `SPD_STILL`)** : rester à moins de 1 m/s pendant 10 minutes consécutives,
enregistrement actif.

**Pourquoi c'est un stub** : rien ne suit aujourd'hui la durée d'immobilité *continue* pendant une
session. `RecordingRepository` distingue déjà mobile/immobile via `MOVING_SPEED_THRESHOLD_MS` (0.5
m/s) pour calculer la distance et la vitesse moyenne, mais ne mesure pas le plus long palier
immobile.

**Ce qu'il faut construire** :
- Dans `RecordingRepository` (`app/src/main/java/app/matthieu/cairngps/data/RecordingRepository.kt`),
  ajouter un accumulateur "durée immobile en cours" à côté de `movingDistanceMeters`/`movingTimeMs`
  dans `RecordingCheckpoint` : remonter à zéro dès que la vitesse dépasse le seuil, sinon
  incrémenter, et garder le maximum atteint pendant la session.
- Persister ce maximum sur `Session` (nouvelle colonne, ex. `maxStationaryMs`) → migration Room
  `v7 → v8`.
- Exposer `maxSessionStationaryMs` (ou un booléen `hasStillSession`) sur `GamificationMetrics`,
  calculé dans `Achievements.metricsFrom` à partir des sessions.
- Remplacer le stub par `oneShotCheck = { m -> m.maxSessionStationaryMs >= 600_000L }`.

**Risque** : nécessite une migration Room (seul stub dans ce cas — les 5 autres peuvent
s'implémenter sans toucher au schéma `sessions`).

---

## 2. `waypoints_arrivee` — À bon port

**Condition (spec `WPT_ARRIVEE`)** : rejoindre un repère cible à moins de 10 m via la boussole.

**Pourquoi c'est un stub** : `CompassViewModel`
(`app/src/main/java/app/matthieu/cairngps/ui/compass/CompassViewModel.kt`) calcule déjà
`targetDistanceMeters` (distance au repère visé, via `NavigationTargetRepository` +
`distanceAndBearing`) mais ce résultat n'est jamais transmis à la couche gamification.

**Ce qu'il faut construire** :
- Injecter `GamificationFlagsRepository` dans `CompassViewModel` (même pattern que
  `SessionDetailViewModel`/`SettingsViewModel` : nouveau paramètre constructeur + `factory(...)`
  + mise à jour de `CompassRoute` et de l'appel dans `AppNavigation.kt`).
- Quand `targetDistanceMeters` passe sous 10 m *alors qu'une cible est active*, appeler
  `gamificationFlagsRepository.set("wpt_arrivee")`.
- Remplacer le stub par `oneShotCheck = { m -> "wpt_arrivee" in m.flags }`.

**Risque** : faible — suit exactement le pattern déjà utilisé pour `geo_confluence`/`cmp_decl`
dans `GamificationManager`, juste depuis un autre ViewModel.

---

## 3. `compass_nord` — Cap au nord

**Condition (spec `CMP_NORD`)** : maintenir un cap à moins de 2° du nord pendant 30 s.

**Pourquoi c'est un stub** : c'est une condition à *durée continue*, pas un simple seuil
instantané — il faut un minuteur qui se réarme dès que le cap sort de la fenêtre ±2°, ce qui
n'existe pas encore.

**Ce qu'il faut construire** :
- Dans `CompassViewModel`, à chaque mise à jour de cap filtré (`heading` déjà lissé par le
  filtre passe-bas existant) : si `abs(heading) <= 2f || abs(heading) >= 358f`, démarrer/poursuivre
  un chronomètre (`System.currentTimeMillis()` de référence) ; sinon le réinitialiser.
- Dès que le chronomètre atteint 30 000 ms, appeler `gamificationFlagsRepository.set("cmp_nord")`
  une seule fois.
- Remplacer le stub par `oneShotCheck = { m -> "cmp_nord" in m.flags }`.

**Point d'attention** : le minuteur doit survivre aux micro-interruptions du capteur mais pas aux
changements d'écran/mise en arrière-plan (sinon un cap tenu sur plusieurs sessions distinctes
compterait à tort) — définir clairement la fenêtre de "30 s continues" par rapport au cycle de vie
du ViewModel.

---

## 4. `compass_rose` — Rose des vents

**Condition (spec `CMP_ROSE`)** : parcourir au moins 1 km dans chacune des quatre directions
cardinales (Nord/Sud/Est/Ouest), toutes sorties confondues.

**Pourquoi c'est un stub** : aucune décomposition de la distance parcourue par direction n'existe
aujourd'hui ; `RecordingRepository` n'accumule qu'une distance totale scalaire.

**Ce qu'il faut construire** :
- Décomposer chaque segment de trace (deux fixs consécutifs) en projection Nord/Sud/Est/Ouest
  selon son cap (`Location.bearingTo` ou le calcul déjà utilisé pour `distanceAndBearing`), et
  accumuler quatre totaux cumulatifs à vie (pas juste par session).
- Le plus simple : quatre nouveaux `RecordType` (`NORTH_DISTANCE`, `SOUTH_DISTANCE`,
  `EAST_DISTANCE`, `WEST_DISTANCE`, tous `higherIsBetter = true`) alimentés depuis
  `GamificationManager.submitLiveFix` en comparant chaque fix au précédent — pas de migration Room
  nécessaire (la table `records` existe déjà et stocke par `type`).
- Exposer les 4 valeurs sur `GamificationMetrics`, remplacer le stub par
  `oneShotCheck = { m -> m.northDistanceMeters >= 1000.0 && /* idem S/E/O */ }`.

**Risque** : la décomposition doit filtrer le bruit GPS (comme le fait déjà
`RecordingRepository.MAX_ACCURACY_METERS`) pour éviter d'accumuler de la "fausse" distance quand
l'appareil est immobile mais bruité.

---

## 5. `geo_antipodes` — Aux antipodes

**Condition (spec `GEO_ANTIPODES`)** : avoir été repéré en deux points diamétralement opposés du
globe (± 500 km).

**Pourquoi c'est un stub** : nécessite de retenir *l'ensemble* des positions visitées (pas
seulement les 4 extrêmes actuels type boussole), pour pouvoir ensuite chercher une paire
antipodale — le plus gros morceau des 6 stubs.

**Ce qu'il faut construire** :
- Nouvelle table Room `visited_cells` (`lat: Int`, `lon: Int` — clé composite, une ligne par
  cellule de grille 1° déjà visitée) → migration Room `v7 → v8` (peut être fusionnée avec celle du
  §1 si les deux sont faites ensemble).
- À chaque fix live, insérer `(round(lat), round(lon))` dans cette table (idempotent, comme
  `AchievementDao`/`GamificationFlagDao`).
- Le test d'antipodalité : pour la cellule courante `(lat, lon)`, calculer sa cellule antipodale
  `(-lat, lon + 180 mod 360)` et vérifier si une cellule *déjà visitée* tombe dans un rayon de
  500 km de cette antipode (pas seulement une égalité stricte de cellule, à cause de la marge de
  tolérance — comparer distances réelles entre les quelques cellules candidates plutôt qu'un match
  exact de grille).
- Poser le flag `geo_antipodes` dès qu'une paire est trouvée.

**Risque** : le plus complexe des 6 — table dédiée + logique géométrique de recherche de paire,
à faire tourner sur chaque fix sans dégrader les perfs (la table peut grossir sur des années
d'usage ; prévoir un index ou limiter la recherche aux cellules dans la bonne bande de latitude).

---

## 6. `app_globe` — Vue d'ensemble

**Condition (spec `APP_GLOBE`)** : faire tourner le globe 3D d'un tour complet (360°).

**Pourquoi c'est un stub** : la rotation du globe
(`app/src/main/java/app/matthieu/cairngps/ui/satellites/SatelliteGlobeScreen.kt`, via
`detectTransformGestures` + `rememberSaveable { mutableFloatStateOf(...) }`) est un état de geste
Compose purement local à l'écran — jamais accumulé au-delà de l'angle courant, jamais exposé à un
ViewModel ni persisté.

**Ce qu'il faut construire** :
- Faire remonter la rotation dans `SatelliteGlobeViewModel`
  (`app/src/main/java/app/matthieu/cairngps/ui/satellites/SatelliteGlobeViewModel.kt`, qui n'a
  aujourd'hui aucune notion de rotation) plutôt que de la garder locale à l'écran.
- Accumuler la **valeur absolue** des deltas de rotation à chaque frame de geste (pas l'angle net —
  un aller-retour de 180°+180° doit compter comme 360° parcourus, pas 0°), jusqu'à atteindre 360°
  cumulés sur l'écran (à vie, ou par simple ouverture — à trancher : voir point ouvert ci-dessous).
- Une fois le seuil atteint, appeler `gamificationFlagsRepository.set("app_globe")` (nécessite
  d'injecter `GamificationFlagsRepository` dans `SatelliteGlobeViewModel`, même pattern que les
  autres ViewModels câblés).
- Remplacer le stub par `oneShotCheck = { m -> "app_globe" in m.flags }`.

**Point ouvert** : la spec ne précise pas si les 360° doivent être parcourus en une seule session
d'interaction ou cumulés à vie. Cumuler à vie est plus simple (pas de fenêtre temporelle à gérer)
et plus cohérent avec le reste du catalogue (XP jamais perdu) — recommandé sauf avis contraire.

---

## Résumé des migrations Room nécessaires

| Stub | Nouvelle table/colonne | Migration |
|---|---|---|
| `speed_still` | colonne `Session.maxStationaryMs` | v7 → v8 |
| `geo_antipodes` | table `visited_cells` | v7 → v8 (peut être la même que ci-dessus) |
| `waypoints_arrivee`, `compass_nord`, `app_globe` | aucune (flag existant `gamification_flags`) | — |
| `compass_rose` | aucune (nouveaux `RecordType` dans la table `records` existante) | — |

Quatre des six stubs (`waypoints_arrivee`, `compass_nord`, `compass_rose`, `app_globe`) ne
nécessitent donc **aucune migration** : ils réutilisent les tables `gamification_flags`/`records`
déjà en place et n'ont besoin que de nouveau code d'observation + un appel `set(...)`/`submit(...)`
au bon endroit. Seuls `speed_still` et `geo_antipodes` demandent un nouveau schéma.
