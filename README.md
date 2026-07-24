# WaterFinder Pro

WaterFinder Pro is een Android-app voor motorhomereizigers die waterpartijen in de omgeving willen vinden.

## Fase 1 – v0.1.0-alpha

Deze eerste versie bevat:

- een OpenStreetMap-kaart;
- GPS-locatie via Google Play Services;
- een instelbare zoekradius van 5 tot 100 km;
- zoeken naar meren, rivieren, kanalen, baaien en zee via de Overpass API;
- markers voor de huidige locatie en gevonden waterobjecten;
- een eenvoudige tabletvriendelijke Jetpack Compose-interface.

## Openen in Android Studio

1. Clone of download deze repository.
2. Open de hoofdmap `WaterFinderPro` in een recente Android Studio-versie.
3. Laat Android Studio de Gradle-configuratie synchroniseren.
4. Selecteer een Samsung-tablet, Android-emulator of ander toestel met Android 10 of hoger.
5. Start de app en geef locatietoestemming.
6. Kies een zoekradius en tik op **Zoek water**.

## Techniek

- Kotlin
- Jetpack Compose / Material 3
- osmdroid met OpenStreetMap-kaarten
- Fused Location Provider
- OpenStreetMap Overpass API

## Huidige beperkingen

- De Overpass API kan bij een grote straal traag reageren.
- De resultaten zijn waterobjecten, nog geen gecontroleerde camper- of overnachtingsplaatsen.
- Favorieten, navigatie, weersinformatie, comfortscore en offline kaarten volgen in latere fasen.
- Gebruik de app niet tijdens het rijden en respecteer altijd lokale parkeer- en kampeerregels.
