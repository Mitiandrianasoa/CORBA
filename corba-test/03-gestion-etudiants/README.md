# 03-gestion-etudiants — CRUD étudiants/notes, calcul C++, affichage Java, 4 callbacks bidirectionnels

**Statut : construit et testé de bout en bout. Java ET C++ sont chacun simultanément client ET serveur (voir la section dédiée ci-dessous), exactement comme dans `02-bidirectionnel`.**

Projet réaliste combinant tout ce qui a été vu jusqu'ici : CRUD complet, `struct`/`sequence`, exception custom, MySQL, lecture/écriture de fichiers, et **4 callbacks bidirectionnels** distincts — le tout dans un seul scénario cohérent, comme un vrai sujet d'examen.

## Scénario

- **C++** (`cpp_server`) sert l'interface `GestionEtudiants` : CRUD étudiants + notes, stockage MySQL, calcule la moyenne.
- **Java** (`JavaClient`) est client PRINCIPALEMENT : importe des étudiants/notes depuis des fichiers CSV, **calcule lui-même une note à partir de notes brutes puis l'insère via C++**, pilote la suppression depuis un fichier (`delete.txt`), affiche la liste avec la moyenne, écrit des fichiers résultat. Tous ces appels vont de Java vers C++ — Java décide/calcule, C++ exécute.
- **Java sert AUSSI 4 petites interfaces**, chacune rappelée par C++ au moment précis où il en a besoin :
  - `MentionService.calculerMention(moyenne)` → C++ calcule la moyenne, ne connaît pas le barème des mentions, demande à Java (qui lit `data/bareme_mentions.txt`).
  - `FicheService.ecrireFiche(etudiant, moyenne, mention)` → C++ recherche l'étudiant (`rechercherEtudiant`), calcule tout, puis demande à Java d'écrire le fichier fiche.
  - `ValidationService.classeValide(nomClasse)` → avant de modifier la classe d'un étudiant (`modifierEtudiant`), C++ demande d'abord à Java si le nom de classe est autorisé (Java lit `data/classes_valides.txt`).
  - `PenaliteService.obtenirPenalite(matiere)` → avant d'appliquer une pénalité, C++ demande à Java le nombre de points à retirer (Java lit `data/penalites.txt`), puis C++ soustrait lui-même la note en MySQL.

Dans les 4 cas, le principe est le même : **C++ a besoin d'une info ou d'une règle métier que seul Java possède (parce que Java lit un fichier), donc c'est C++ qui prend l'initiative d'appeler Java** — jamais l'inverse.

```
data/etudiants.csv ──┐
data/notes.csv ───────┤
data/notes_brutes.csv ┤ lus par Java, pilotent des appels Java → C++
data/delete.txt ───────┘
        │
        │ ajouterEtudiant() / ajouterNote() / calculerMoyenne() / supprimerEtudiant()
        │ genererFiche() / changerClasse() / appliquerPenalite()
        ▼
┌─────────────────┐        MySQL (ecole_demo)        ┌──────────────┐
│  JavaClient      │ ────────────────────────────────▶│  cpp_server   │
│  (decide, calcule,│                                  │  (CRUD + calc,│
│   lit/ecrit       │                                  │   execute)    │
│   fichiers)       │                                  │               │
│                  │                                  │               │
│  MentionService   │ ◀──── obtenirMention(id) ─────── │               │
│  FicheService     │ ◀──── genererFiche(id) ───────── │               │
│  ValidationService│ ◀──── changerClasse(id, classe) ─ │               │
│  PenaliteService  │ ◀──── appliquerPenalite(id,mat) ─ │               │
│  (servent, lisent  │       C++ rappelle Java ici       │               │
│   les fichiers)   │ ── calculerMention() / ──────────▶│               │
│                  │    ecrireFiche() / classeValide() │               │
│                  │    / obtenirPenalite()             │               │
└─────────────────┘                                  └──────────────┘
```

## Structure

