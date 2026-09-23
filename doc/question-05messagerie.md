- pourquoi est ce que l'on n'as pas mis dans l'annuaire l'objet notifiable ?

reponse: Parce que le C++ **a déjà la référence** : Java la lui donne en paramètre de `sAbonner(monNotifiable)`. L'annuaire ne sert qu'à une chose : **trouver un objet dont on n'a encore aucune référence** (le "premier contact").

Il y a 3 façons d'obtenir une référence CORBA vers un objet distant :

| Moyen | Exemple dans le projet |
|---|---|
| 1. Chercher un **nom** dans l'annuaire (`resolve_str`) | Java trouve `Messagerie`, C++ trouve `Moderateur` |
| 2. La recevoir comme **paramètre** ou **valeur de retour** d'un appel CORBA | C++ reçoit `Notifiable` via `sAbonner(in Notifiable abonne)` |
| 3. Une chaîne IOR (`object_to_string` / `string_to_object`) copiée dans un fichier | pas utilisé ici |

Et il y a une 2e raison, plus importante : **il y a un Notifiable par client** (Alice, Bob…). Si chacun faisait `rebind("Notifiable", ...)`, chaque nouveau client **écraserait** le précédent dans l'annuaire : le C++ ne pourrait notifier que le dernier. Avec `sAbonner`, le C++ garde une liste (`std::vector<chat::Notifiable_var> abonnes`) avec toutes les références.

Pourquoi le `Moderateur`, lui, est dans l'annuaire ? Parce qu'aucun appel Java → C++ ne le transporte en paramètre : le C++ n'a pas d'autre moyen de le trouver. (On aurait très bien pu ajouter `sAbonner(in Notifiable n, in Moderateur m)` et ne plus l'inscrire dans l'annuaire.)

À retenir : **annuaire = objet "public" unique, connu par son nom. Paramètre = objet "privé", ou objet dont il existe plusieurs exemplaires.**

- est ce que si je veux pouvoir appleer une fonction, resultat d'une fonction de Java <-> cpp je dois mettre dans l'annuaire

reponse: Non. Deux précisions :

1. **On n'inscrit jamais une fonction, on inscrit un objet** (une interface IDL). Quand tu as la référence de l'objet, tu as accès à **toutes** ses méthodes. `Messagerie` est inscrit une seule fois, et Java peut appeler `envoyer()` ET `sAbonner()`.
2. **Le résultat d'une fonction** (valeur de retour, paramètre `out`, exception) revient **automatiquement** à l'appelant par la même connexion. Rien à inscrire : `String mot = moderateur.chercherMotTabou(contenu);` → `mot` arrive tout seul côté C++.

Tu as besoin de l'annuaire seulement si **l'appelant n'a aucun autre moyen d'obtenir la référence de l'objet** (voir la question précédente). En pratique : on inscrit 1 objet "point d'entrée" par application, et le reste circule en paramètre.

- explique moi bien comment est la syntaxe pour la mise en place de l'appel de la fonction depuis ORB et pour qu'une fonction peut etre appeler.
 ORB orb = ORB.init(args, null);
=> initialisation orb 
... comment exactement explique simplement

reponse: Il y a toujours les mêmes étapes. Une partie commune, puis une partie **serveur** (rendre une méthode appelable) et une partie **client** (appeler une méthode).

Image pour comprendre :
- **ORB** = le standard téléphonique (il transporte les appels sur le réseau)
- **POA** = la secrétaire qui reçoit les appels entrants et les passe au bon objet
- **Servant** (`XxxImpl`) = l'employé qui fait vraiment le travail
- **Référence** = le numéro de téléphone de l'employé (ce qu'on donne aux autres)
- **Annuaire (NameService / omniNames)** = l'annuaire papier : un nom → un numéro

**Étape 0 — commun (toujours)**

```java
// JAVA
ORB orb = ORB.init(args, null);   // démarre le standard. args contient -ORBInitRef NameService=...
                                  // => c'est là que l'ORB apprend OÙ est l'annuaire
NamingContextExt nc = NamingContextExtHelper.narrow(
        orb.resolve_initial_references("NameService"));   // ouvre l'annuaire
```
```cpp
// C++
CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);
CORBA::Object_var nsObj = orb->resolve_initial_references("NameService");
CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);
```

**Partie SERVEUR — pour qu'une méthode puisse être appelée (4 étapes)**

