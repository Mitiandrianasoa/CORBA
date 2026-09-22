# Dictionnaire CORBA — Java ↔ C++

> **Ce fichier a été remplacé par une page web mieux mise en page** (code coloré par langage, navigation cliquable, comparaisons Java/C++ côte à côte lisibles) :
> **https://claude.ai/artifact/LRiWFo9Uex9mbYkuNxymQD**
>
> Le contenu ci-dessous reste en local à titre de sauvegarde/texte brut, mais utilise le lien ci-dessus pour la version à jour et agréable à lire.

---

Référence pour comprendre et manipuler la communication entre applications Java et C++. Tous les exemples viennent des projets réels du dossier (`01-hello-java/`, `02-bidirectionnel/`).

## Sommaire

- [1. Glossaire des concepts](#1-glossaire-des-concepts)
- [2. Types de données (IDL ↔ Java ↔ C++)](#2-types-de-donnees-idl--java--c)
- [3. Passage des paramètres (in / out / inout)](#3-passage-des-parametres-in--out--inout)
- [4. Les 7 opérations courantes, Java vs C++](#4-les-7-operations-courantes-java-vs-c)
  - [4.1 Démarrer l'ORB](#41-demarrer-lorb)
  - [4.2 Activer le POA](#42-activer-le-poa)
  - [4.3 Créer le servant](#43-creer-le-servant-et-obtenir-sa-reference-corba)
  - [4.4 S'enregistrer dans l'annuaire](#44-senregistrer-dans-lannuaire-le-serveur-se-fait-connaitre)
  - [4.5 Retrouver un service par son nom](#45-retrouver-un-service-par-son-nom-le-client-cherche)
  - [4.6 Appeler une méthode distante](#46-appeler-une-methode-distante-lappel-qui-traverse-le-reseau)
  - [4.7 Rester actif](#47-rester-actif-pour-repondre-aux-appels-entrants)
- [5. Gestion des erreurs](#5-gestion-des-erreurs)
- [6. Commandes CLI récapitulatives](#6-commandes-cli-recapitulatives)
- [7. Checklist : ajouter une nouvelle fonction partagée](#7-checklist--ajouter-une-nouvelle-fonction-partagee)

---

## 1. Glossaire des concepts

**IDL** (Interface Definition Language)
Le fichier `.idl` : la liste des fonctions partagées, écrite dans un langage neutre (ni Java ni C++). C'est le seul fichier que les deux langages lisent en commun.
Voir : `Hello.idl`, `app.idl`

**idlj**
L'outil qui traduit un `.idl` en fichiers **Java** (stubs + squelettes).
Commande : `idlj -fall app.idl`

**omniidl**
L'outil qui traduit un `.idl` en fichiers **C++** (stubs + squelettes).
Commande : `omniidl -bcxx app.idl`

**ORB** (Object Request Broker)
La bibliothèque (déjà installée) qui envoie/reçoit les messages sur le réseau. Chaque programme (Java ET C++) démarre sa propre copie en mémoire.
Voir : `ORB.init(...)` (Java), `CORBA::ORB_init(...)` (C++)

**POA** (Portable Object Adapter)
Le gestionnaire, côté serveur, qui active les objets et les relie à une vraie implémentation.
Voir : `POAHelper.narrow(...)` (Java), `PortableServer::POA::_narrow(...)` (C++)

**Servant**
La classe qui contient le VRAI code métier (ce que fait vraiment la fonction).
Voir : `FileServiceImpl.java`, `DataServiceImpl.cc`

**Stub**
Le "messager" côté CLIENT, généré automatiquement, qui empaquette un appel et l'envoie sur le réseau.
Voir : `_FileServiceStub.java` (Java, généré) ; côté C++ c'est intégré dans `appSK.cc`

**Skeleton**
Le "réceptionniste" côté SERVEUR, généré automatiquement, qui reçoit un message réseau et appelle la vraie fonction du servant.
Voir : `FileServicePOA.java` (Java) ; classe `POA_app::FileService` dans `app.hh` (C++)

**Helper**
Classe utilitaire générée qui sert à "caster" un objet générique CORBA vers ton interface précise.
Exemple : `FileServiceHelper.narrow(...)`

**Holder**
Classe "boîte" générée, utilisée uniquement pour les paramètres `out`/`inout` (voir section 3).
Exemple : `FileServiceHolder.java`

**Naming Service**
L'annuaire qui associe un NOM (ex: `"FileService"`) à une adresse réseau, pour que le client n'ait pas besoin de connaître l'IP/le port en dur.
Voir : `omniNames` (utilisé par les 2 langages dans `02-bidirectionnel`)

**IIOP**
Le protocole réseau (par-dessus TCP/IP) que l'ORB utilise pour transporter les messages CORBA. Invisible dans le code, géré automatiquement par l'ORB.

**Module** (IDL)
Équivalent d'un `package` (Java) / `namespace` (C++) — regroupe des interfaces.
`module app { ... };` → devient `package app;` en Java, `namespace app` en C++

**Interface** (IDL)
Le contrat d'un service — équivalent d'une interface Java / classe abstraite C++.
Exemple : `interface FileService { ... };`

**Narrow**
L'opération qui convertit une référence CORBA générique vers ton type précis (comme un `(Type) objet` en Java).
Exemple : `FileServiceHelper.narrow(ref)`, `app::FileService::_narrow(obj)`

**corbaloc / corbaname**
Une "URL" pour joindre directement un service CORBA sans passer par un fichier de config.
Exemple : `corbaname::localhost:2809`

**_var** (C++ uniquement)
Un type "pointeur intelligent" généré par omniidl qui gère automatiquement la mémoire (libère tout seul).
Exemple : `CORBA::ORB_var`, `CosNaming::Name_var`

[↑ Retour au sommaire](#sommaire)

---

## 2. Types de données (IDL ↔ Java ↔ C++)

Quand tu écris une méthode dans `.idl`, voici comment chaque type se traduit des deux côtés :

| Type IDL | Type Java | Type C++ |
|---|---|---|
| `string` | `String` | `char*` (entrée) / `CORBA::String_var` (retour) |
| `long` | `int` | `CORBA::Long` |
| `long long` | `long` | `CORBA::LongLong` |
| `short` | `short` | `CORBA::Short` |
| `boolean` | `boolean` | `CORBA::Boolean` |
| `double` | `double` | `CORBA::Double` |
| `float` | `float` | `CORBA::Float` |
| `octet` | `byte` | `CORBA::Octet` |
| `void` | `void` | `void` |
| `sequence<T>` | tableau `T[]` | classe générée type liste |
| `struct {...}` | classe générée (champs publics) | classe générée (champs publics) |

**Exemples d'usage :**
- `string` → un texte, ex: `string readMessage();`
- `long` → un identifiant ou un compteur
- `boolean` → un vrai/faux
- `sequence<T>` → une liste de résultats (ex: plusieurs lignes MySQL)
- `struct` → un enregistrement à plusieurs champs (ex: une ligne de table MySQL)

> Utile pour la suite : quand tu voudras que `DataService` renvoie plusieurs lignes MySQL (pas juste une string), tu ajouteras un `struct` (ex: `struct Ligne { string cle; string valeur; };`) et un `sequence<Ligne>` dans `app.idl`.

[↑ Retour au sommaire](#sommaire)

---

## 3. Passage des paramètres (in / out / inout)

| Mot-clé IDL | Signification | Effet Java | Effet C++ |
|---|---|---|---|
| `in` | Le client ENVOIE une valeur, rien en retour pour ce paramètre | paramètre normal | `const char*` (ou type normal) |
| `out` | Le serveur RENVOIE une valeur via ce paramètre | `XxxHolder` requis | pointeur / référence |
| `inout` | Les deux sens : envoyé puis modifié/renvoyé | `XxxHolder` requis | référence modifiable |

**Exemple concret** (pas encore dans nos fichiers, mais utile à connaître) :
```idl
void getStatus(in string cle, out string valeur);
```

Côté Java, l'appel utilise un `Holder` :
```java
StringHolder valeurHolder = new StringHolder();
service.getStatus("monCle", valeurHolder);
System.out.println(valeurHolder.value);
```

Dans nos exemples actuels (`readMessage()`, `getData()`), on n'utilise QUE des `in` et des valeurs de retour normales (`return`) — c'est le cas le plus simple, suffisant pour la plupart des besoins.

[↑ Retour au sommaire](#sommaire)

---

## 4. Les 7 opérations courantes, Java vs C++

Chaque sous-section montre le code Java puis le code C++ équivalent, tirés directement de `JavaPeer.java` et `cpp_peer.cc`.

### 4.1 Démarrer l'ORB

Java :
```java
ORB orb = ORB.init(args, null);
```

C++ :
```cpp
CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);
```

### 4.2 Activer le POA

Java :
```java
POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
rootpoa.the_POAManager().activate();
```

C++ :
```cpp
CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");
PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
poa->the_POAManager()->activate();
```

### 4.3 Créer le servant et obtenir sa référence CORBA

Java :
```java
FileServiceImpl impl = new FileServiceImpl();
Object ref = rootpoa.servant_to_reference(impl);
FileService href = FileServiceHelper.narrow(ref);
```

C++ :
```cpp
DataServiceImpl* impl = new DataServiceImpl();
poa->activate_object(impl);
app::DataService_var ref = impl->_this();
```

### 4.4 S'enregistrer dans l'annuaire (le serveur se fait connaître)

Java :
```java
NamingContextExt nc = NamingContextExtHelper.narrow(
        orb.resolve_initial_references("NameService"));
nc.rebind(nc.to_name("FileService"), href);
```

C++ :
```cpp
CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(
        orb->resolve_initial_references("NameService"));
CosNaming::Name_var name = nc->to_name("DataService");
nc->rebind(name, ref);
```

### 4.5 Retrouver un service par son nom (le client cherche)

Java :
```java
DataService data = DataServiceHelper.narrow(nc.resolve_str("DataService"));
```

C++ :
```cpp
CORBA::Object_var obj = nc->resolve_str("FileService");
app::FileService_var file = app::FileService::_narrow(obj);
```

### 4.6 Appeler une méthode distante (l'appel qui traverse le réseau)

Java :
```java
String reponse = data.getData();
```

C++ :
```cpp
CORBA::String_var reponse = file->readMessage();
```

### 4.7 Rester actif pour répondre aux appels entrants

Java :
```java
orb.run();
```

C++ :
```cpp
orb->run();
```

[↑ Retour au sommaire](#sommaire)

---

## 5. Gestion des erreurs

| | Java | C++ |
|---|---|---|
| Type d'exception CORBA | `org.omg.CORBA.SystemException` (ex: `OBJECT_NOT_EXIST`, `COMM_FAILURE`) | `CORBA::Exception` (ex: `CORBA::OBJECT_NOT_EXIST`, `CORBA::COMM_FAILURE`) |
| "Le nom n'existe pas dans l'annuaire" | `org.omg.CosNaming.NamingContextPackage.NotFound` | `CosNaming::NamingContext::NotFound` |

**Exemple de capture, Java** (`JavaPeer.java`) :
```java
try {
    // appel distant
} catch (Exception e) {
    System.out.println("DataService pas encore disponible : " + e);
}
```

**Exemple de capture, C++** (`cpp_peer.cc`) :
```cpp
try {
    // appel distant
} catch (const CORBA::Exception& e) {
    std::cout << "FileService pas encore disponible" << std::endl;
}
```

**Astuce pratique** : si l'autre programme (Java ou C++) n'est pas encore lancé au moment de l'appel, tu obtiens une exception (`NotFound` si le nom n'a jamais été enregistré, ou une erreur de communication si le nom existe mais que le programme est mort) — c'est normal, prévois toujours un `try/catch` autour d'un appel distant.

[↑ Retour au sommaire](#sommaire)

---

## 6. Commandes CLI récapitulatives

| Action | Java | C++ |
|---|---|---|
| Générer depuis l'IDL | `idlj -fall fichier.idl` | `omniidl -bcxx fichier.idl` |
| Compiler | `javac -d bin src/*.java` | `g++ -Isrc -o bin/prog src/*.cc -lomniORB4 -lomnithread` (ou `make`) |
| Lancer | `java -cp bin Prog -ORBInitRef NameService=corbaname::localhost:2809` | `./bin/prog -ORBInitRef NameService=corbaname::localhost:2809` |
| Lister l'annuaire | `nameclt list -ORBInitRef NameService=corbaname::localhost:2809` | (même commande, outil séparé) |

[↑ Retour au sommaire](#sommaire)

---

## 7. Checklist : ajouter une nouvelle fonction partagée

1. **Modifie `app.idl`** (le seul fichier "contrat", commun aux deux langages) — ajoute ta méthode dans l'interface concernée.
2. **Régénère les DEUX côtés** :
   ```bash
   cd java/src && idlj -fall ../../app.idl && cd ../..
   cd cpp/src  && omniidl -bcxx ../../app.idl && cd ../..
   ```
3. **Implémente la vraie logique** dans le servant concerné (`FileServiceImpl.java` si c'est Java qui sert cette fonction, `DataServiceImpl.cc` si c'est C++) — c'est ici que va, par exemple, le vrai accès MySQL ou fichier.
4. **Recompile les deux côtés** (voir section 6).
5. **Si tu appelles cette nouvelle fonction depuis l'autre langage**, ajoute l'appel dans `JavaPeer.java` ou `cpp_peer.cc` (partie cliente).
6. **Teste en lançant les deux programmes en même temps**, dans 2 terminaux séparés.

C'est exactement le chemin suivi pour construire `01-hello-java` puis `02-bidirectionnel` — les étapes ne changent jamais, seule la logique métier (étape 3) change selon ce que tu veux faire (fichiers, MySQL, etc.).

[↑ Retour au sommaire](#sommaire)
