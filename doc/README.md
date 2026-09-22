# CORBA — parcours d'apprentissage

Basé sur `../contexte.md` :
1. exemple générique (ce dossier) pour comprendre l'architecture
2. C++ ↔ Java, communication dans les deux sens
3. C++ → MySQL
4. Java → Fichiers

## 01-hello-java — exemple de référence (testé, fonctionnel)

Cycle CORBA complet en Java seul : `Hello.idl` → génération des stubs → serveur → client.
Fonctionne — testé de bout en bout (`Reponse : Hello World from CORBA!`).

### Architecture — les 4 briques CORBA

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────┐
│ HelloClient │────────▶│  Naming Service   │◀────────│ HelloServer │
│  (ORB)      │  1. "où │   (tnameserv)     │ 2. je    │  (ORB+POA)  │
│             │  est    │  annuaire nom→réf │ m'enre-  │             │
│             │  Hello?"│                   │ gistre   │             │
└──────┬──────┘         └───────────────────┘ ici      └──────┬──────┘
       │                                                       │
       │              3. appel direct via IIOP                 │
       └──────────────────────────────────────────────────────▶│
                    hello.sayHello() → "Hello World..."         │
```

| Brique | Rôle | Fichier / commande |
|---|---|---|
| **IDL** (Interface Definition Language) | Contrat de l'interface, indépendant du langage. C'est le point commun entre C++ et Java. | `Hello.idl` |
| **idlj / omniidl** | Compile l'IDL en code natif (stubs client + squelettes serveur) | `idlj -fall Hello.idl` → génère `helloapp/` |
| **ORB** (Object Request Broker) | Middleware qui sérialise les appels, gère le réseau (protocole **IIOP**), côté client ET serveur | `ORB.init(args, null)` |
| **POA** (Portable Object Adapter) | Côté serveur : gère le cycle de vie des objets CORBA (activation, association servant ↔ référence) | `POAHelper.narrow(...)`, `rootpoa.the_POAManager().activate()` |
| **Servant** | La classe qui contient la vraie logique métier, hérite du squelette généré (`HelloPOA`) | `HelloImpl.java` |
| **Naming Service** | Annuaire qui permet au client de trouver un objet par un **nom** plutôt qu'une adresse réseau en dur | `tnameserv`, `ncRef.rebind(...)` / `ncRef.resolve_str(...)` |

### Ce qu'il faut retenir pour la suite (C++)

- Le fichier `.idl` est **strictement identique** en C++ et Java — c'est le contrat partagé.
- Côté C++, `omniidl -bcxx Hello.idl` génère l'équivalent des stubs (`.hh`/`.cc`) au lieu de `idlj`.
- Le **Naming Service** est le point de rendez-vous : un serveur C++ peut s'enregistrer dans le même annuaire qu'un client Java, et inversement → c'est ce qui permet la communication bidirectionnelle sans coder d'adresses IP/ports en dur.
- omniORB utilise `omniNames` comme service de nommage (au lieu de `tnameserv`) — les deux parlent le même protocole standard **CosNaming**, donc un client Java peut interroger `omniNames` et vice versa.

### Structure du dossier

```
01-hello-java/
├── Hello.idl              → le contrat, écrit à la main (module en minuscules : "helloapp")
├── helloapp/               → généré par idlj, NE PAS modifier à la main
│   ├── Hello.java
│   ├── HelloOperations.java
│   ├── HelloPOA.java
│   ├── HelloHelper.java
│   ├── HelloHolder.java
│   └── _HelloStub.java
├── HelloImpl.java          → écrit à la main : la vraie logique (le servant)
├── HelloServer.java        → écrit à la main : démarre l'ORB, enregistre le servant
├── HelloClient.java        → écrit à la main : cherche le servant, l'appelle
└── bin/                    → généré par javac -d, tous les .class rangés ici
    ├── helloapp/*.class
    ├── HelloImpl.class
    ├── HelloServer.class
    └── HelloClient.class
```

> Note : le module IDL est nommé en minuscules (`helloapp`) exprès. `idlj` transforme un nom de module en package Java **en minuscules**, mais certains fichiers générés référencent ce package avec la casse d'origine — si le module IDL a des majuscules (`HelloApp`), ça crée une incohérence interne qui fait planter `javac -d`. Solution : toujours nommer les modules IDL en minuscules.

### Reproduire / relancer ce test

```bash
cd 01-hello-java

# Terminal 1 — service de nommage (laisser ouvert)
tnameserv -ORBInitialPort 1050

# Terminal 2 — serveur (laisser ouvert)
java -cp bin HelloServer -ORBInitialPort 1050

# Terminal 3 — client
java -cp bin HelloClient -ORBInitialPort 1050
# → Reponse : Salama!
```

Pour recompiler après modification (classes toujours rangées dans `bin/`, jamais mélangées aux `.java`) :
```bash
idlj -fall Hello.idl              # seulement si Hello.idl change → régénère helloapp/
rm -rf bin && mkdir bin
javac -d bin helloapp/*.java *.java
```

## Étapes suivantes (à faire seul, en réutilisant ce pattern)

- [ ] **02-hello-cpp** : même `Hello.idl`, mais compilé avec `omniidl -bcxx`, serveur/client en C++ avec omniORB, enregistré dans `omniNames`. Vérifie d'abord que `java HelloClient` (Java) peut trouver un objet enregistré par un serveur C++ dans `omniNames` — c'est la preuve de l'interopérabilité.
- [ ] **03-bidirectionnel** : un IDL avec deux interfaces (ex: `Client` et `Server` qui s'appellent mutuellement), OU un serveur C++ + client Java ET un serveur Java + client C++ dans le même dossier.
- [ ] **04-cpp-mysql** : un servant C++ CORBA dont l'implémentation utilise `libmysqlcppconn` (`#include <mysql_connection.h>`) pour lire/écrire en base au lieu de juste retourner une string.
- [ ] **05-java-files** : un servant Java CORBA dont l'implémentation utilise `java.nio.file`/`java.io` pour lire/écrire des fichiers.

Le `README.md` de `installation-prerequis.md` et `verification-prerequis.md` (racine `CORBA/`) confirment que tous les outils nécessaires (omniidl, idlj, g++, libmysqlcppconn-dev, mysql-server) sont déjà installés et opérationnels.