```java
// JAVA
// S1. Récupérer la secrétaire (POA) et l'ouvrir (sinon les appels restent en attente)
POA poa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
poa.the_POAManager().activate();

// S2. Créer l'employé : une classe qui extends XxxPOA (généré par idlj)
ModerateurImpl impl = new ModerateurImpl();

// S3. Obtenir son "numéro" (la référence CORBA)
Moderateur ref = ModerateurHelper.narrow(poa.servant_to_reference(impl));

// S4. Publier le numéro : annuaire (rebind) OU le passer en paramètre à l'autre
nc.rebind(nc.to_name("Moderateur"), ref);

// S5. Écouter les appels entrants (bloque pour toujours)
orb.run();
```
```cpp
// C++
CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");       // S1
PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
poa->the_POAManager()->activate();

MessagerieImpl* impl = new MessagerieImpl(nc);                                // S2 (hérite de POA_chat::Messagerie)
PortableServer::ObjectId_var id = poa->activate_object(impl);
chat::Messagerie_var ref = impl->_this();                                     // S3

nc->rebind(nc->to_name("Messagerie"), ref);                                   // S4
orb->run();                                                                   // S5
```

**Partie CLIENT — pour appeler une méthode (3 étapes)**

```java
// JAVA
org.omg.CORBA.Object obj = nc.resolve_str("Messagerie");  // C1. chercher le nom -> objet "générique"
Messagerie m = MessagerieHelper.narrow(obj);              // C2. le convertir dans le bon type
m.envoyer("Alice", "bonjour");                            // C3. appeler comme une méthode normale
```
```cpp
// C++
CORBA::Object_var obj = nc->resolve_str("Moderateur");                 // C1
chat::Moderateur_var mod = chat::Moderateur::_narrow(obj);             // C2
CORBA::String_var mot = mod->chercherMotTabou(contenu);                // C3
```

Pourquoi `narrow` ? `resolve_str` ne sait pas quel type d'objet il renvoie (l'annuaire contient de tout) : il rend un `Object` générique. `narrow` = "je vérifie que c'est bien un `Messagerie` et je le convertis", comme un cast. Sans ça tu ne peux pas appeler `envoyer()`.

**Cas "les deux à la fois" (Java client ET serveur)** : `orb.run()` bloque. Donc on le lance dans un thread à part, et le thread principal fait la partie client :
```java
Thread t = new Thread(orb::run); t.setDaemon(true); t.start();
```
En C++ c'est inutile dans notre projet : omniORB traite chaque appel entrant dans son propre thread, et les appels C++ → Java sont faits **à l'intérieur** d'une méthode servante (`envoyer`).

- est ce que je dois donc connaitre le nom des services partager entre les deux applications et connaitre NamingContextExtHelper...., ModerateurHelper
comment est ce que je sais sur quoi je dois me referencier ?
si j'ai des fonctions deux part et d'autre: fonction acheter bobon dans Java a besoin de la verification stock C++ depuis la base mysql, si oui decrementer le stock de bonbon par le nombre de bonbon dans le fichier. 
par exemple donne moi comment j'implement depuis java et cpp les deux server/client en meme temps. 
explique moi globalement comment plus les syntaxe ou  je dois m

reponse:

**1) Ce qu'il faut connaître — 3 catégories, et d'où ça vient**

| Quoi | D'où ça vient | Exemple |
|---|---|---|
| Les classes fixes de CORBA | toujours les mêmes, à apprendre une fois (c'est le "copier-coller" de l'étape 0 et S1) | `ORB`, `POAHelper`, `NamingContextExtHelper`, `"RootPOA"`, `"NameService"` |
| Les classes générées | **déduites de l'IDL** par une règle fixe (voir ci-dessous) | `ModerateurHelper`, `ModerateurPOA`, `chat::Moderateur_var` |
| Les noms dans l'annuaire | **une convention que TU choisis** : une simple chaîne que les deux programmes doivent écrire pareil | `"Messagerie"`, `"Moderateur"` |

La règle : pour une interface `X` dans un `module m` de l'IDL :

| Je veux… | Java (idlj) | C++ (omniidl) |
|---|---|---|
| **servir** X (écrire le code) | `class XImpl extends XPOA` | `class XImpl : public POA_m::X` |
| **appeler** X (convertir la référence) | `X x = XHelper.narrow(obj);` | `m::X_var x = m::X::_narrow(obj);` |
| type d'une référence | `X` | `m::X_var` (variable) / `m::X_ptr` (paramètre) |
| exception `E` | `catch (E e)` | `throw m::E(champ);` |

Donc : **l'IDL est ta seule référence.** Tu lis l'IDL, tu vois `interface Moderateur` → tu sais que tu auras `ModerateurHelper` / `ModerateurPOA` / `chat::Moderateur_var`. Le nom `"Moderateur"` dans l'annuaire, c'est juste une habitude de lui donner le même nom que l'interface.

