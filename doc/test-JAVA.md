Voici un ToDo simple pour tester CORBA avec **Java seul** (le plus rapide à mettre en œuvre avec votre environnement déjà installé).

## ToDo : Test CORBA « Hello World » en Java

### 1. Créer le fichier IDL
Créez un fichier `Hello.idl` :

```idl
module HelloApp {
    interface Hello {
        string sayHello();
    };
};
```

### 2. Compiler l'IDL en Java
Utilisez `idlj` pour générer les stubs et squelettes :

```bash
idlj -fall Hello.idl
```

Cela génère un dossier `HelloApp/` avec les fichiers Java nécessaires .

### 3. Créer l'implémentation du serveur
Créez `HelloImpl.java` :

```java
package HelloApp;

public class HelloImpl extends HelloPOA {
    public String sayHello() {
        return "Hello World from CORBA!";
    }
}
```

L'implémentation hérite de `HelloPOA` et contient la logique métier .

### 4. Créer le serveur
Créez `HelloServer.java` :

```java
import HelloApp.*;
import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import org.omg.CosNaming.*;

public class HelloServer {
    public static void main(String[] args) {
        try {
            // 1. Initialiser l'ORB
            ORB orb = ORB.init(args, null);

            // 2. Récupérer et activer le POA racine
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            // 3. Créer l'objet servant
            HelloImpl helloImpl = new HelloImpl();

            // 4. Obtenir la référence CORBA
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(helloImpl);
            Hello href = HelloHelper.narrow(ref);

            // 5. Enregistrer dans le service de nommage
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            
            NameComponent path[] = ncRef.to_name("Hello");
            ncRef.rebind(path, href);

            System.out.println("Serveur prêt...");
            orb.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

Le serveur initialise l'ORB, active le POA, crée l'objet et l'enregistre dans le service de nommage .

### 5. Créer le client
Créez `HelloClient.java` :

```java
import HelloApp.*;
import org.omg.CORBA.*;
import org.omg.CosNaming.*;

public class HelloClient {
    public static void main(String[] args) {
        try {
            ORB orb = ORB.init(args, null);

            // Rechercher le service de nommage
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            // Trouver l'objet "Hello"
            Hello hello = HelloHelper.narrow(ncRef.resolve_str("Hello"));

            // Appeler la méthode distante
            System.out.println("Réponse : " + hello.sayHello());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

Le client localise le serveur via le service de nommage puis appelle la méthode .

### 6. Compiler les fichiers Java

```bash
javac HelloApp/*.java *.java
```

### 7. Lancer le test

Ouvrez **3 terminaux** :

**Terminal 1 — Service de nommage :**
```bash
tnameserv -ORBInitialPort 1050
```
Laissez ce terminal ouvert .

**Terminal 2 — Serveur :**
```bash
java HelloServer -ORBInitialPort 1050
```

**Terminal 3 — Client :**
```bash
java HelloClient -ORBInitialPort 1050
```

Vous devriez voir : `Réponse : Hello World from CORBA!`

### Points clés à retenir

- **ORB** : Le middleware qui gère la communication 
- **POA** : Gère le cycle de vie des objets distants 
- **Service de nommage** : Permet au client de trouver l'objet par un nom ("Hello") plutôt qu'une adresse 
- **IIOP** : Protocole réseau sous-jacent (basé sur TCP/IP) 

Ce test minimal vous familiarise avec le cycle complet : IDL → génération → implémentation → enregistrement → invocation. Une fois maîtrisé, vous pourrez étendre vers C++ avec omniORB.