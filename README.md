# Générateur de sorts Gladiatrool

Assistant console destiné aux sorts Gladiatrool de grade 6. Il propose trois modes :

- créer un sort personnalisé de dégâts directs ou de vol de vie ;
- modifier les paramètres maîtrisés d'un sort Gladiatrool existant, vanilla ou personnalisé ;
- supprimer proprement un sort personnalisé créé par le builder.

Les sélections courtes utilisent les flèches `↑` / `↓` et `Entrée`. Les valeurs numériques, le nom et la description utilisent une saisie texte contrôlée.

Le projet cible la structure SQL de l'émulateur Aegnor. Une adaptation peut être nécessaire pour les forks ayant renommé les tables, les colonnes ou les clés de `config.properties`.

## Prérequis

- serveur Aegnor avec la structure MariaDB attendue ;
- Java JDK 11 ou supérieur disponible dans le `PATH` ;
- client contenant le `core.fla` du projet ;
- publication du client avec Flash CS6.

## Installation du builder

1. Cloner ou télécharger ce dépôt dans le dossier de votre choix :

   ```bash
   git clone https://github.com/Charlydcn/dofus-gladiatrool-spell-builder.git
   ```

2. Vérifier que `java -version` utilise Java 11 ou supérieur.
3. Exécuter `build.bat` une première fois.
4. Copier `builder.properties.example` sous le nom `builder.properties`, ou lancer `run.bat` une fois pour effectuer cette copie automatiquement.
5. Adapter les trois chemins dans `builder.properties`.
6. Installer ensuite l'override client décrit ci-dessous.

Le projet utilise le Maven Wrapper (`mvnw.cmd`). Maven n'a pas besoin d'être installé : le wrapper télécharge Maven et les dépendances Java depuis Maven Central lors de la première compilation. Le JAR autonome est généré dans `target/dofus-gladiatrool-spell-builder.jar`.

## Installation client unique

1. Ouvrir `client/resources/app/retroclient/modules/core.fla` dans Flash CS6.
2. Coller le contenu de `client/CustomSpellsOverride.as` dans la frame 1, avec les autres overrides.
3. Publier vers `client/resources/app/retroclient/modules/core.swf`.

Cette publication est nécessaire une seule fois, sauf si `CustomSpellsOverride.as` est ensuite mis à jour. Modifier `builder.properties` ou créer un sort ne nécessite pas de republier `core.swf`.

Les appels AS2 `trace()` sont visibles dans le panneau **Sortie** de Flash CS6 pendant un test depuis l'éditeur. Ils ne sont pas affichés dans la console du jeu lancé normalement.

La validation réelle consiste à créer un sort, redémarrer le serveur et le client, puis vérifier son nom, son icône et ses caractéristiques dans le livre de sorts Gladiatrool.

## Configuration du builder

Le fichier local `builder.properties` se trouve à côté de `run.bat`. Il est ignoré par Git afin d'éviter de publier des chemins ou une configuration personnelle. Sa base est fournie dans `builder.properties.example` :

```properties
server.config.path=../../serveur/game/config.properties
client.customSpells.path=../../client/resources/app/retroclient/custom_spells.json
client.spellPatches.path=../../client/resources/app/retroclient/spell_patches.json
template.iconSpellId=176
template.animationSpellId=103
```

Les chemins peuvent être absolus ou relatifs au dossier du builder. Dans un chemin Windows, utiliser `/` afin d'éviter l'échappement des antislashs dans un fichier Java `.properties`.

### Valeurs configurables

| Propriété | Rôle | Valeur par défaut |
|---|---|---:|
| `server.config.path` | Chemin du `config.properties` du serveur Game. Le builder y lit la connexion MariaDB. | `../../serveur/game/config.properties` |
| `client.customSpells.path` | Fichier client contenant les définitions des sorts créés. | `../../client/resources/app/retroclient/custom_spells.json` |
| `client.spellPatches.path` | Fichier client contenant les paramètres modifiés des sorts existants. | `../../client/resources/app/retroclient/spell_patches.json` |
| `template.iconSpellId` | ID du sort utilisé comme modèle d'icône pour les prochaines créations. | `176` — Flèche Persécutrice |
| `template.animationSpellId` | ID du sort utilisé comme modèle d'animation pour les prochaines créations. | `103` — Chance d'Écaflip |

