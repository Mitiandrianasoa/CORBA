# Recettes de code CORBA Java ↔ C++ (à copier-adapter)

Complément de `dictionnaire-java-cpp.md` (qui explique les concepts). Ici : **des blocs prêts à copier** pour les fonctionnalités qui reviennent dans un aléa.

**Tout le code de ce fichier a été compilé et exécuté** (omniORB 4.3.2, OpenJDK 8, MySQL). Il vient du projet complet `modele-complet/`, que tu peux copier tel quel comme point de départ.

## Sommaire

- [0. Comment utiliser ce fichier](#0-comment-utiliser-ce-fichier)
- [1. L'IDL modèle](#1-lidl-modèle)
- [2. Squelettes `main` (client + serveur)](#2-squelettes-main-client--serveur)
- [3. Attendre que l'autre programme soit lancé](#3-attendre-que-lautre-programme-soit-lancé)
- [4. Struct : envoyer / renvoyer un enregistrement](#4-struct--envoyer--renvoyer-un-enregistrement)
- [5. Sequence : envoyer / renvoyer une liste](#5-sequence--envoyer--renvoyer-une-liste)
- [6. Exceptions métier](#6-exceptions-métier)
- [7. Paramètres `out` (renvoyer plusieurs valeurs)](#7-paramètres-out-renvoyer-plusieurs-valeurs)
- [8. C++ appelle Java pendant un traitement (validation)](#8-c-appelle-java-pendant-un-traitement-validation)
- [9. Callback / abonnement (C++ prévient Java)](#9-callback--abonnement-c-prévient-java)
- [10. MySQL en C++ (CRUD complet)](#10-mysql-en-c-crud-complet)
- [11. Fichiers en Java](#11-fichiers-en-java)
- [12. Fichiers en C++](#12-fichiers-en-c)
- [13. Menu interactif Java](#13-menu-interactif-java)
- [14. Pièges C++ (mémoire, strings)](#14-pièges-c-mémoire-strings)
- [15. Compiler et lancer](#15-compiler-et-lancer)
- [16. Aléa → quelle recette ?](#16-aléa--quelle-recette-)

---

## 0. Comment utiliser ce fichier

1. Copie le dossier `modele-complet/` vers `corba-test/07-mon-alea/` (ou pioche les blocs dans un projet existant).
2. Dans chaque bloc, remplace les noms marqués **À ADAPTER** : module `modele`, struct `Produit`, interfaces `ServiceBase` / `ServiceFichier`, table `produits`.
3. Règle pour choisir qui sert quoi : **celui qui possède la ressource sert l'interface**. MySQL → C++ sert. Fichiers côté Java → Java sert.

Correspondance des noms générés, pour une `interface X` dans `module m` :

| Je veux… | Java | C++ |
|---|---|---|
| servir X | `class XImpl extends XPOA` | `class XImpl : public POA_m::X` |
| convertir une référence | `XHelper.narrow(obj)` | `m::X::_narrow(obj)` |
| garder une référence | `X` | `m::X_var` |
| recevoir une référence en paramètre | `X` | `m::X_ptr` (+ `_duplicate` pour la garder) |

---

## 1. L'IDL modèle

`modele.idl` — contient un exemple de chaque construction utile.

```idl
module modele {                                   // À ADAPTER : nom en minuscules
    // Une ligne de table MySQL
    struct Produit {
        long   id;
        string nom;
        long   quantite;
        double prix;
    };
    typedef sequence<Produit> ListeProduits;      // liste de struct
    typedef sequence<string>  ListeTextes;        // liste de strings

    exception Introuvable { long id; };           // exception avec un champ
    exception Refuse      { string raison; };

    // Servi par JAVA, donne a C++ via sAbonner (callback)
    interface Notifiable {
        void notifier(in string evenement);
    };

    // Servi par JAVA : tout ce qui touche aux fichiers
    interface ServiceFichier {
        ListeTextes lireLignes(in string fichier);
        void        ajouterLigne(in string fichier, in string ligne);
        boolean     contient(in string fichier, in string valeur);
        long        lireValeur(in string fichier, in string cle) raises (Refuse);
    };

    // Servi par C++ : tout ce qui touche a MySQL
    interface ServiceBase {
        long          ajouter(in Produit p) raises (Refuse);
        Produit       trouver(in long id) raises (Introuvable);
        ListeProduits lister();
        ListeProduits chercher(in string motif);
        void          modifierQuantite(in long id, in long delta) raises (Introuvable, Refuse);
        boolean       supprimer(in long id);
        long          importer(in ListeProduits liste);
        void          statistiques(out long nombre, out double valeurTotale);
        void          sAbonner(in Notifiable abonne);
    };
};
```

Ce que ça donne des deux côtés (signatures générées, à recopier dans tes `Impl`) :

| IDL | Java (`XxxOperations.java`) | C++ (`modele.hh`) |
|---|---|---|
| `long ajouter(in Produit p)` | `int ajouter(Produit p) throws Refuse` | `CORBA::Long ajouter(const modele::Produit& p)` |
| `Produit trouver(in long id)` | `Produit trouver(int id) throws Introuvable` | `modele::Produit* trouver(CORBA::Long id)` |
| `ListeProduits lister()` | `Produit[] lister()` | `modele::ListeProduits* lister()` |
| `long importer(in ListeProduits l)` | `int importer(Produit[] l)` | `CORBA::Long importer(const modele::ListeProduits& l)` |
| `ListeTextes lireLignes(in string f)` | `String[] lireLignes(String f)` | `modele::ListeTextes* lireLignes(const char* f)` |
| `void statistiques(out long n, out double t)` | `void statistiques(IntHolder n, DoubleHolder t)` | `void statistiques(CORBA::Long& n, CORBA::Double& t)` |
| `void sAbonner(in Notifiable a)` | `void sAbonner(Notifiable a)` | `void sAbonner(modele::Notifiable_ptr a)` |

> Astuce : en cas de doute, ouvre `java/src/modele/XOperations.java` et cherche `= 0;` dans `cpp/src/modele.hh` : ce sont les signatures exactes à implémenter.

---

## 2. Squelettes `main` (client + serveur)

### Java — sert `ServiceFichier` + `Notifiable`, appelle `ServiceBase`

```java
import modele.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

public class JavaPeer {
    public static void main(String[] args) throws Exception {
        ORB orb = ORB.init(args, null);

        POA poa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
        poa.the_POAManager().activate();

        NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));

        // --- SERVEUR 1 : publie dans l'annuaire (C++ le cherchera par son nom) ---
        ServiceFichierImpl fichierImpl = new ServiceFichierImpl();
        ServiceFichier fichierRef = ServiceFichierHelper.narrow(poa.servant_to_reference(fichierImpl));
        nc.rebind(nc.to_name("ServiceFichier"), fichierRef);

        // --- SERVEUR 2 : PAS dans l'annuaire (donne a C++ en parametre) ---
        Notifiable notifRef = NotifiableHelper.narrow(poa.servant_to_reference(new NotifiableImpl()));

        // orb.run() bloque : dans un thread, pour garder le main pour la partie client
        Thread t = new Thread(orb::run);
        t.setDaemon(true);
        t.start();

        // --- CLIENT ---
        ServiceBase base = ServiceBaseHelper.narrow(attendre(nc, "ServiceBase"));  // voir section 3
        base.sAbonner(notifRef);

        // ... tes appels : base.ajouter(...), base.lister(), ...

        orb.shutdown(false);
    }
}
```

> Si Java n'est **que serveur**, remplace le thread par un simple `orb.run();` à la fin.
> Si Java n'est **que client**, supprime tout ce qui concerne `POA` et `orb.run`.

### C++ — sert `ServiceBase`, appelle Java

```cpp
#include "modele.hh"
#include "ServiceBaseImpl.hh"
#include <iostream>

int main(int argc, char** argv)
{
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);

    CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");
    PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
    poa->the_POAManager()->activate();

    CORBA::Object_var nsObj = orb->resolve_initial_references("NameService");
    CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);

    // --- SERVEUR ---
    ServiceBaseImpl* impl = new ServiceBaseImpl(nc);   // on lui passe l'annuaire pour qu'il appelle Java
    PortableServer::ObjectId_var id = poa->activate_object(impl);
    modele::ServiceBase_var ref = impl->_this();
    CosNaming::Name_var nom = nc->to_name("ServiceBase");   // PAS nc->rebind(nc->to_name(..)) : pointeur !
    nc->rebind(nom, ref);
    std::cout << "[C++] ServiceBase enregistre" << std::endl;

    orb->run();
    return 0;
}
```

### C++ — l'en-tête du servant

```cpp
#ifndef SERVICEBASEIMPL_HH
#define SERVICEBASEIMPL_HH

#include "modele.hh"
#include <omnithread.h>
#include <vector>

class ServiceBaseImpl : public POA_modele::ServiceBase
{
    CosNaming::NamingContextExt_var nc;            // pour retrouver les services Java
    std::vector<modele::Notifiable_var> abonnes;   // callbacks (section 9)
    omni_mutex verrou;                             // protege "abonnes"

    void notifierTous(const char* evenement);

public:
    ServiceBaseImpl(CosNaming::NamingContextExt_ptr annuaire);

    CORBA::Long            ajouter(const modele::Produit& p);
    modele::Produit*       trouver(CORBA::Long id);
    modele::ListeProduits* lister();
    modele::ListeProduits* chercher(const char* motif);
    void                   modifierQuantite(CORBA::Long id, CORBA::Long delta);
    CORBA::Boolean         supprimer(CORBA::Long id);
    CORBA::Long            importer(const modele::ListeProduits& liste);
    void                   statistiques(CORBA::Long& nombre, CORBA::Double& valeurTotale);
    void                   sAbonner(modele::Notifiable_ptr abonne);
};

#endif
```

Constructeur (dans le `.cc`) :
```cpp
ServiceBaseImpl::ServiceBaseImpl(CosNaming::NamingContextExt_ptr annuaire)
    : nc(CosNaming::NamingContextExt::_duplicate(annuaire)) {}
```

---

## 3. Attendre que l'autre programme soit lancé

Évite l'erreur `NotFound` quand on lance les programmes dans le « mauvais » ordre.

Java :
```java
import org.omg.CosNaming.NamingContextPackage.NotFound;

static org.omg.CORBA.Object attendre(NamingContextExt nc, String nom) throws Exception {
    for (int essai = 1; essai <= 30; essai++) {
        try {
            return nc.resolve_str(nom);
        } catch (NotFound e) {
            System.out.println("[Java] " + nom + " pas encore la, essai " + essai);
            Thread.sleep(1000);
        }
    }
    throw new Exception(nom + " introuvable dans l'annuaire");
}

// usage :
ServiceBase base = ServiceBaseHelper.narrow(attendre(nc, "ServiceBase"));
```

C++ :
```cpp
static CORBA::Object_ptr attendre(CosNaming::NamingContextExt_ptr nc, const char* nom) {
    for (int essai = 1; essai <= 30; essai++) {
        try {
            return nc->resolve_str(nom);
        } catch (const CosNaming::NamingContext::NotFound&) {
            std::cout << "[C++] " << nom << " pas encore la, essai " << essai << std::endl;
            omni_thread::sleep(1);
        }
    }
    throw CORBA::TRANSIENT();
}

// usage (toujours dans un try, voir la remarque) :
try {
    CORBA::Object_var obj = attendre(nc, "ServiceFichier");
    modele::ServiceFichier_var fichier = modele::ServiceFichier::_narrow(obj);
    // ... appels ...
} catch (const CORBA::Exception& e) {
    std::cout << "[C++] Java injoignable : " << e._name() << std::endl;
}
```

> **Attention, nom périmé** : si un programme est fermé, son nom **reste dans l'annuaire**. `resolve_str` réussit, mais l'appel échoue avec `TRANSIENT` ou `COMM_FAILURE`. En C++, une exception non attrapée **tue le programme** (`terminate called after throwing an instance of 'CORBA::COMM_FAILURE'`). D'où le `catch (const CORBA::Exception&)` autour de tout appel distant fait depuis `main`.
> Pour vider un nom à la main : `nameclt -ORBInitRef NameService=corbaname::localhost:2809 unbind ServiceFichier`.

---

## 4. Struct : envoyer / renvoyer un enregistrement

### Java construit et envoie une struct

```java
int id = base.ajouter(new Produit(0, "bonbon", 20, 0.5));   // constructeur : champs dans l'ordre de l'IDL
```

### C++ reçoit une struct

```cpp
CORBA::Long ServiceBaseImpl::ajouter(const modele::Produit& p) {
    // p.nom est un String_member : (const char*)p.nom pour le passer a MySQL / std::string(p.nom)
    std::cout << "recu " << p.nom << " x" << p.quantite << " a " << p.prix << std::endl;
    ...
}
```

### C++ renvoie une struct

```cpp
modele::Produit* ServiceBaseImpl::trouver(CORBA::Long id) {
    ...
    if (!rs->next())
        throw modele::Introuvable(id);            // section 6

    modele::Produit* p = new modele::Produit();   // "new" obligatoire : l'ORB la liberera
    p->id       = rs->getInt("id");
    p->nom      = rs->getString("nom").c_str();   // copie automatique dans le String_member
    p->quantite = rs->getInt("quantite");
    p->prix     = rs->getDouble("prix");
    return p;
}
```

### Java reçoit une struct

```java
Produit p = base.trouver(id);
System.out.println(p.nom + " x" + p.quantite);   // champs publics
```

---

## 5. Sequence : envoyer / renvoyer une liste

### C++ renvoie une liste (ex. résultat d'un SELECT)

```cpp
modele::ListeProduits* ServiceBaseImpl::lister() {
    modele::ListeProduits* liste = new modele::ListeProduits();
    ...
    while (rs->next()) {
        CORBA::ULong n = liste->length();
        liste->length(n + 1);              // agrandir de 1
        remplir((*liste)[n], rs.get());    // (*liste)[n] : liste est un pointeur
    }
    return liste;
}
```

### Java reçoit la liste (= un tableau)

```java
for (Produit p : base.lister()) {
    System.out.println("#" + p.id + " " + p.nom);
}
```

### Java construit et envoie une liste (ex. lue dans un CSV)

```java
List<Produit> liste = new ArrayList<>();
liste.add(new Produit(0, "bonbon", 10, 0.5));
int n = base.importer(liste.toArray(new Produit[0]));   // List -> tableau
```

### C++ reçoit une liste

```cpp
CORBA::Long ServiceBaseImpl::importer(const modele::ListeProduits& liste) {
    CORBA::Long ajoutes = 0;
    for (CORBA::ULong i = 0; i < liste.length(); i++) {
        try {
            ajouter(liste[i]);             // liste[i] : c'est une reference, pas un pointeur
            ajoutes++;
        } catch (const modele::Refuse& e) {
            std::cout << "[C++] Ignore " << liste[i].nom << " : " << e.raison << std::endl;
        }
    }
    return ajoutes;
}
```

### Liste de strings

Java renvoie :
```java
public String[] lireLignes(String fichier) {
    List<String> resultat = new ArrayList<>();
    // ... remplir ...
    return resultat.toArray(new String[0]);
}
```

C++ reçoit (appel vers Java) :
```cpp
modele::ListeTextes_var lignes = fichier->lireLignes("data/autorises.txt");
for (CORBA::ULong i = 0; i < lignes->length(); i++)     // "->" sur un _var
    std::cout << lignes[i] << std::endl;                // [] directement sur le _var
```

C++ renvoie une liste de strings (sens inverse) :
```cpp
modele::ListeTextes* l = new modele::ListeTextes();
l->length(2);
(*l)[0] = CORBA::string_dup("premier");
(*l)[1] = CORBA::string_dup(monStdString.c_str());
return l;
```

---

## 6. Exceptions métier

IDL :
```idl
exception Refuse { string raison; };
long ajouter(in Produit p) raises (Refuse);
void modifierQuantite(in long id, in long delta) raises (Introuvable, Refuse);
```

### C++ lève → Java attrape

```cpp
throw modele::Refuse("produit non autorise");                              // champ string
throw modele::Introuvable(id);                                             // champ long
throw modele::Refuse(("stock insuffisant : " + std::to_string(q)).c_str()); // std::string -> c_str()
```
```java
try {
    base.modifierQuantite(id, -1000);
} catch (Refuse e) {
    System.out.println("Refuse : " + e.raison);        // champs publics
} catch (Introuvable e) {
    System.out.println("Introuvable : id " + e.id);
}
```

### Java lève → C++ attrape

```java
public int lireValeur(String fichier, String cle) throws Refuse {
    ...
    throw new Refuse("cle absente : " + cle);
}
```
```cpp
try {
    CORBA::Long v = fichier->lireValeur("data/config.txt", "bonbon");
} catch (const modele::Refuse& e) {
    std::cout << "Refuse : " << e.raison << std::endl;
}
```

### Erreurs techniques (l'autre programme est fermé, réseau…)

| | Java | C++ |
|---|---|---|
| exceptions de l'IDL | `catch (Refuse e)` | `catch (const modele::Refuse& e)` |
| toutes les erreurs CORBA techniques | `catch (org.omg.CORBA.SystemException e)` | `catch (const CORBA::SystemException& e)` |
| nom de l'erreur | `e.toString()` | `e._name()` (ex. `TRANSIENT`) |

> **Piège** : `catch (const CORBA::Exception&)` attrape AUSSI tes exceptions IDL (`Refuse`…). Si tu lèves `Refuse` dans un `try` qui a ce catch, tu l'attrapes toi-même. Solution : lever **après** le `try` (voir section 8).

---

## 7. Paramètres `out` (renvoyer plusieurs valeurs)

IDL :
```idl
void statistiques(out long nombre, out double valeurTotale);
```

C++ (serveur) : on affecte simplement les références
```cpp
void ServiceBaseImpl::statistiques(CORBA::Long& nombre, CORBA::Double& valeurTotale) {
    ...
    nombre       = rs->getInt("n");
    valeurTotale = rs->getDouble("total");
}
```

Java (client) : on passe des « boîtes » `Holder`
```java
import org.omg.CORBA.IntHolder;
import org.omg.CORBA.DoubleHolder;

IntHolder nombre = new IntHolder();
DoubleHolder total = new DoubleHolder();
base.statistiques(nombre, total);
System.out.println(nombre.value + " produits, valeur " + total.value);
```

Holders Java : `long` → `IntHolder`, `double` → `DoubleHolder`, `string` → `StringHolder`, `boolean` → `BooleanHolder`, struct `Produit` → `ProduitHolder` (généré).

> Plus simple si possible : renvoyer une **struct** qui contient les deux valeurs.

---

## 8. C++ appelle Java pendant un traitement (validation)

Le cas typique d'un aléa : « avant d'écrire en base, vérifier dans un fichier côté Java ». C'est le modèle de `Moderateur` (05) ou `Commande` (06).

```cpp
CORBA::Long ServiceBaseImpl::ajouter(const modele::Produit& p) {
    // 1) Demander a Java (lit data/autorises.txt)
    CORBA::Boolean autorise = true;
    try {
        CORBA::Object_var obj = nc->resolve_str("ServiceFichier");
        modele::ServiceFichier_var fichier = modele::ServiceFichier::_narrow(obj);
        autorise = fichier->contient("data/autorises.txt", p.nom);
    } catch (const CosNaming::NamingContext::NotFound&) {
        std::cout << "[C++] ServiceFichier jamais enregistre, pas de verification" << std::endl;
    } catch (const CORBA::SystemException& e) {
        // TRANSIENT / COMM_FAILURE : le nom existe mais le programme Java est ferme
        std::cout << "[C++] ServiceFichier injoignable (" << e._name() << "), pas de verification" << std::endl;
    }
    if (!autorise)
        throw modele::Refuse("produit non autorise");   // HORS du try

    // 2) ... travail MySQL (section 10) ...
}
```

Côté Java, le servant correspondant :
```java
public boolean contient(String fichier, String valeur) {
    for (String ligne : lireLignes(fichier)) {
        if (ligne.equalsIgnoreCase(valeur.trim())) return true;
    }
    return false;
}
```

> Choix à faire : si Java est absent, **accepter sans vérifier** (comme ici) ou **refuser** (`autorise = false;` dans les `catch`). Dis-le à l'oral, c'est une vraie décision de conception.

---

## 9. Callback / abonnement (C++ prévient Java)

Java crée l'objet et **donne sa référence** (pas d'annuaire) :
```java
public class NotifiableImpl extends NotifiablePOA {
    public void notifier(String evenement) {
        System.out.println("[Java] <<< Notification : " + evenement);
    }
}

// dans le main (le thread orb.run doit etre demarre, section 2) :
Notifiable notifRef = NotifiableHelper.narrow(poa.servant_to_reference(new NotifiableImpl()));
base.sAbonner(notifRef);
```

C++ garde la liste et notifie :
```cpp
void ServiceBaseImpl::sAbonner(modele::Notifiable_ptr abonne) {
    omni_mutex_lock lock(verrou);
    abonnes.push_back(modele::Notifiable::_duplicate(abonne));   // _duplicate : on garde une copie
}

void ServiceBaseImpl::notifierTous(const char* evenement) {
    omni_mutex_lock lock(verrou);
    for (auto it = abonnes.begin(); it != abonnes.end(); ) {
        try {
            (*it)->notifier(evenement);
            ++it;
        } catch (const CORBA::Exception&) {
            it = abonnes.erase(it);   // client ferme : on l'oublie
        }
    }
}

// usage, n'importe ou dans une methode :
notifierTous(("ajout de " + std::string(p.nom)).c_str());
```

---

## 10. MySQL en C++ (CRUD complet)

Includes et connexion (en haut du `.cc`) :
```cpp
#include <cppconn/driver.h>
#include <cppconn/connection.h>
#include <cppconn/statement.h>
#include <cppconn/prepared_statement.h>
#include <cppconn/resultset.h>
#include <memory>

static const char* DB_HOST = "tcp://127.0.0.1:3306";
static const char* DB_USER = "corba_user";      // À ADAPTER
static const char* DB_PASS = "corba";
static const char* DB_NAME = "corba_demo";

static std::unique_ptr<sql::Connection> connecter() {
    sql::Driver* driver = get_driver_instance();
    std::unique_ptr<sql::Connection> con(driver->connect(DB_HOST, DB_USER, DB_PASS));
    con->setSchema(DB_NAME);
    return con;
}

// Remplit une struct IDL a partir de la ligne courante (evite de repeter 4 lignes partout)
static void remplir(modele::Produit& p, sql::ResultSet* rs) {
    p.id       = rs->getInt("id");
    p.nom      = rs->getString("nom").c_str();
    p.quantite = rs->getInt("quantite");
    p.prix     = rs->getDouble("prix");
}
```

Table (`setup-mysql.sql`, à lancer avec `mysql -u corba_user -pcorba corba_demo < setup-mysql.sql`) :
```sql
CREATE TABLE IF NOT EXISTS produits (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    nom      VARCHAR(100) NOT NULL,
    quantite INT NOT NULL DEFAULT 0,
    prix     DOUBLE NOT NULL DEFAULT 0
);
```

### INSERT + récupérer l'id généré
```cpp
auto con = connecter();
std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
    "INSERT INTO produits (nom, quantite, prix) VALUES (?, ?, ?)"));
ps->setString(1, (const char*)p.nom);    // String_member -> const char*
ps->setInt(2, p.quantite);
ps->setDouble(3, p.prix);
ps->executeUpdate();

std::unique_ptr<sql::Statement> st(con->createStatement());
std::unique_ptr<sql::ResultSet> rs(st->executeQuery("SELECT LAST_INSERT_ID() AS id"));
rs->next();
CORBA::Long id = rs->getInt("id");
```

### SELECT une ligne (ou exception)
```cpp
std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
    "SELECT id, nom, quantite, prix FROM produits WHERE id = ?"));
ps->setInt(1, id);
std::unique_ptr<sql::ResultSet> rs(ps->executeQuery());
if (!rs->next())
    throw modele::Introuvable(id);
```

### SELECT plusieurs lignes → voir section 5. Avec filtre `LIKE` :
```cpp
std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
    "SELECT id, nom, quantite, prix FROM produits WHERE nom LIKE ? ORDER BY id"));
ps->setString(1, std::string("%") + motif + "%");
```

### UPDATE conditionnel : vérifier ET modifier en une requête
```cpp
std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
    "UPDATE produits SET quantite = quantite + ? WHERE id = ? AND quantite + ? >= 0"));
ps->setInt(1, delta);
ps->setInt(2, id);
ps->setInt(3, delta);
if (ps->executeUpdate() > 0)
    return;                                      // OK

// 0 ligne modifiee : id inexistant, ou quantite deviendrait negative
std::unique_ptr<modele::Produit> p(trouver(id)); // leve Introuvable si absent
throw modele::Refuse(("stock insuffisant : " + std::to_string(p->quantite)).c_str());
```
> Pourquoi une seule requête : un `SELECT` puis un `UPDATE` séparés laisseraient deux clients acheter le même dernier bonbon en même temps.

### DELETE
```cpp
std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
    "DELETE FROM produits WHERE id = ?"));
ps->setInt(1, id);
return ps->executeUpdate() > 0;                  // false = id inexistant
```

### Agrégats (COUNT, SUM, AVG…)
```cpp
std::unique_ptr<sql::ResultSet> rs(st->executeQuery(
    "SELECT COUNT(*) AS n, COALESCE(SUM(quantite * prix), 0) AS total FROM produits"));
rs->next();
CORBA::Long n = rs->getInt("n");
CORBA::Double total = rs->getDouble("total");   // COALESCE : 0 au lieu de NULL si table vide
```

### Attraper les erreurs MySQL
```cpp
try {
    ...
} catch (sql::SQLException& e) {
    std::cerr << "[C++] Erreur MySQL : " << e.what() << std::endl;
    throw modele::Refuse(e.what());   // renvoyer l'erreur a Java (sinon il recoit UNKNOWN)
}
```

---

## 11. Fichiers en Java

Imports :
```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
```

### Lire toutes les lignes non vides
```java
List<String> resultat = new ArrayList<>();
try {
    for (String ligne : Files.readAllLines(Paths.get(fichier), StandardCharsets.UTF_8)) {
        if (!ligne.trim().isEmpty()) resultat.add(ligne.trim());
    }
} catch (IOException e) {
    System.err.println("[Java] Lecture impossible " + fichier + " : " + e.getMessage());
}
```

### Fichier `cle=valeur`
```java
for (String ligne : lireLignes(fichier)) {
    String[] p = ligne.split("=", 2);
    if (p.length == 2 && p[0].trim().equals(cle)) {
        return Integer.parseInt(p[1].trim());
    }
}
throw new Refuse("cle absente : " + cle);
```

### CSV `nom;quantite;prix` (1re ligne = en-tête) → tableau de struct
```java
List<Produit> liste = new ArrayList<>();
List<String> lignes = Files.readAllLines(Paths.get(fichier), StandardCharsets.UTF_8);
for (int i = 1; i < lignes.size(); i++) {           // i = 1 : on saute l'en-tete
    String[] c = lignes.get(i).split(";");
    if (c.length < 3) continue;
    liste.add(new Produit(0, c[0].trim(), Integer.parseInt(c[1].trim()),
            Double.parseDouble(c[2].trim())));
}
Produit[] tableau = liste.toArray(new Produit[0]);
```

### Écrire (écrase le fichier)
```java
List<String> sortie = new ArrayList<>();
for (Produit x : base.lister()) sortie.add(x.nom + ";" + x.quantite);
Files.write(Paths.get("data/export.txt"), sortie, StandardCharsets.UTF_8);
```

### Ajouter une ligne à la fin (crée le fichier si besoin)
```java
Files.write(Paths.get(fichier), (ligne + "\n").getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
```

> Les chemins `data/...` sont relatifs au **dossier depuis lequel tu lances** `java`. Lance toujours depuis la racine du projet (voir section 15).

---

## 12. Fichiers en C++

```cpp
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

// Lire toutes les lignes non vides (vide si le fichier n'existe pas)
static std::vector<std::string> lireLignes(const std::string& chemin) {
    std::vector<std::string> lignes;
    std::ifstream f(chemin);
    std::string ligne;
    while (std::getline(f, ligne)) {
        if (!ligne.empty() && ligne.back() == '\r') ligne.pop_back();   // fichiers Windows
        if (!ligne.empty()) lignes.push_back(ligne);
    }
    return lignes;
}

// Decouper "a;b;c" -> {"a","b","c"}
static std::vector<std::string> decouper(const std::string& ligne, char sep) {
    std::vector<std::string> champs;
    std::stringstream ss(ligne);
    std::string champ;
    while (std::getline(ss, champ, sep)) champs.push_back(champ);
    return champs;
}

// Fichier "cle=valeur" -> valeur, ou defaut si absente
static std::string lireValeur(const std::string& chemin, const std::string& cle, const std::string& defaut) {
    for (const std::string& ligne : lireLignes(chemin)) {
        std::size_t pos = ligne.find('=');
        if (pos != std::string::npos && ligne.substr(0, pos) == cle)
            return ligne.substr(pos + 1);
    }
    return defaut;
}

// Ecraser (ajouter = false) ou ajouter a la fin (ajouter = true)
static void ecrire(const std::string& chemin, const std::string& texte, bool ajouter) {
    std::ofstream f(chemin, ajouter ? std::ios::app : std::ios::trunc);
    f << texte << "\n";
}
```

Usage (CSV) :
```cpp
auto lignes = lireLignes("data/produits.csv");
for (size_t i = 1; i < lignes.size(); i++) {          // saute l'en-tete
    auto c = decouper(lignes[i], ';');
    if (c.size() < 3) continue;
    std::string nom = c[0];
    int quantite    = std::stoi(c[1]);
    double prix     = std::stod(c[2]);
}
int n = std::stoi(lireValeur("data/config.txt", "bonbon", "0"));
```

---

## 13. Menu interactif Java

Pratique pour une démo devant le prof. Fichier complet : `modele-complet/java/src/Menu.java`.

```java
static void menu(ServiceBase base) {
    Scanner sc = new Scanner(System.in);
    while (true) {
        System.out.println("\n1) Lister  2) Ajouter  3) Retirer du stock  4) Supprimer  0) Quitter");
        System.out.print("Choix : ");
        if (!sc.hasNextLine()) return;              // Ctrl+D
        String choix = sc.nextLine().trim();
        try {
            switch (choix) {
                case "1":
                    for (Produit p : base.lister())
                        System.out.println("#" + p.id + " " + p.nom + " x" + p.quantite);
                    break;
                case "2":
                    System.out.print("Nom : ");      String nom = sc.nextLine().trim();
                    System.out.print("Quantite : "); int q = Integer.parseInt(sc.nextLine().trim());
                    System.out.print("Prix : ");     double prix = Double.parseDouble(sc.nextLine().trim());
                    System.out.println("Ajoute, id = " + base.ajouter(new Produit(0, nom, q, prix)));
                    break;
                case "3":
                    System.out.print("Id : ");       int id = Integer.parseInt(sc.nextLine().trim());
                    System.out.print("Combien : ");  int n = Integer.parseInt(sc.nextLine().trim());
                    base.modifierQuantite(id, -n);
                    System.out.println("OK");
                    break;
                case "4":
                    System.out.print("Id : ");
                    System.out.println(base.supprimer(Integer.parseInt(sc.nextLine().trim())) ? "Supprime" : "Id inconnu");
                    break;
                case "0":
                    return;
                default:
                    System.out.println("Choix invalide");
            }
        } catch (Refuse e) {
            System.out.println("Refuse : " + e.raison);
        } catch (Introuvable e) {
            System.out.println("Introuvable : id " + e.id);
        } catch (NumberFormatException e) {
            System.out.println("Nombre attendu");
        } catch (org.omg.CORBA.SystemException e) {
            System.out.println("Serveur C++ injoignable : " + e);
        }
    }
}
```

> Si Java sert aussi un objet (callback, `ServiceFichier`), le thread `orb.run` doit être démarré **avant** d'appeler `menu(...)`.

---

## 14. Pièges C++ (mémoire, strings)

| Situation | À écrire | Pourquoi |
|---|---|---|
| Publier un nom | `CosNaming::Name_var nom = nc->to_name("X"); nc->rebind(nom, ref);` | `to_name` renvoie un **pointeur** : `rebind(nc->to_name(..))` ne compile pas |
| Garder une référence reçue en paramètre | `m::X::_duplicate(param)` | le paramètre n'est « prêté » que pendant l'appel |
| Renvoyer une struct / sequence | `new m::Produit()` / `new m::ListeProduits()` | l'ORB libère après envoi |
| Renvoyer un `string` | `return CORBA::string_dup(s.c_str());` | idem, copie allouée pour l'ORB |
| Recevoir un `string` d'un appel | `CORBA::String_var s = obj->methode();` | libère tout seul |
| Recevoir une struct / sequence d'un appel | `m::ListeTextes_var l = obj->methode();` | libère tout seul |
| Champ string d'une struct ← `std::string` | `p.nom = s.c_str();` | copie automatique |
| Champ string d'une struct → `std::string` | `std::string(p.nom)` ou `(const char*)p.nom` | |
| Lever une exception avec un `std::string` | `throw m::Refuse(s.c_str());` | |
| Liste partagée entre plusieurs appels | `omni_mutex` + `omni_mutex_lock lock(verrou);` | chaque appel entrant a son propre thread |

---

## 15. Compiler et lancer

Toujours depuis **la racine du projet** (le dossier qui contient `modele.idl`, `java/`, `cpp/`, `data/`).

```bash
cd corba-test/07-mon-alea

# 0. Une fois : la table
mysql -u corba_user -pcorba corba_demo < setup-mysql.sql

# 1. Après chaque modification de l'IDL : régénérer LES DEUX côtés
(cd java/src && idlj -fall ../../modele.idl)
(cd cpp/src && omniidl -bcxx ../../modele.idl)

# 2. Compiler
mkdir -p java/bin
(cd java/src && javac -d ../bin modele/*.java *.java)
(cd cpp && make)

# 3. Lancer (2 terminaux, depuis la racine du projet)
./cpp/bin/cpp_peer -ORBInitRef NameService=corbaname::localhost:2809
java -cp java/bin JavaPeer -ORBInitRef NameService=corbaname::localhost:2809
# ou le menu :
java -cp java/bin Menu -ORBInitRef NameService=corbaname::localhost:2809
```

Makefile C++ (ajoute tes nouveaux `.cc` dans `SRCS`) :
```make
CXX = g++
CXXFLAGS = -Isrc -Wall
LIBS = -lomniORB4 -lomnithread -lmysqlcppconn
BIN = bin/cpp_peer

SRCS = src/modeleSK.cc src/ServiceBaseImpl.cc src/cpp_peer.cc

all: $(BIN)

$(BIN): $(SRCS) src/ServiceBaseImpl.hh
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -o $(BIN) $(SRCS) $(LIBS)

clean:
	rm -f $(BIN)

.PHONY: all clean
```

| Erreur | Cause | Remède |
|---|---|---|
| `impossible de trouver ou charger la classe principale` | lancé depuis le mauvais dossier | `cd` à la racine du projet |
| `javac: directory not found: ../bin` | dossier de sortie absent | `mkdir -p java/bin` |
| `NotFound` | l'autre programme n'est pas lancé | lancer l'autre d'abord, ou section 3 |
| `TRANSIENT` / `COMM_FAILURE` | nom périmé (programme fermé) | relancer l'autre programme ; `catch` section 3 |
| `terminate called after throwing…` (C++) | exception non attrapée dans `main` | `try { } catch (const CORBA::Exception& e)` |
| `UNKNOWN` reçu par Java | exception C++ non-IDL (ex. `sql::SQLException`) sortie d'un servant | l'attraper et lever une exception IDL |
| Java lit un fichier vide / valeur 0 | chemin relatif, mauvais dossier de lancement | lancer depuis la racine du projet |
| méthode ajoutée à l'IDL introuvable | un seul côté régénéré | régénérer **et** recompiler les deux |

---

## 16. Aléa → quelle recette ?

| Le prof demande… | Recettes |
|---|---|
| « Ajouter une fonction qui renvoie un enregistrement de la base » | 1 (struct) + 4 + 10 SELECT une ligne + 6 (Introuvable) |
| « Lister / filtrer / trier » | 5 + 10 SELECT / LIKE (change le `ORDER BY` / `WHERE`) |
| « Importer un fichier dans la base » | 11 CSV (Java) → 5 envoyer une liste → 5 C++ reçoit + 10 INSERT |
| « Exporter la base dans un fichier » | 5 C++ renvoie une liste → 11 écrire (Java) |
| « Vérifier dans un fichier avant d'écrire en base » | 8 (C++ appelle Java) + 11 lire lignes |
| « Refuser si… (stock, droit, mot interdit) » | 6 + 10 UPDATE conditionnel |
| « Prévenir les autres clients » | 9 (callback) |
| « Statistiques : moyenne, total, nombre » | 10 agrégats + 7 (out) ou une struct |
| « Journaliser les opérations » | 12 `ecrire(..., true)` (C++) ou 11 ajouter une ligne (Java) |
| « Menu utilisateur » | 13 |

Checklist à chaque nouvelle fonction :
1. Ajouter la méthode dans l'IDL (dans l'interface de **celui qui possède la ressource**).
2. Régénérer les deux côtés (section 15).
3. Implémenter dans le servant (signature : section 1).
4. Appeler depuis l'autre côté, **dans un try/catch**.
5. Recompiler les deux côtés, lancer depuis la racine du projet.
