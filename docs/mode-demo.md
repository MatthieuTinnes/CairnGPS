# Mode démo — captures d'écran et vidéos anonymisées

Le mode démo remplace **toutes** les données affichées par des données fictives, pour pouvoir
faire des captures d'écran et des screencasts (F-Droid, README, store) sans jamais exposer sa
position réelle, ses traces ou ses repères.

Il n'existe **que dans les builds debug**. Dans le build release, `DemoMode.isAvailable` vaut
`BuildConfig.DEBUG`, donc `false` : R8 supprime toutes les branches concernées, et le package
`demo` disparaît entièrement de l'APK. Vérifié sur `mapping.txt` — zéro classe `cairngps.demo`
conservée. La reproductibilité du build F-Droid n'est pas affectée.

## Utilisation

1. Installer le build debug (`./gradlew installDebug`).
2. **Réglages → Debug → Mode démo** (section visible uniquement en debug).
3. L'application redémarre : la base et les sources de données sont choisies au démarrage du
   processus, pas à chaud.
4. Faire les captures.
5. Rebasculer l'interrupteur pour revenir aux données réelles — elles n'ont jamais été touchées.

## Ce qui est simulé

| Donnée | En mode démo |
|---|---|
| Position, altitude, vitesse, précision | Marche fictive en boucle, ~4,5 km/h, vers 2400 m, précision ~3 m (indicateur vert) |
| Satellites (`GnssStatus`) | 33 satellites GPS / Galileo / GLONASS / BeiDou / SBAS, dérive lente, ~2/3 utilisés dans le fix |
| Boussole | Cap suivant le sens de marche, précision haute (jamais d'avertissement de calibration) |
| État du GPS | Toujours activé (pas de bandeau « GPS désactivé ») |
| Base de données | Fichier séparé `cairn-demo.db` — `cairn.db` n'est ni lu ni écrit |
| Historique | 10 sessions fictives sur 10 mois, dans des massifs sans rapport entre eux |
| Repères | 16 repères, un par icône du sélecteur |
| Records, succès, niveau, XP | **Non pré-remplis** : `GamificationManager` les recalcule à partir des sessions/repères ci-dessus, donc ils ne peuvent pas contredire les traces affichées |

Toutes les valeurs live sont des fonctions pures de l'horloge : elles bougent de façon continue
(bon pour la vidéo), sans dérive accumulée, et sont identiques d'un lancement à l'autre.

Le jeu de données est calibré pour que les écrans de gamification soient intéressants à
capturer : 3 jours consécutifs pour la série, 6 mois distincts, une session à cheval sur minuit,
une session assez rapide pour la condition « croisière », un point à −19 m et un sommet à
3113 m — tout en **laissant beaucoup de succès verrouillés**, pour que les barres de progression
restent visibles.

## Limite connue

L'enregistrement d'une trace nécessite toujours la permission de localisation réelle, même en
mode démo : le service de premier plan est de type `location`, et Android 14+ refuse de le
démarrer sans `ACCESS_FINE_LOCATION`. Sur un appareil où la permission est accordée,
l'enregistrement fonctionne normalement et enregistre la trace simulée. Le reste de
l'application (écran de permission compris) est accessible sans aucune permission.

## Où c'est implémenté

- `demo/DemoMode.kt` — l'interrupteur, ses `SharedPreferences` et le redémarrage du processus
- `demo/DemoRoute.kt` — la géométrie de boucle partagée entre le live et l'historique
- `demo/DemoGpsSource.kt` — position, satellites et cap simulés
- `demo/DemoDataSeeder.kt` — le jeu de données fictif

Les points de branchement sont volontairement peu nombreux : `LocationRepository`,
`CompassRepository`, `AppDatabase.databaseName()`, `CairnApplication.onCreate`,
`GamificationManager.startLiveTracking` et `LocationPermissionGate`. Aucun ViewModel ni aucun
composable ne sait que le mode démo existe.