```
03-gestion-etudiants/
├── ecole.idl                  → IDL partagé (Etudiant, exception, GestionEtudiants + 4 services Java)
├── setup-mysql.sql            → cree la base ecole_demo + tables etudiants/notes
├── data/
│   ├── etudiants.csv          → nom;prenom;classe (importe par Java)
│   ├── notes.csv               → nom;prenom;matiere;note (importe par Java)
│   ├── notes_brutes.csv        → nom;prenom;matiere;note1;note2;note3 (Java calcule la moyenne, insere via C++)
│   ├── delete.txt               → nom;prenom des etudiants a supprimer (lu par Java, qui appelle C++)
│   ├── bareme_mentions.txt     → seuil:libelle (lu par MentionServiceImpl a chaque appel)
│   ├── classes_valides.txt     → une classe autorisee par ligne (lu par ValidationServiceImpl)
│   ├── penalites.txt            → matiere:points (lu par PenaliteServiceImpl)
│   ├── fiches/                  → un fichier par etudiant, ecrit par FicheServiceImpl
│   ├── moyenne_etudiant.txt     → ecrit par JavaClient (etape 4)
│   └── liste_etudiants.txt      → ecrit par JavaClient (etape 5)
├── java/
│   ├── src/
│   │   ├── ecole/                    → genere par idlj
│   │   ├── MentionServiceImpl.java     → servant Java, sert C++ (mention)
│   │   ├── FicheServiceImpl.java       → servant Java, sert C++ (fiche fichier)
│   │   ├── ValidationServiceImpl.java  → servant Java, sert C++ (classe valide ?)
│   │   ├── PenaliteServiceImpl.java    → servant Java, sert C++ (penalite)
│   │   └── JavaClient.java            → client ET serveur des 4 services
│   └── bin/
└── cpp/
    ├── src/
    │   ├── ecole.hh, ecoleSK.cc          → genere par omniidl
    │   ├── GestionEtudiantsImpl.hh/.cc    → CRUD + MySQL + 3 rappels vers Java
    │   └── cpp_server.cc                  → serveur uniquement
    ├── Makefile
    └── bin/cpp_server
```

## L'IDL

```idl
module ecole {
    struct Etudiant {
        long id;
        string nom;
        string prenom;
        string classe;
    };

    typedef sequence<Etudiant> ListeEtudiants;

    exception EtudiantIntrouvable {
        long id;
    };

    // Servi par JAVA : convertit une moyenne en mention, selon un bareme lu dans un fichier.
    interface MentionService {
        string calculerMention(in double moyenne);
    };

    // Servi par JAVA : ecrit la fiche complete d'un etudiant dans un fichier.
    interface FicheService {
        void ecrireFiche(in Etudiant e, in double moyenne, in string mention);
    };

    // Servi par JAVA : verifie qu'un nom de classe existe dans data/classes_valides.txt.
    interface ValidationService {
        boolean classeValide(in string nomClasse);
    };

    interface GestionEtudiants {
        // --- CRUD etudiants ---
        long ajouterEtudiant(in string nom, in string prenom, in string classe);
        boolean modifierEtudiant(in long id, in string nom, in string prenom, in string classe);
        boolean supprimerEtudiant(in long id);
        ListeEtudiants listerEtudiants();
        Etudiant rechercherEtudiant(in long id) raises (EtudiantIntrouvable);

        // --- Notes ---
        void ajouterNote(in long etudiantId, in string matiere, in double note);

        // --- Calcul ---
        double calculerMoyenne(in long etudiantId) raises (EtudiantIntrouvable);

        // --- Bidirectionnel : Java s'enregistre, C++ le rappelle pour la mention ---
        void enregistrerMentionService(in MentionService svc);
        string obtenirMention(in long etudiantId) raises (EtudiantIntrouvable);

        // --- Bidirectionnel : C++ recherche l'etudiant, puis demande a Java d'ecrire sa fiche ---
        void enregistrerFicheService(in FicheService svc);
        void genererFiche(in long etudiantId) raises (EtudiantIntrouvable);

        // --- Bidirectionnel : C++ demande a Java si la classe est valide avant de modifier ---
        void enregistrerValidationService(in ValidationService svc);
        boolean changerClasse(in long etudiantId, in string nouvelleClasse) raises (EtudiantIntrouvable);

        // --- Bidirectionnel : C++ demande a Java la penalite (lue dans un fichier), puis l'applique ---
        void enregistrerPenaliteService(in PenaliteService svc);
        boolean appliquerPenalite(in long etudiantId, in string matiere) raises (EtudiantIntrouvable);
    };
};
```