Et pour savoir **qui sert quoi** : *celui qui possède la ressource sert l'interface qui y donne accès.* MySQL est côté C++ → C++ sert `Stock`. Le fichier est côté Java → Java sert `Commande`.

**2) Exemple bonbons, Java ET C++ client+serveur**

Scénario : Java veut acheter des bonbons → appelle `acheter("bonbon")` sur le C++ → le C++ demande à Java combien en acheter (lu dans `data/commande.txt`) → vérifie le stock MySQL → décrémente ou refuse.

```
 JAVA (JavaBoutique)                          C++ (cpp_boutique)
 sert : Commande                              sert : Stock
   |                                              |
   |-- 1. stock.acheter("bonbon") --------------->|
   |                                              |-- 2. resolve_str("Commande")
   |<-- 3. commande.quantiteDemandee("bonbon") ---|
   |--- 4. renvoie 5 (lu dans commande.txt) ----->|
   |                                              |-- 5. MySQL : stock >= 5 ? UPDATE -5
   |<-- 6. renvoie stock restant, ou exception ---|
```

`boutique.idl` (le contrat, commun aux deux)
```idl
module boutique {
    exception StockInsuffisant {
        long disponible;
    };

    // Servi par JAVA : lit data/commande.txt. C'est C++ qui l'appelle.
    interface Commande {
        long quantiteDemandee(in string produit);
    };

    // Servi par C++ : verifie et decremente le stock MySQL. C'est Java qui l'appelle.
    interface Stock {
        long acheter(in string produit) raises (StockInsuffisant);  // renvoie le stock restant
    };
};
```
Générer : `cd java/src && idlj -fall ../../boutique.idl` et `cd cpp/src && omniidl -bcxx ../../boutique.idl`.

MySQL :
```sql
CREATE TABLE stock (produit VARCHAR(50) PRIMARY KEY, quantite INT NOT NULL);
INSERT INTO stock VALUES ('bonbon', 20);
```
`data/commande.txt` :
```
bonbon=5
```

**Côté C++ — serveur de `Stock` + client de `Commande`**

`StockImpl.hh`
```cpp
#include "boutique.hh"

class StockImpl : public POA_boutique::Stock {       // je SERS Stock
    CosNaming::NamingContextExt_var nc;              // pour retrouver Commande (Java)
public:
    StockImpl(CosNaming::NamingContextExt_ptr annuaire)
        : nc(CosNaming::NamingContextExt::_duplicate(annuaire)) {}
    CORBA::Long acheter(const char* produit);
};
```
`StockImpl.cc`
```cpp
#include "StockImpl.hh"
#include <cppconn/driver.h>
#include <cppconn/connection.h>
#include <cppconn/prepared_statement.h>
#include <cppconn/resultset.h>
#include <memory>

CORBA::Long StockImpl::acheter(const char* produit) {
    // --- PARTIE CLIENTE : j'appelle Java pour connaitre la quantite ---
    CORBA::Object_var obj = nc->resolve_str("Commande");
    boutique::Commande_var commande = boutique::Commande::_narrow(obj);
    CORBA::Long qte = commande->quantiteDemandee(produit);

    // --- Travail local : MySQL ---
    sql::Driver* driver = get_driver_instance();
    std::unique_ptr<sql::Connection> con(driver->connect("tcp://127.0.0.1:3306", "corba_user", "corba"));
    con->setSchema("corba_demo");

    // Verifier ET decrementer en une seule requete : si le stock est insuffisant, 0 ligne modifiee
    std::unique_ptr<sql::PreparedStatement> maj(con->prepareStatement(
        "UPDATE stock SET quantite = quantite - ? WHERE produit = ? AND quantite >= ?"));
    maj->setInt(1, qte);
    maj->setString(2, produit);
    maj->setInt(3, qte);
    int modifiees = maj->executeUpdate();

    // Lire le stock actuel (pour le renvoyer ou le mettre dans l'exception)
    std::unique_ptr<sql::PreparedStatement> sel(con->prepareStatement(
        "SELECT quantite FROM stock WHERE produit = ?"));
    sel->setString(1, produit);
    std::unique_ptr<sql::ResultSet> rs(sel->executeQuery());
    CORBA::Long restant = rs->next() ? rs->getInt(1) : 0;

    if (modifiees == 0)
        throw boutique::StockInsuffisant(restant);   // traverse le reseau jusqu'au catch Java
    return restant;
}
```
`cpp_boutique.cc`
```cpp
#include "boutique.hh"
#include "StockImpl.hh"
#include <iostream>

int main(int argc, char** argv) {
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);                          // 0. ORB

    CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");     // S1. POA
    PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
    poa->the_POAManager()->activate();

    CORBA::Object_var nsObj = orb->resolve_initial_references("NameService");  // 0. annuaire
    CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);

    StockImpl* impl = new StockImpl(nc);                                       // S2. servant
    PortableServer::ObjectId_var id = poa->activate_object(impl);
    boutique::Stock_var ref = impl->_this();                                   // S3. reference
    nc->rebind(nc->to_name("Stock"), ref);                                     // S4. publier
    std::cout << "[C++] Stock enregistre" << std::endl;

    orb->run();                                                                // S5. ecouter
    return 0;
}
```
Makefile : même que le 05 avec `SRCS = src/boutiqueSK.cc src/StockImpl.cc src/cpp_boutique.cc`.