Les identifiants et mots de passe MariaDB ne sont pas dupliqués dans `builder.properties`. Ils restent dans le `config.properties` du serveur.

### Choix de l'icône

À l'étape de création, le builder propose deux sources :

- **Icône d'un sort existant** : un ID de sort, par exemple `176` ; le builder vérifie que ce sort existe dans `spells` puis reprend sa propriété client `i`.
- **Icône par ID dans `clips/spells/icons/up`** : un ID de fichier tel que `673` pour `clips/spells/icons/up/673.swf`. Le builder vérifie que le fichier `<ID>.swf` existe dans le client configuré.

L'icône directe est enregistrée dans `custom_spells.json`. L'override AS2 fourni doit être republié dans `core.swf` une fois après cette mise à jour ; il reconstruit les propriétés complètes de l'icône et remplace uniquement l'ID du fichier `up`.

Les sorts créés sont explicitement classés comme sorts de classe dans le client, indépendamment du sort choisi comme modèle d'icône. Les effets sans condition n'affichent pas de bouton « Conditions ».

`template.iconSpellId` reste le modèle par défaut pour le premier choix et fournit les métadonnées client lorsqu'une icône directe est utilisée.

### Correspondance de `template.animationSpellId`

La valeur correspond directement à `spells.id` dans la base Game. Pour chaque nouveau sort, le builder lit :

```sql
SELECT sprite, spriteinfo
FROM spells
WHERE id = template.animationSpellId;
```

Les valeurs `spells.sprite` et `spells.spriteinfo` sont copiées dans la nouvelle ligne de `spells`. Il n'est donc pas nécessaire de connaître un ID interne d'animation : il suffit de fournir l'ID d'un sort dont l'animation de lancement convient.

Cette valeur est proposée par défaut pendant chaque création. L'utilisateur peut conserver ce modèle avec `Entrée` ou saisir un autre ID. Le builder vérifie l'existence du sort dans `spells`, affiche son nom et demande confirmation avant de copier son animation.

## Lancement

Exécuter `run.bat`, puis choisir :

```text
Créer un sort
Modifier un sort Gladiatrool existant
Supprimer un sort personnalisé
```

Au démarrage, le programme vérifie :

- la connexion MariaDB ;
- les tables et colonnes nécessaires ;
- les chemins configurés lorsqu'ils sont utilisés.

Pendant une création, chaque ID de sort modèle choisi est vérifié dans `spells`. Un ID absent est refusé et le programme demande une nouvelle valeur.

## Création d'un sort

Le builder crée uniquement le grade 6 et attribue le premier ID libre entre `10000` et `10999`.

Propriétés prises en charge :

- nom et description libre, éventuellement vide pour la description ;
- choix des sorts modèles d'icône et d'animation, avec les valeurs de `builder.properties` proposées par défaut ;
- classe Gladiatrool unique ou sort global aux douze classes ;
- PA, PO minimale et maximale, PO modifiable ;
- lancer en ligne et ligne de vue ;
- CC, EC et fin du tour sur EC ;
- délai de relance, maximum par tour et maximum par cible ;
- cibles : tout le monde, ennemis uniquement ou lanceur uniquement ;
- plusieurs lignes normales et critiques ;
- dégâts directs ou vol de vie ;
- éléments Neutre, Terre, Feu, Eau et Air ;
- ajout sans raccourci ou remplacement réversible d'un sort lié.

Avant l'écriture, un récapitulatif permet de modifier chaque section ou d'annuler. La création écrit dans `spells`, `spells_grade` et `spells_effect`, puis met à jour les liens `full_morphs.spells` et les dispositions existantes `gladiatrool_spells.spells`.