## Lancer

```bash
cd corba-test/03-gestion-etudiants

# Terminal A — serveur C++ (laisser ouvert)
./cpp/bin/cpp_server -ORBInitRef NameService=corbaname::localhost:2809

# Terminal B — client Java
java -cp java/bin JavaClient -ORBInitRef NameService=corbaname::localhost:2809
```

Sortie attendue (testée), étapes 2b, 7, 8 et 9 :
```
=== 2b. Calcul de note : Java calcule depuis data/notes_brutes.csv, insere via C++ ===
  -> Rakoto Jean : Info calculee = 12,00 (moyenne de 3 notes brutes), inseree via C++
  -> Rabe Marie : Info calculee = 16,33 (moyenne de 3 notes brutes), inseree via C++
  -> Ravao Sophie : Info calculee = 16,00 (moyenne de 3 notes brutes), inseree via C++

=== 7. Fiches : C++ recherche l'etudiant (rechercherEtudiant) puis demande a Java d'ecrire sa fiche ===
[Java] Fiche ecrite : data/fiches/Rakoto_Jean.txt
  -> Fiche demandee pour Rakoto Jean
[Java] Fiche ecrite : data/fiches/Rabe_Marie.txt
  -> Fiche demandee pour Rabe Marie
[Java] Fiche ecrite : data/fiches/Ravao_Sophie.txt
  -> Fiche demandee pour Ravao Sophie

=== 8. Changement de classe : C++ valide aupres de Java avant d'appeler modifierEtudiant ===
[Java] Classe "Terminale D" absente de data/classes_valides.txt : invalide.
  -> Rakoto Jean vers "Terminale D" : ERREUR, classe non autorisee (voir data/classes_valides.txt)
[Java] Classe "Terminale L" trouvee dans data/classes_valides.txt : valide.
  -> Ravao Sophie change vers "Terminale L" : OK

=== 9. Penalite : C++ demande la valeur a Java (fichier), puis l'applique en MySQL ===
[Java] Penalite trouvee pour "Maths" : 2.0 point(s)
  -> Penalite Maths pour Rakoto Jean : appliquee (voir MySQL)
[Java] Aucune penalite definie pour "Physique" dans data/penalites.txt
  -> Penalite Physique pour Rabe Marie : non appliquee (pas de regle ou pas de note)
```

Contenu généré, `data/fiches/Rakoto_Jean.txt` :
```
Fiche etudiant
===============
Nom     : Rakoto
Prenom  : Jean
Classe  : Terminale S
Moyenne : 9,67
Mention : Insuffisant
```

Vérifié en base après l'étape 8 : Ravao Sophie a bien `classe = Terminale L` ; Rakoto Jean garde `Terminale S` (le refus n'a rien modifié). Après l'étape 9 : la note de Maths de Rakoto Jean est passée de `8` à `6` en base.

## Java ET C++ sont chacun simultanément client ET serveur

Exactement le même principe que `02-bidirectionnel` (Option B), vérifié dans les logs :

| | `JavaClient` (Java) | `cpp_server` (C++) |
|---|---|---|
| **Rôle CLIENT** | Appelle `GestionEtudiants` : `ajouterEtudiant`, `ajouterNote`, `calculerMoyenne`, `supprimerEtudiant`, `listerEtudiants`, `obtenirMention`, `genererFiche`, `changerClasse`, `appliquerPenalite`... | Appelle 4 services Java : `mentionService_->calculerMention(...)`, `ficheService_->ecrireFiche(...)`, `validationService_->classeValide(...)`, `penaliteService_->obtenirPenalite(...)` |
| **Rôle SERVEUR** | Sert 4 interfaces (`MentionService`, `FicheService`, `ValidationService`, `PenaliteService`) : POA activé, servants créés, enregistrés auprès de C++ | Sert `GestionEtudiants` : POA activé, enregistré dans l'annuaire |
| **Preuve dans les logs** | `[Java] Fiche ecrite...`, `[Java] Penalite trouvee...` (Java REÇOIT des appels) | `[C++] Appel de Java pour...` (C++ INITIE des appels) |

## Les 4 callbacks bidirectionnels, en détail

