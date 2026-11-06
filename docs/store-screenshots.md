# Captures store — génération automatique

Les captures d'écran du store (Google Play, F-Droid) et la bannière *feature graphic* sont
générées par un test Robolectric, sans émulateur ni appareil, directement dans
`fastlane/metadata/android/<locale>/images/` :

```bash
./gradlew :app:testDebugUnitTest -PstoreScreenshots
```

Sans `-PstoreScreenshots`, ce test est exclu de `./gradlew test` : il est lent (quelques minutes)
et réécrit les fichiers de `fastlane/`.

## Ce qui est généré

| Fichier | Taille |
|---|---|
| `phoneScreenshots/1.png` … `7.png` | 1080 × 2160 |
| `sevenInchScreenshots/1.png` … `7.png` | 1200 × 1920 |
| `tenInchScreenshots/1.png` … `7.png` | 1600 × 2560 |
| `featureGraphic.png` | 1024 × 500 |

Pour `fr-FR` et `en-US`, soit 44 images. Toutes respectent la limite de ratio 2:1 de Google Play.

## Design

Le style reprend celui du site (`cairngps-website`) : fonds forêt `#0d3b30` et crème `#f3ebdd`
en alternance, titres en Fraunces avec la fin en italique (pêche sur forêt, argile sur crème),
surtitre numéroté en Roboto Mono, courbes de niveau du bandeau d'accueil, téléphones coupés en bas
de l'image. Les tablettes n'affichent qu'un appareil, plus grand.

Les écrans sont les **vrais écrans de l'application** (`MainScaffold`), alimentés par les mêmes
données fictives que la visite guidée et le mode démo (`demo/`), avec l'horloge figée à un instant
fixe : position, ciel et cap sont identiques d'une génération à l'autre. La barre d'état est
dessinée (12:00, batterie pleine, aucune notification).

## Où modifier quoi

Tout est dans `app/src/test/java/app/matthieu/cairngps/storescreenshots/` :

- `StoreCopy.kt` — les accroches, surtitres et chiffres, en français et en anglais. Les chiffres
  (6 constellations, 66 succès) doivent rester exacts et cohérents avec le site.
- `StoreScreenshotsTest.kt` — l'ordre des captures, l'écran affiché dans chacune, son thème
  (clair ou sombre), le fond et la position des courbes de niveau.
- `StoreFrame.kt` — la mise en page : cadre, typographie, appareil, barre d'état, bannière.
- `StoreContainer.kt` — les données fictives ; l'alerte de leurrage de la capture 6 vient d'un
  décalage d'une heure entre l'heure GNSS simulée et l'heure réseau.

Les polices Fraunces et Manrope (`app/src/test/resources/store-fonts/`, licence OFL) ne servent
qu'au générateur et ne sont pas incluses dans l'APK.