## Modification d'un sort existant

Ce mode accepte un sort vanilla ou personnalisé lié à un morph Gladiatrool. Il modifie uniquement le grade 6 et les colonnes maîtrisées de `spells_grade` :

- `paCost` ;
- `poMin` et `poMax` ;
- `isPoModif` ;
- `isLine` ;
- `needLOS` ;
- `ratioCC` et `ratioEC` ;
- `endTurn` ;
- `CD` ;
- `maxByTurn` et `maxByTarget`.

Les lignes de `spells_effect`, les états et les zones ne sont jamais modifiés par ce mode. Le nom et la description peuvent être remplacés ; le builder met à jour `spells.name` et leur affichage client. L'icône peut être remplacée avec les mêmes deux sources que lors de la création. L'animation peut être copiée depuis un sort modèle ; le builder met alors à jour `spells.sprite` et `spells.spriteinfo`.

Si le même ID est lié à plusieurs classes, le programme affiche toutes les classes concernées. La modification de `spells_grade` s'applique à cet ID partout où il est utilisé.

Le fichier `spell_patches.json` surcharge uniquement l'affichage client du grade 6 avec les nouvelles valeurs. Les effets et les données visuelles vanilla restent intacts.

## Suppression d'un sort personnalisé

Le programme demande :

1. sort global ou sort d'une classe ;
2. classe concernée si nécessaire ;
3. sort personnalisé à supprimer ;
4. deux confirmations successives.

La suppression retire uniquement l'ID sélectionné de `full_morphs.spells`, de `gladiatrool_spells.spells`, de `spells_effect`, de `spells_grade`, de `spells`, de `custom_spells.json` et de `spell_patches.json`.

`created_spells.json`, placé à côté de `run.bat`, est le registre interne du builder. Il mémorise notamment les anciens liens nécessaires pour restaurer automatiquement un sort remplacé. Il ne doit pas être placé dans le client.

Les sorts créés avant l'existence de ce registre restent détectés grâce à la base et à `custom_spells.json`. Si les informations d'un ancien remplacement sont introuvables, le programme affiche un avertissement avant la suppression.

## Fichiers JSON

| Fichier | Emplacement | Fonction |
|---|---|---|
| `custom_spells.json` | Client, chemin configurable | Définitions client des sorts personnalisés. |
| `spell_patches.json` | Client, chemin configurable | Surcharges d'affichage du grade 6 pour les sorts modifiés. |
| `created_spells.json` | Dossier du builder | Registre interne de création et de remplacement. |

Ces fichiers sont mis à jour automatiquement. Ils ne doivent pas être édités manuellement pendant l'utilisation du builder.

`builder.properties`, `created_spells.json`, les sauvegardes et les fichiers compilés sont exclus du dépôt par `.gitignore`.

## Sauvegardes et restauration

Avant chaque création, modification ou suppression, le programme crée une sauvegarde ciblée dans `backups/`.

- `restore.sql` restaure les données SQL concernées ;
- les fichiers `*.json.before` contiennent l'état client ou interne avant l'opération.

Une ancienne sauvegarde ne doit pas être restaurée aveuglément après plusieurs opérations ultérieures, car elle représente l'état exact au moment où elle a été créée.

## Après une opération

1. Redémarrer le serveur Game.
2. Redémarrer complètement le client.
3. Entrer dans le Gladiatrool.
4. Vérifier les caractéristiques et le comportement du sort.
5. Placer manuellement un nouveau sort dans un raccourci si nécessaire.

## Contributions et licence

Les issues et pull requests sont bienvenues. Consultez `CONTRIBUTING.md` avant de proposer une modification et ne joignez jamais de client Dofus, de SWF/FLA, de dump SQL ou de configuration privée.

Ce projet est distribué sous licence MIT. Voir `LICENSE`.