Les 4 suivent le même schéma général : **au démarrage, `JavaClient` crée le servant, obtient sa référence, et l'enregistre auprès de C++** (`service.enregistrerXxxService(...)`). Ensuite, à chaque appel concerné, **C++ rappelle Java au milieu de son propre traitement**.

### 1. MentionService — le plus simple
`obtenirMention(id)` → C++ calcule la moyenne → appelle `mentionService_->calculerMention(moyenne)` → Java lit `bareme_mentions.txt` → renvoie le libellé.

### 2. FicheService — enchaîne 2 fonctions C++ avant le rappel
`genererFiche(id)` → C++ appelle **en interne** `rechercherEtudiant(id)` (récupère nom/prénom/classe) ET `calculerMoyenne(id)` ET `mentionService_->calculerMention(...)` → puis appelle `ficheService_->ecrireFiche(etudiant, moyenne, mention)` → Java écrit `data/fiches/Nom_Prenom.txt`. C'est la preuve que **du code C++ existant peut être réutilisé comme brique interne** avant de déclencher un callback.

### 3. ValidationService — un vrai "sinon message d'erreur"
`changerClasse(id, nouvelleClasse)` → C++ appelle `validationService_->classeValide(nouvelleClasse)` → Java lit `classes_valides.txt` ligne par ligne :
- Si trouvée : Java renvoie `true`, C++ appelle alors **en interne** `modifierEtudiant(id, nom, prenom, nouvelleClasse)` (le "U" du CRUD, enfin exercé) → la classe change réellement en base.
- Si absente : Java renvoie `false`, C++ n'appelle PAS `modifierEtudiant`, et `JavaClient` affiche un message d'erreur clair (`ERREUR, classe non autorisee`) sans jamais toucher la base.

### 4. PenaliteService — même schéma "sinon rien ne se passe"
`appliquerPenalite(id, matiere)` → C++ vérifie l'étudiant (`rechercherEtudiant`) → appelle `penaliteService_->obtenirPenalite(matiere)` → Java lit `penalites.txt` :
- Si une règle existe (ex. `Maths:2`) : Java renvoie `2.0`, C++ exécute `UPDATE notes SET note = GREATEST(note - 2, 0) ...` → la note baisse réellement en base.
- Si aucune règle : Java renvoie `0.0`, C++ n'exécute aucun `UPDATE`, `JavaClient` affiche `non appliquee (pas de regle ou pas de note)`.

## Java → C++ qui N'est PAS un callback : le calcul de note (étape 2b)