**Côté Java — serveur de `Commande` + client de `Stock`**

`CommandeImpl.java`
```java
import boutique.CommandePOA;
import java.nio.file.*;

public class CommandeImpl extends CommandePOA {      // je SERS Commande
    public int quantiteDemandee(String produit) {    // IDL "long" = Java "int"
        try {
            for (String ligne : Files.readAllLines(Paths.get("data", "commande.txt"))) {
                String[] p = ligne.split("=");       // "bonbon=5"
                if (p.length == 2 && p[0].trim().equals(produit))
                    return Integer.parseInt(p[1].trim());
            }
        } catch (Exception e) {
            System.err.println("[Java] Lecture commande.txt impossible : " + e.getMessage());
        }
        return 0;
    }
}
```
`JavaBoutique.java`
```java
import boutique.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.*;
import org.omg.PortableServer.*;

public class JavaBoutique {
    public static void main(String[] args) throws Exception {
        ORB orb = ORB.init(args, null);                                          // 0. ORB

        POA poa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));   // S1. POA
        poa.the_POAManager().activate();

        NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));                  // 0. annuaire

        // --- PARTIE SERVEUR : Commande, que C++ va appeler ---
        CommandeImpl impl = new CommandeImpl();                                  // S2
        Commande cmdRef = CommandeHelper.narrow(poa.servant_to_reference(impl)); // S3
        nc.rebind(nc.to_name("Commande"), cmdRef);                               // S4

        Thread t = new Thread(orb::run);   // S5 dans un thread : le main reste libre pour la partie client
        t.setDaemon(true);
        t.start();

        // --- PARTIE CLIENTE : j'appelle Stock (C++) ---
        Stock stock = StockHelper.narrow(nc.resolve_str("Stock"));               // C1 + C2
        try {
            int restant = stock.acheter("bonbon");                               // C3
            System.out.println("[Java] Achat OK, il reste " + restant + " bonbons");
        } catch (StockInsuffisant e) {
            System.out.println("[Java] Stock insuffisant, seulement " + e.disponible + " disponibles");
        }
        orb.shutdown(false);
    }
}
```

Lancer (omniNames tourne déjà) — **C++ d'abord**, puisque Java cherche `"Stock"` :
```bash
./cpp/bin/cpp_boutique -ORBInitRef NameService=corbaname::localhost:2809
java -cp java/bin JavaBoutique -ORBInitRef NameService=corbaname::localhost:2809
```

**3) La méthode générale, à refaire pour n'importe quel sujet**

1. Pour chaque action, se demander **qui possède la donnée** (MySQL → C++, fichier → Java). Celui-là **sert** une interface.
2. Écrire l'IDL : une `interface` par côté serveur, les `exception` pour les cas d'erreur.
3. Générer (`idlj -fall`, `omniidl -bcxx`), puis dans chaque langage :
   - servir = `XImpl extends XPOA` / `: public POA_m::X` + étapes S1→S5
   - appeler = `resolve_str("X")` + `XHelper.narrow` / `m::X::_narrow` + appel
4. Décider comment chacun trouve l'autre : **annuaire** si c'est un objet unique connu d'avance, **paramètre** si l'autre peut te le donner pendant un appel (ou s'il y en a plusieurs).
5. Ordre de lancement : celui dont on cherche le nom en premier doit être démarré avant.

Remarque : ici on aurait pu faire plus simple — Java lit le fichier lui-même et appelle `acheter("bonbon", 5)`. Le C++ n'aurait alors plus besoin d'appeler Java. On passe par `Commande` uniquement pour avoir un vrai client/serveur des deux côtés, exactement comme `Moderateur` dans le 05.
