# TODO — Exercices pour se préparer à l'aléa CORBA

Objectif : arriver à l'examen assez à l'aise avec CORBA pour implémenter N'IMPORTE QUELLE fonction qu'on te donnera, quel que soit le sens (Java→C++ ou C++→Java) et quel que soit le backend (fichier ou MySQL) assigné à chaque langage — car l'aléa peut très bien inverser ce qu'on a fait jusqu'ici (par ex. demander du MySQL côté Java, ou des fichiers côté C++).

Chaque exercice ci-dessous est petit (quelques heures max), amusant, et entraîne une compétence CORBA précise. Fais-les dans l'ordre, en réutilisant à chaque fois la structure `java/src` + `cpp/src` + `app.idl` qu'on a déjà.

## Vue d'ensemble

| # | Exercice | Compétence CORBA entraînée | Backend Java | Backend C++ | Difficulté |
|---|---|---|---|---|---|
| 1 | Compteur partagé | types numériques, `in`/`out` | mémoire | mémoire | ⭐ |
| 2 | Journal de bord | `sequence<string>`, historique | fichier (append) | mémoire | ⭐⭐ |
| 3 | Carnet d'adresses | `struct`, `sequence<struct>`, CRUD | — | MySQL | ⭐⭐⭐ |
| 4 | Nombre mystère | `enum`, exception custom, état persistant | fichier | mémoire | ⭐⭐⭐ |
| 5 | Messagerie + notifications | **callback** (serveur rappelle le client) | — | MySQL | ⭐⭐⭐⭐ |
| 6 | Combo inversé | Java→MySQL (JDBC), C++→fichiers (fstream) | MySQL | fichier | ⭐⭐⭐⭐ |

Avant de commencer, garde sous la main :
- Le dictionnaire de référence (glossaire, types, syntaxe Java/C++ côte à côte) : `documentation/dictionnaire-java-cpp.md`
- La checklist "ajouter une fonction partagée" (section 7 du dictionnaire) — c'est LE réflexe à avoir à l'examen.

---

## Exercice 1 — Compteur partagé ⭐

**But** : se réchauffer avec des types numériques et le sens `out`, pas seulement des `string`.

**Scénario** : un compteur commun. N'importe lequel des deux programmes peut l'incrémenter, le décrémenter, ou juste le lire.

**IDL** :
```idl
interface Counter {
    long increment(in long pas);
    long decrement(in long pas);
    long getValeur();
};
```