À l'inverse des 4 ci-dessus, **`calculerEtInsererNotes`** est un sens simple Java → C++ : Java lit `data/notes_brutes.csv` (3 notes par matière), **calcule lui-même la moyenne** (`somme / nb` en Java, aucun calcul côté C++), puis appelle l'`ajouterNote` déjà existant pour l'insérer. Pas besoin de nouvelle interface IDL : Java a déjà tout ce qu'il faut (le fichier ET la fonction d'insertion), donc un simple appel direct suffit — même logique que pour la suppression (étape 6).

## Pourquoi la suppression (étape 6) N'est PAS un callback

Pareil : **Java lit `delete.txt` et appelle directement `service.supprimerEtudiant(id)`** — même schéma que `importerEtudiants`/`importerNotes`, sans passer par un service Java intermédiaire.

**Règle générale retenue dans ce projet** : le callback ne se justifie que quand celui qui appelle (C++) a besoin d'une info/règle qu'il n'a pas et que seul Java possède, parce que Java lit un fichier que C++ n'a aucune raison de lire lui-même (le barème, le contenu de la fiche, la liste des classes valides, les pénalités). Dès que celui qui appelle a déjà tout ce qu'il faut, un appel direct suffit — pas besoin de complexifier.

## Audit complet des fonctions (rien n'est mort)

| Fonction (`GestionEtudiants`, C++) | Appelée par |
|---|---|
| `ajouterEtudiant` | Java, étape 1 (import CSV) |
| `modifierEtudiant` | **C++ en interne**, dans `changerClasse` |
| `supprimerEtudiant` | Java, étape 6 (delete.txt) |
| `listerEtudiants` | Java, étapes 3, 5, 7 |
| `rechercherEtudiant` | **C++ en interne**, dans `genererFiche`, `changerClasse` et `appliquerPenalite` |
| `ajouterNote` | Java, étapes 2 et 2b (import CSV + note calculée) |
| `calculerMoyenne` | Java (étapes 3, 4) + C++ en interne (`obtenirMention`, `genererFiche`) |
| `enregistrerMentionService` / `obtenirMention` | Java (démarrage) / Java (étape 3) → callback vers `MentionService` |
| `enregistrerFicheService` / `genererFiche` | Java (démarrage) / Java (étape 7) → callback vers `FicheService` |
| `enregistrerValidationService` / `changerClasse` | Java (démarrage) / Java (étape 8) → callback vers `ValidationService` |
| `enregistrerPenaliteService` / `appliquerPenalite` | Java (démarrage) / Java (étape 9) → callback vers `PenaliteService` |

## Recompiler après modification

```bash
# si ecole.idl change :
cd java/src && idlj -fall ../../ecole.idl && cd ../..
cd cpp/src  && omniidl -bcxx ../../ecole.idl && cd ../..

# Java
cd java/src && javac -d ../bin ecole/*.java MentionServiceImpl.java FicheServiceImpl.java ValidationServiceImpl.java PenaliteServiceImpl.java JavaClient.java && cd ../..

# C++
cd cpp && make && cd ..
```

## Notes techniques (pièges rencontrés et déjà corrigés)

- **`ajouterNote`/`supprimerEtudiant` prennent un `int` côté Java**, pas un `long` — l'IDL `long` se traduit en `int` en Java (voir dictionnaire section 2). L'id récupéré dans une `Map<String, Long>` doit être "unboxé" avec `.intValue()`.
- **Construire un `sequence<Etudiant>` en C++** se fait avec `liste->length(n+1)` puis `(*liste)[n].champ = ...` — pas de `push_back` comme un `std::vector`.
- **Lire un champ `string` de `struct` en C++** (ex. `res->getString("nom")`, un `std::string` JDBC-style) doit être converti avec `.c_str()` avant d'être assigné à un champ `string` IDL (`CORBA::String_member`).
- **Passer un `struct` par `in` en C++** génère un paramètre `const ecole::Etudiant&` (référence constante), pas un pointeur — c'est pour ça que `ecrireFiche` reçoit `const ecole::Etudiant& e` et qu'on l'appelle avec `ficheService_->ecrireFiche(e.in(), ...)` (`e` étant un `Etudiant_var`).
- **Réutiliser une fonction CORBA déjà implémentée comme brique interne** (ex. `rechercherEtudiant` appelée depuis `genererFiche`) : on l'appelle directement comme une méthode C++ normale (`rechercherEtudiant(id)`), pas via le réseau — c'est le même processus, donc un simple appel de fonction C++.
- **Relancer une base vide à chaque test** : si tu relances `JavaClient` plusieurs fois de suite sans vider la base, les étudiants seront dupliqués. Pour repartir propre : `mysql -u ecole_user -pecole -e "DELETE FROM ecole_demo.notes; DELETE FROM ecole_demo.etudiants;"`
- **Un paramètre `in Interface` (référence d'objet) doit être dupliqué avant stockage côté serveur** : chaque `enregistrerXxxService(svc)` fait `ecole::XxxService::_duplicate(svc)` avant de stocker dans le `_var` membre, sinon la référence devient invalide après le retour de la fonction.
- **Vérifier qu'une référence stockée n'est pas vide** : avant chaque appel vers un service Java, on teste `CORBA::is_nil(...)` — sinon, si la fonction est appelée avant que Java ne se soit enregistré, ça plante.
- **Ne pas complexifier chaque fonction avec un callback** : un aller-retour Java↔C++↔Java n'est utile QUE si C++ a réellement besoin d'une donnée/logique côté Java. Sinon, un simple appel direct (comme pour `delete.txt`) est plus clair et plus facile à déboguer.

## Pour aller plus loin (déjà couvert dans `ToDo.md` de `02-bidirectionnel`)

- Inverser les rôles : faire ce même exercice avec Java qui sert `GestionEtudiants` via JDBC, C++ qui importe les fichiers (voir Exercice 6 du `ToDo.md`).
- Étendre `FicheService` : déclencher `genererFiche` automatiquement dès qu'un élève atteint un nombre de notes complet, plutôt que de le faire pour tout le monde en boucle.
