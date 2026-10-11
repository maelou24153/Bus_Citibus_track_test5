# Cadence — carnet de partitions pour Android (téléphone et tablette)

Une application de lecture de partitions et de grilles d'accords pour musiciens, pensée pour la scène,
avec une interface moderne et claire (thèmes Clair, Sombre et « Scène » en noir pur).

## Fonctions

- **Bibliothèque** : recherche (titre, artiste, tags, paroles), tri A–Z / artiste / récents, favoris, tags.
- **Import** : fichiers ChordPro, texte (accords au-dessus des paroles convertis automatiquement) et PDF.
- **Lecture** : accords au-dessus des paroles, sections, refrains, 1 à 3 colonnes, taille du texte réglable.
- **Transposition**, **capo** (avec « formes » jouées), notation **Nashville**.
- **Défilement automatique** à vitesse réglable, **métronome** (tempo et mesure du morceau), tap tempo.
- **Diagrammes d'accords** guitare et ukulélé : touchez un accord dans la grille.
- **Sets** (listes de concert) réordonnables, lecture enchaînée avec boutons précédent / suivant.
- **Pédale Bluetooth / clavier** : pages suivante et précédente (PageUp/PageDown, flèches, espace).
- **PDF** : affichage page par page, inversion des couleurs en thème sombre.
- **Éditeur** : accords rapides selon la tonalité, sections, conversion « accords au-dessus », PDF joint.
- **Sauvegarde / restauration** JSON, partage ChordPro.
- **Tablette** : rail de navigation et liste + lecture côte à côte.
- Écran maintenu allumé pendant la lecture.
- **Volet masquable** (tablette) : bouton plein écran dans la barre de lecture, ou touche Retour pour le ré-afficher.
- **Transfert Wi-Fi** : Réglages → Transfert Wi-Fi. Ouvrez l'adresse affichée sur un PC du même réseau, saisissez le code
  à 4 chiffres, puis ajoutez, modifiez ou supprimez des morceaux et joignez des PDF (glisser-déposer possible).
- **Import depuis un site** : menu « + » → « Importer depuis un site » (Ultimate Guitar, La Boîte à chansons ou toute page
  d'accords). Importez la page, ou sélectionnez le texte de la chanson puis « Importer la sélection ».
- **Écouter** : bouton casque dans la barre de lecture → Spotify (lecture directe si l'appli est installée) ou YouTube.
- **Diagrammes** guitare, ukulélé **et piano** en touchant un accord.
- **PDF** : menu « + » → « Ajouter un PDF », ou depuis le PC.

## Nouveautés de cette version

- **Serveur 1 · Administration** (port 8080, code à 4 chiffres) : modifier les morceaux, **créer et réorganiser des sets**,
  joindre PDF et partitions MuseScore, **transposer tous les accords**, dupliquer, favoris, export `.cho`, sauvegarde complète.
- **Serveur 2 · Lecteur** (port 8090, lecture seule, code facultatif) : lire morceaux, sets, solfège et PDF depuis un autre
  PC ou téléphone ; transposition, notation (A B C / Do Ré Mi / Nashville), taille du texte, thème et couleur ; se met à jour seul.
- **Notation des accords en solfège** (Do, Ré, Mi… : `Am` → `Lam`) dans Réglages et dans le panneau Affichage.
- **Synchronisation automatique** entre appareils du même Wi-Fi (découverte UDP + échange HTTP, code commun) :
  morceaux, partitions, PDF, sets et suppressions ; le plus récent l'emporte.
- **Partitions MuseScore** (`.mscz` / `.mscx`, MuseScore 3 et 4) : notes en **solfège** (Do fixe ou mobile), accords,
  paroles, transposition, et **lecture avec 8 instruments** synthétisés (piano, guitare, orgue, flûte, violon, marimba,
  basse, trompette), choix par portée, vitesse réglable, lecture à partir d'une mesure.
  Les paroles et accords de la partition sont aussi convertis en grille ChordPro.
- **Apparence** : couleur de l'application, du fond, du texte et des accords.

Limites connues : reprises, nuances et ornements MuseScore ignorés ; lecture limitée à 7 minutes ; synchronisation et
serveurs actifs seulement quand l'appli est ouverte ; les horloges des appareils doivent être à peu près à l'heure.

## Obtenir l'APK sans Android Studio (GitHub)

1. Créez un dépôt GitHub et envoyez-y tout le contenu de ce dossier.
2. Onglet **Actions** → « Compiler l'APK » → **Run workflow** (ou simplement faites un push).
3. Quand c'est vert, ouvrez l'exécution et téléchargez l'artefact **cadence-apk** : c'est `app-debug.apk`.
4. Copiez-le sur le téléphone ou la tablette et installez-le (autoriser les sources inconnues).

## Avec Android Studio

Ouvrez ce dossier, laissez Gradle synchroniser, puis **Run** ou *Build → Build APK(s)*.

## Technique

Kotlin, Jetpack Compose (Material 3), Room, DataStore. Android 8.0+ (API 26). Aucune connexion Internet requise.
