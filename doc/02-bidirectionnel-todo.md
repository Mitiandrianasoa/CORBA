# 02-bidirectionnel — app Java ↔ C++ (base pour MySQL + Fichiers)

**Statut : construit et testé de bout en bout.** Les deux sens de communication fonctionnent (voir sortie attendue à l'étape 5).

Objectif : une seule petite app qui couvre en même temps les points 2, 3 et 4 de `contexte.md` :
- communication Java ↔ C++ dans les deux sens
- une brique côté C++ qui deviendra l'accès MySQL
- une brique côté Java qui deviendra l'accès fichiers

## Idée de conception (pourquoi c'est facile à adapter plus tard)

On définit **2 interfaces dans le même fichier IDL**, une par langage. Chaque langage est responsable de SA brique :

```
┌─────────────────────┐              ┌─────────────────────┐
│   Programme JAVA     │              │   Programme C++      │
│                      │              │                      │
│  sert : FileService  │◀────────────▶│  sert : DataService  │
│  (lit/écrit un       │   appels     │  (lit/écrit une      │
│   "fichier")          │   dans les   │   "base de données") │
│                      │   2 sens     │                      │
│  appelle : DataService│              │  appelle : FileService│
└─────────────────────┘              └─────────────────────┘
```

- `FileService` (implémenté en Java) → plus tard, ses méthodes liront/écriront de vrais fichiers avec `java.io`/`java.nio`.
- `DataService` (implémenté en C++) → plus tard, ses méthodes feront de vraies requêtes MySQL avec `libmysqlcppconn`.
- Pour l'instant (étape 1), les deux ne font que manipuler des chaînes de caractères en mémoire (comme `HelloImpl` avant) — on valide d'abord que le tuyau CORBA fonctionne, AVANT de brancher MySQL/fichiers.

C'est le principe "option B" vu dans `question-bidirectionnel.md` : chaque programme est à la fois serveur ET client, mais avec des méthodes ultra simples.

## Structure du dossier (Java et C++ bien séparés)

```
02-bidirectionnel/
├── app.idl                    → IDL partagé, SOURCE UNIQUE (écrit à la main)
│
├── java/
│   ├── src/
│   │   ├── app/                → généré par idlj, ne pas toucher
│   │   ├── FileServiceImpl.java → écrit à la main (le servant)
│   │   └── JavaPeer.java        → écrit à la main (serveur + client)
│   └── bin/                    → généré par javac -d (tous les .class)
│
└── cpp/
    ├── src/
    │   ├── app.hh, appSK.cc     → générés par omniidl, ne pas toucher
    │   ├── DataServiceImpl.hh   → écrit à la main (le servant)
    │   ├── DataServiceImpl.cc   → écrit à la main
    │   └── cpp_peer.cc          → écrit à la main (serveur + client)
    ├── Makefile
    └── bin/
        └── cpp_peer             → exécutable compilé
```

## Étape 0 — Service de nommage commun

Java et C++ doivent utiliser le **même** annuaire pour se trouver. On utilise `omniNames`.

**Rien à démarrer toi-même** : le paquet `omniorb-nameserver` installe `omniNames` comme **service système**, déjà actif au boot sur le port 2809 (vérifié : `systemctl status omniorb-nameserver` → `active (running)`). Si tu essaies de le lancer manuellement (`omniNames -start 2809`), tu auras une erreur `Address in use` — normal, il tourne déjà.

## Étape 1 — Le fichier IDL partagé (à la racine de `02-bidirectionnel/`)

`app.idl` :
```idl
module app {
    interface FileService {
        string readMessage();
        void writeMessage(in string msg);
    };

    interface DataService {
        string getData();
        void saveData(in string value);
    };
};
```

Génération (chacun dans son propre dossier `src/`) :
```bash
cd corba-test/02-bidirectionnel

cd java/src && idlj -fall ../../app.idl && cd ../..
cd cpp/src  && omniidl -bcxx ../../app.idl && cd ../..
```

## Étape 2 — Côté Java (`java/src/`)

`FileServiceImpl.java` :
```java
import app.FileServicePOA;

public class FileServiceImpl extends FileServicePOA {
    private String stockage = "(rien ecrit pour l'instant)";

    public String readMessage() {
        return stockage; // TODO plus tard : lire un vrai fichier ici
    }

    public void writeMessage(String msg) {
        stockage = msg; // TODO plus tard : ecrire dans un vrai fichier ici
    }
}
```

`JavaPeer.java` :
```java
import app.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

public class JavaPeer {
    public static void main(String[] args) throws Exception {
        ORB orb = ORB.init(args, null);

        POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
        rootpoa.the_POAManager().activate();

        // --- PARTIE SERVEUR : j'enregistre mon FileService ---
        FileServiceImpl fileImpl = new FileServiceImpl();
        org.omg.CORBA.Object ref = rootpoa.servant_to_reference(fileImpl);
        FileService fileHref = FileServiceHelper.narrow(ref);

        NamingContextExt ncRef = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));
        ncRef.rebind(ncRef.to_name("FileService"), fileHref);
        System.out.println("[Java] FileService enregistre, en attente...");

        // --- PARTIE CLIENTE : j'appelle DataService (cote C++) ---
        Thread.sleep(2000);
        try {
            DataService data = DataServiceHelper.narrow(ncRef.resolve_str("DataService"));
            System.out.println("[Java] Reponse de DataService (C++) : " + data.getData());
            data.saveData("bonjour depuis Java");
        } catch (Exception e) {
            System.out.println("[Java] DataService pas encore disponible : " + e);
        }

        orb.run();
    }
}
```

## Étape 3 — Côté C++ (`cpp/src/`)

`DataServiceImpl.hh` :
```cpp
#ifndef DATASERVICEIMPL_HH
#define DATASERVICEIMPL_HH

#include "app.hh"
#include <string>

class DataServiceImpl : public POA_app::DataService {
    std::string stockage = "(rien enregistre pour l'instant)";
public:
    char* getData();
    void saveData(const char* value);
};

#endif
```

`DataServiceImpl.cc` :
```cpp
#include "DataServiceImpl.hh"

char* DataServiceImpl::getData() {
    // TODO plus tard : faire un SELECT MySQL ici (libmysqlcppconn)
    return CORBA::string_dup(stockage.c_str());
}

void DataServiceImpl::saveData(const char* value) {
    // TODO plus tard : faire un INSERT/UPDATE MySQL ici
    stockage = value;
}
```

`cpp_peer.cc` :
```cpp
#include "app.hh"
#include "DataServiceImpl.hh"
#include <omniORB4/CORBA.h>
#include <iostream>
#include <unistd.h>

int main(int argc, char** argv) {
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);

    CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");
    PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
    PortableServer::POAManager_var pman = poa->the_POAManager();
    pman->activate();

    // --- PARTIE SERVEUR : j'enregistre mon DataService ---
    DataServiceImpl* dataImpl = new DataServiceImpl();
    PortableServer::ObjectId_var id = poa->activate_object(dataImpl);
    app::DataService_var dataRef = dataImpl->_this();

    CORBA::Object_var nsObj = orb->resolve_initial_references("NameService");
    CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);
    CosNaming::Name_var dataName = nc->to_name("DataService");
    nc->rebind(dataName, dataRef);
    std::cout << "[C++] DataService enregistre, en attente..." << std::endl;

    // --- PARTIE CLIENTE : j'appelle FileService (cote Java) ---
    sleep(2);
    try {
        CORBA::Object_var obj = nc->resolve_str("FileService");
        app::FileService_var file = app::FileService::_narrow(obj);
        CORBA::String_var msg = file->readMessage();
        std::cout << "[C++] Reponse de FileService (Java) : " << msg << std::endl;
        file->writeMessage("bonjour depuis C++");
    } catch (const CORBA::Exception& e) {
        std::cout << "[C++] FileService pas encore disponible" << std::endl;
    }

    orb->run();
    return 0;
}
```

> Note technique : `nc->to_name("DataService")` renvoie un pointeur (`CosNaming::Name*`), pas une référence — il faut le stocker dans un `CosNaming::Name_var` avant de le passer à `rebind()`, sinon erreur de compilation `invalid conversion`.

`cpp/Makefile` :
```makefile
CXX = g++
CXXFLAGS = -Isrc -Wall
LIBS = -lomniORB4 -lomnithread
BIN = bin/cpp_peer

SRCS = src/appSK.cc src/DataServiceImpl.cc src/cpp_peer.cc

all: $(BIN)

$(BIN): $(SRCS)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -o $(BIN) $(SRCS) $(LIBS)

clean:
	rm -f $(BIN)

.PHONY: all clean
```

> Note technique : sur cette installation, seules `libomniORB4` et `libomnithread` existent (`ldconfig -p | grep omni`). Les libs `-lCOS4`/`-lomniDynamic4` citées dans certains tutoriels n'existent pas ici et ne sont pas nécessaires pour ce code — ne les mets pas dans `LIBS`, sinon erreur `ld: cannot find -lCOS4`.

## Étape 4 — Compiler

```bash
cd corba-test/02-bidirectionnel

# Java (classes dans java/bin/)
cd java/src
javac -d ../bin app/*.java FileServiceImpl.java JavaPeer.java
cd ../..

# C++ (executable dans cpp/bin/cpp_peer)
cd cpp
make
cd ..
```

## Étape 5 — Lancer (2 terminaux — `omniNames` tourne déjà en service système)

```bash
cd corba-test/02-bidirectionnel

# Terminal A — Java
java -cp java/bin JavaPeer -ORBInitRef NameService=corbaname::localhost:2809

# Terminal B — C++
./cpp/bin/cpp_peer -ORBInitRef NameService=corbaname::localhost:2809
```

> Note technique : `-ORBInitialHost`/`-ORBInitialPort` (utilisés dans `01-hello-java` avec `tnameserv`) ne sont PAS reconnus par omniORB côté C++ (`unknown option`). La syntaxe qui marche pour **les deux langages** avec `omniNames` est `-ORBInitRef NameService=corbaname::localhost:2809`.

Lance A et B à peu près en même temps (les `sleep`/`Thread.sleep` de 2s dans le code servent à ça). Sortie observée (testée) :
```
[Java] FileService enregistre, en attente...
[Java] Reponse de DataService (C++) : (rien enregistre pour l'instant)

[C++] DataService enregistre, en attente...
[C++] Reponse de FileService (Java) : (rien ecrit pour l'instant)
```
→ les deux sens fonctionnent : Java a appelé C++, ET C++ a appelé Java, dans la même exécution.

## Étape 6 (à faire) — Brancher MySQL et les fichiers

Une fois que ça marche avec les chaînes factices (fait), il ne reste QUE 2 fichiers à modifier, tout le reste (IDL, ORB, naming service, structure) ne change pas :

- **`cpp/src/DataServiceImpl.cc`** : remplacer le `stockage` par de vraies requêtes avec `libmysqlcppconn` (`#include <mysql_connection.h>`, `#include <mysql_driver.h>`) dans `getData()`/`saveData()`.
- **`java/src/FileServiceImpl.java`** : remplacer `stockage` par de vraies lectures/écritures avec `java.nio.file.Files.readString(...)` / `Files.writeString(...)` dans `readMessage()`/`writeMessage()`.

C'est tout l'intérêt de cette conception : **la partie CORBA (tuyau de communication) et la partie métier (MySQL / fichiers) sont complètement séparées** — tu ne retouches jamais l'IDL ni le code réseau pour brancher la base de données ou les fichiers.

## Points de vigilance (déjà rencontrés)

- Nomme toujours le module IDL en **minuscules** (`module app`, pas `module App`) pour éviter le bug `idlj`/`javac -d` vu sur `01-hello-java`.
- Garde les `.class`/l'exécutable dans `bin/`, jamais mélangés aux sources.
- `omniNames` doit tourner AVANT de lancer Java et C++ (déjà le cas, service système).
- Utilise `-ORBInitRef NameService=corbaname::localhost:2809` pour les deux langages, pas `-ORBInitialHost`/`-ORBInitialPort` (spécifique Java/tnameserv, non reconnu par omniORB).
- Côté C++, `to_name(...)` doit être stocké dans un `_var` avant d'être passé à `rebind()`.
- Côté C++, ne linke que `-lomniORB4 -lomnithread` (pas de `-lCOS4`/`-lomniDynamic4` sur cette installation).