**Comment ça marche** : `increment`/`decrement` modifient une variable `long` en mémoire dans le servant (Java OU C++, à toi de choisir qui l'implémente) et renvoient la nouvelle valeur direct dans le `return` — pas besoin de `out` ici puisqu'une seule valeur revient. Le programme qui NE possède PAS le compteur l'appelle comme client normal.

**Variante pour t'entraîner sur `out`** : ajoute une méthode qui renvoie DEUX informations à la fois (par ex. la valeur ET si elle a dépassé un seuil) :
```idl
void getValeurEtStatut(out long valeur, out boolean depasseSeuil);
```
Ça t'oblige à utiliser un `LongHolder`/`BooleanHolder` côté Java, et des pointeurs côté C++ — vois section 3 du dictionnaire.

---

## Exercice 2 — Journal de bord (mini chat log) ⭐⭐

**But** : pratiquer `sequence<string>` (une LISTE de valeurs, pas juste une seule) et l'écriture incrémentale dans un fichier (append, pas juste overwrite comme dans `02-bidirectionnel`).

**Scénario** : les deux programmes peuvent poster un message dans un journal partagé, et n'importe lequel peut relire tout l'historique.

**IDL** :
```idl
interface Journal {
    void poster(in string auteur, in string message);
    sequence<string> lireTout();
};
```

**Comment ça marche** :
- `poster(...)` **ajoute** une ligne au fichier (`FileWriter` en mode append côté Java avec `Files.write(path, ligne.getBytes(), StandardOpenOption.APPEND)`, ou `std::ofstream` en mode `std::ios::app` côté C++) — format simple, ex. `"[auteur] message\n"`.
- `lireTout()` relit le fichier ligne par ligne et renvoie un `sequence<string>` (un tableau Java `String[]`, ou une classe liste générée côté C++) — CORBA sérialise cette liste entière en un seul appel réseau.

**Piège à connaître** : `sequence<string>` génère en Java un type `String[]`, mais côté C++ omniidl génère une VRAIE classe (ex. `app::StringSeq`) avec des méthodes `length()`, et l'opérateur `[]` pour accéder aux éléments — pas un tableau C++ classique. Regarde le fichier `.hh` généré pour voir son nom exact après un `omniidl -bcxx`.

---

## Exercice 3 — Carnet d'adresses (struct + MySQL) ⭐⭐⭐

**But** : LE cas le plus probable à l'examen — manipuler un `struct` (un enregistrement à plusieurs champs) et une vraie table MySQL avec plusieurs lignes, pas juste une seule case comme dans `DataService`.

**Scénario** : un carnet de contacts stocké en MySQL, consultable et modifiable depuis les deux langages.

**IDL** :
```idl
struct Contact {
    string nom;
    string telephone;
};

typedef sequence<Contact> ListeContacts;

interface Annuaire {
    void ajouter(in Contact c);
    boolean supprimer(in string nom);
    ListeContacts listerTous();
    Contact rechercher(in string nom);
};
```

**Table MySQL** (adapte `setup-mysql.sql`) :
```sql
CREATE TABLE contacts (
  nom VARCHAR(100) PRIMARY KEY,
  telephone VARCHAR(30) NOT NULL
);
```

**Comment ça marche** :
- `ajouter(c)` → `INSERT INTO contacts VALUES (c.nom, c.telephone)`. En C++, tu accèdes aux champs avec `c.nom.in()` (le `struct` généré a des accesseurs particuliers pour les `string`, voir le `.hh` généré). En Java, accès direct : `c.nom`, `c.telephone` (champs publics).
- `listerTous()` → `SELECT * FROM contacts`, tu construis un `sequence<Contact>` en boucle sur le `ResultSet` (C++) ou le `ResultSet` JDBC (Java) — un `Contact` par ligne.
- `rechercher(nom)` → `SELECT ... WHERE nom = ?` (prepared statement, comme dans `DataServiceImpl.cc`), lève une exception si rien trouvé (voir exercice 4 pour les exceptions custom).

**Astuce examen** : c'est l'exercice le plus représentatif d'un vrai "CRUD" qu'on pourrait te demander — refais-le une deuxième fois en inversant qui implémente quoi (Java sert `Annuaire` avec MySQL via JDBC, comme dans l'exercice 6).

---

## Exercice 4 — Le nombre mystère (enum + exception custom) ⭐⭐⭐

**Ton idée de départ**, formalisée : *"C++ choisit un nombre, Java propose, C++ répond plus grand/plus petit"*.

**But** : pratiquer un type `enum` IDL (au lieu de renvoyer une string comme `"plus grand"`) et une **exception personnalisée** — deux choses qu'on n'a pas encore touchées et que l'examen peut très bien demander.

**IDL** :
```idl
enum Indice { TROP_GRAND, TROP_PETIT, TROUVE };

exception PartieTerminee {
    string raison;
};

interface Devinette {
    void nouvellePartie(in long min, in long max);
    Indice proposer(in long valeur) raises (PartieTerminee);
};
```

**Comment ça marche** :
- `nouvellePartie(min, max)` : le serveur (ex. C++) tire un nombre secret au hasard entre `min` et `max`, le sauvegarde dans un fichier (`secret.txt`) pour que l'état survive même si le programme relance une partie plus tard — c'est ton entraînement "état persistant".
- `proposer(valeur)` : compare `valeur` au secret lu dans le fichier, renvoie `TROP_GRAND`, `TROP_PETIT` ou `TROUVE`. Si le joueur a déjà gagné et rejoue, **lève l'exception** `PartieTerminee` plutôt qu'un retour normal.
- Côté client (Java), tu captures ça avec un `catch` dédié :
  ```java
  try {
      Indice res = devinette.proposer(42);
  } catch (PartieTerminee e) {
      System.out.println("Partie finie : " + e.raison);
  }
  ```
  Une exception IDL personnalisée devient une VRAIE classe d'exception Java/C++ générée (avec ses propres champs, ici `raison`) — pas juste un `CORBA::Exception` générique comme dans nos `catch` actuels.

---

## Exercice 5 — Messagerie avec notifications (le vrai bidirectionnel) ⭐⭐⭐⭐

**Ton idée de départ**, formalisée : *"Java envoie des messages, C++ les stocke en MySQL, callbacks pour notifications"*.

**But** : implémenter le "dialogue simultané" (Option B) vu dans `question-bidirectionnel.md` — ici pour de vrai, avec un usage concret : le **callback**. C'est le pattern CORBA le plus avancé et le plus impressionnant à montrer à l'examen si ça tombe.

**Idée du callback** : au lieu que le client demande sans arrêt "il y a du nouveau ?", le client donne au serveur une référence vers LUI-MÊME, et c'est le serveur qui le rappelle quand il y a du nouveau.

**IDL** (deux interfaces, une pour chaque rôle) :
```idl
interface Notifiable {
    void nouveauMessage(in string auteur, in string contenu);
};

interface Messagerie {
    void envoyer(in string auteur, in string contenu);
    void sAbonner(in Notifiable abonne);
};
```

**Comment ça marche, étape par étape** :
1. Le serveur C++ implémente `Messagerie` (comme `DataService` avant) et s'enregistre dans l'annuaire.
2. Le client Java implémente EN PLUS `Notifiable` (il devient donc lui-même un mini-serveur, avec son propre POA actif — relis la section 4.2/4.3 du dictionnaire) et s'enregistre.
3. Java appelle `sAbonner(monNotifiable)` en passant sa PROPRE référence CORBA (pas une string, un objet vivant) au serveur C++.
4. Le serveur C++ stocke cette référence dans une variable membre.
5. Quand `envoyer(...)` est appelé (par Java ou par un autre client), le serveur C++ :
   - enregistre le message en MySQL (comme dans `DataServiceImpl`)
   - PUIS appelle lui-même `abonne->nouveauMessage(auteur, contenu)` sur la référence Java stockée à l'étape 4 → c'est le serveur qui initie un appel vers le client, sans que Java n'ait rien redemandé.

**Piège classique** : le client Java doit avoir activé son POA et être en train de tourner (`orb.run()` dans un thread séparé ou après l'enregistrement) AVANT d'appeler `sAbonner`, sinon il ne pourra jamais recevoir le rappel du serveur.

---

## Exercice 6 — Le combo inversé : Java→MySQL et C++→fichiers ⭐⭐⭐⭐

**But** : dans `02-bidirectionnel`, c'est TOUJOURS Java qui fait les fichiers et C++ qui fait MySQL. À l'examen, ça peut très bien être l'inverse. Cet exercice te fait pratiquer les deux techniques qu'on n'a jamais utilisées : **JDBC** (Java vers MySQL) et **fstream** (C++ vers fichiers).

**Scénario** : reprends l'Annuaire de l'exercice 3, mais implémente-le à l'envers : `Annuaire` est servi par **Java** avec MySQL, et un nouveau `NotesService` (petit fichier de notes) est servi par **C++** avec des fichiers.

### Java → MySQL avec JDBC

Contrairement à `libmysqlcppconn` (une lib dédiée), Java utilise **JDBC**, une API standard intégrée au JDK. Il faut juste le driver MySQL (`mysql-connector-j`, un `.jar`) sur le classpath.

```java
import java.sql.*;

public class AnnuaireImpl extends AnnuairePOA {
    private static final String URL = "jdbc:mysql://localhost:3306/corba_demo";
    private static final String USER = "corba_user";
    private static final String PASS = "corba";

    public void ajouter(Contact c) {
        String sql = "INSERT INTO contacts (nom, telephone) VALUES (?, ?)";
        try (Connection con = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, c.nom);
            ps.setString(2, c.telephone);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur MySQL : " + e.getMessage());
        }
    }
}
```
Pour compiler/exécuter, il faudra le driver JDBC sur le classpath :
```bash
# telecharger une fois le driver (fichier .jar) dans le dossier du projet, puis :
javac -cp .:mysql-connector-j.jar -d bin ...
java  -cp bin:mysql-connector-j.jar AnnuairePeer ...
```

### C++ → Fichiers avec fstream

Contrairement à `libmysqlcppconn`, ici pas de lib externe : `<fstream>` fait partie de la bibliothèque standard C++.

```cpp
#include <fstream>
#include <sstream>

char* NotesServiceImpl::lireNote() {
    std::ifstream fichier("notes.txt");
    if (!fichier) {
        return CORBA::string_dup("(pas encore de note)");
    }
    std::stringstream buffer;
    buffer << fichier.rdbuf();
    return CORBA::string_dup(buffer.str().c_str());
}

void NotesServiceImpl::ecrireNote(const char* texte) {
    std::ofstream fichier("notes.txt");   // ecrase le contenu precedent
    fichier << texte;
}
```
Pour ajouter à la fin plutôt qu'écraser (comme le journal de l'exercice 2), ouvre avec `std::ios::app` :
```cpp
std::ofstream fichier("notes.txt", std::ios::app);
```

**Ce que cet exercice prouve** : le code CORBA (IDL, ORB, POA, naming) ne change JAMAIS — seule la techno utilisée DANS le servant change (MySQL vs fichier, JDBC vs libmysqlcppconn vs fstream). C'est exactement ce qui te permettra de t'adapter à n'importe quel aléa : la partie CORBA est toujours la même, seule la logique métier varie.

---

## Avant l'examen — dernière relecture

- Refais de tête la checklist "ajouter une fonction partagée" (section 7 du dictionnaire) sans regarder.
- Sois capable d'écrire, sans copier-coller, les 7 opérations de base (section 4 du dictionnaire) dans les DEUX langages.
- Sache dire, pour chaque type IDL (`string`, `long`, `boolean`, `struct`, `sequence`, `enum`, `exception`), comment ça se traduit en Java et en C++ (section 2 du dictionnaire + exercices 3 et 4 ci-dessus).




faire:
- JE VEUX VRAIMENT UN PROJET SIMPLE AVEC DES CALCULS DES FONCTIONS A APPELER DEPUIS LES DEUX APPLICATIONS:
    -par exemple: calcul moyenne depuis c++ affichage depuis java et lister son nom sa moyen si moyenne <12 
                    - crud
                    - etudiant, nom, prenom, classe
                    - etudiant, matiere, note
                    - creation de etudiant depuis un fichier 