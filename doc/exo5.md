# Exercice 5 — Messagerie avec notifications (callback CORBA)

**Statut : construit et testé de bout en bout** (1 serveur C++ + 2 clients Java, messages stockés en MySQL, notifications reçues des deux côtés — voir la sortie réelle à l'étape 6).

Code : `corba-test/05-messagerie/`

---

## 1. L'idée en une phrase

Au lieu que le client Java demande sans arrêt au serveur « il y a du nouveau ? » (ce qu'on appelle le *polling*), **le client donne au serveur une référence vers lui-même**, et c'est **le serveur qui appelle le client** quand un message arrive. C'est ça, un **callback** (un « rappel »).

---

## 2. Ce qui change par rapport à `02-bidirectionnel`

Dans `02-bidirectionnel`, les deux programmes étaient déjà client ET serveur. Mais **chacun trouvait l'autre dans l'annuaire** (omniNames) :

```
02-bidirectionnel :

  Java  ── rebind("FileService") ──▶ ┌──────────┐ ◀── rebind("DataService") ── C++
                                     │ omniNames │
  Java  ── resolve("DataService") ─▶ │ (annuaire)│ ◀── resolve("FileService") ─ C++
                                     └──────────┘
  → chacun s'inscrit, chacun cherche l'autre par son NOM.
  → d'où les sleep(2) : il fallait attendre que l'autre soit inscrit.
```

Dans l'exercice 5, **seul le serveur C++ est dans l'annuaire**. Le client Java n'y est pas : il **donne sa référence en paramètre** d'un appel CORBA.

```
05-messagerie :

  C++   ── rebind("Messagerie") ───▶ ┌──────────┐
                                     │ omniNames │
  Java  ── resolve("Messagerie") ──▶ └──────────┘

  Java  ── sAbonner(monNotifiable) ─────────────────▶ C++   (Java s'abonne)
                     └─ un OBJET, pas une string       │
                                                        │ stocke la référence
  Java  ── envoyer("Alice", "bonjour") ─────────────▶ C++
                                                        │ 1) INSERT MySQL
  Java  ◀── nouveauMessage("Alice", "bonjour") ──────── C++ 2) CALLBACK
```

| | `02-bidirectionnel` | `05-messagerie` |
|---|---|---|
| Comment C++ trouve Java | `nc->resolve_str("FileService")` dans l'annuaire | Java lui **donne** sa référence via `sAbonner(...)` |
| Quand C++ appelle Java | Une fois au démarrage, après `sleep(2)` | **À chaque message**, au moment où il arrive |
| Java dans l'annuaire ? | Oui (`rebind("FileService")`) | **Non**, inutile |
| Combien de clients Java | 1 | Autant qu'on veut (chacun s'abonne) |

**À retenir pour l'examen** : une référence d'objet CORBA peut **voyager comme paramètre** d'une méthode, exactement comme un `string` ou un `long`. C'est le type `Notifiable` dans l'IDL. Celui qui la reçoit peut ensuite appeler des méthodes dessus.

---

## 3. Structure du dossier (même organisation que `02-bidirectionnel`)

```
05-messagerie/
├── app.idl                       → IDL partagé (écrit à la main)
├── setup-mysql.sql               → crée la table messages
├── java/
│   ├── src/
│   │   ├── chat/                  → généré par idlj, ne pas toucher
│   │   ├── NotifiableImpl.java    → écrit à la main (servant du CLIENT : reçoit les rappels)
│   │   └── ClientMessagerie.java  → écrit à la main (client + mini-serveur)
│   └── bin/                       → .class
└── cpp/
    ├── src/
    │   ├── app.hh, appSK.cc       → générés par omniidl, ne pas toucher
    │   ├── MessagerieImpl.hh/.cc  → écrit à la main (servant : MySQL + callback)
    │   └── cpp_peer.cc            → écrit à la main (serveur)
    ├── Makefile
    └── bin/cpp_peer
```

Correspondance avec le modèle 02 :

| 02-bidirectionnel | 05-messagerie | Rôle |
|---|---|---|
| `DataServiceImpl.cc` | `MessagerieImpl.cc` | servant C++ qui parle à MySQL |
| `FileServiceImpl.java` | `NotifiableImpl.java` | servant Java |
| `cpp_peer.cc` | `cpp_peer.cc` | `main` C++ |
| `JavaPeer.java` | `ClientMessagerie.java` | `main` Java |

---

## 4. L'IDL (`app.idl`)

```idl
module chat {
    // Servi par le CLIENT Java : c'est l'objet que le serveur C++ rappelle
    interface Notifiable {
        void nouveauMessage(in string auteur, in string contenu);
    };

    // Servi par le serveur C++ : stocke en MySQL et notifie les abonnes
    interface Messagerie {
        void envoyer(in string auteur, in string contenu);
        void sAbonner(in Notifiable abonne);
    };
};
```

- **Deux interfaces, une par rôle** : `Messagerie` est implémentée en C++, `Notifiable` est implémentée en Java.
- `sAbonner(in Notifiable abonne)` : le paramètre est de type **interface**. Côté C++, il arrive sous la forme `chat::Notifiable_ptr` ; côté Java, c'est un objet `Notifiable`. C'est une vraie référence réseau (un IOR : adresse IP + port + identifiant de l'objet Java), pas une copie des données.
- `Notifiable` est déclaré **avant** `Messagerie` car `Messagerie` l'utilise (l'IDL se lit de haut en bas, comme le C++).
- Module en **minuscules** (`chat`) : même règle que dans le 02, pour éviter le bug `idlj`/`javac -d`. J'ai choisi `chat` plutôt que `messagerie` pour ne pas avoir un package `messagerie` qui contient une classe `Messagerie` (source de confusion).

Génération (depuis `05-messagerie/`) :
```bash
cd java/src && idlj -fall ../../app.idl && cd ../..
cd cpp/src  && omniidl -bcxx ../../app.idl && cd ../..
```

`idlj -fall` génère pour **chaque** interface les fichiers client (`_XxxStub`, `XxxHelper`) ET serveur (`XxxPOA`). On a besoin des deux côtés Java : le côté client pour `Messagerie`, le côté serveur (`NotifiablePOA`) pour `Notifiable`.

---

## 5. Le code expliqué

### 5.1 Le servant C++ — `cpp/src/MessagerieImpl.hh`

```cpp
class MessagerieImpl : public POA_chat::Messagerie
{
    std::vector<chat::Notifiable_var> abonnes;   // les clients à rappeler
    omni_mutex verrou;                            // protège le vector
public:
    void envoyer(const char *auteur, const char *contenu);
    void sAbonner(chat::Notifiable_ptr abonne);
};
```

- `POA_chat::Messagerie` : la classe de base générée par omniidl (comme `POA_app::DataService` dans le 02).
- **`abonnes`** : c'est « la variable membre » de l'étape 4 de l'énoncé. J'ai mis un `vector` au lieu d'une seule variable pour pouvoir avoir plusieurs clients (Alice, Bob…) — c'est plus parlant pour une messagerie. Avec un seul abonné, ce serait simplement `chat::Notifiable_var abonne;`.
- **`Notifiable_var`** (et pas `Notifiable_ptr`) : le `_var` est un « pointeur intelligent » CORBA qui libère la référence tout seul. On l'utilise pour **stocker** ; le `_ptr` est ce qu'on reçoit en paramètre.
- **`omni_mutex`** : omniORB traite chaque appel entrant dans **son propre thread**. Si Alice et Bob appellent `envoyer()` au même moment, deux threads touchent le `vector` en même temps → il faut un verrou. (Dans le 02, il n'y avait pas d'état partagé en mémoire, donc pas besoin.)

### 5.2 `cpp/src/MessagerieImpl.cc` — l'abonnement

```cpp
void MessagerieImpl::sAbonner(chat::Notifiable_ptr abonne) {
    omni_mutex_lock lock(verrou);
    abonnes.push_back(chat::Notifiable::_duplicate(abonne));
    std::cout << "[C++] Nouvel abonne (" << abonnes.size() << " au total)" << std::endl;
}
```

**Piège C++ important : `_duplicate`.** Un paramètre `in` appartient à l'appelant : l'ORB le libère dès que `sAbonner` se termine. Si on le stockait tel quel, on garderait une référence « morte ». `_duplicate()` crée **notre propre copie** de la référence (en incrémentant un compteur interne), que le `_var` du vector libérera plus tard. Règle : **dès qu'on garde un paramètre objet au-delà de l'appel → `_duplicate`**.

`omni_mutex_lock lock(verrou);` verrouille jusqu'à la fin du bloc `{ }` (déverrouillage automatique).

### 5.3 `cpp/src/MessagerieImpl.cc` — l'envoi (le cœur de l'exercice)

```cpp
void MessagerieImpl::envoyer(const char* auteur, const char* contenu) {
    // 1) Enregistrer le message en MySQL  (même code que DataServiceImpl::saveData)
    ...
        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "INSERT INTO messages (auteur, contenu) VALUES (?, ?)"));
        pstmt->setString(1, auteur);
        pstmt->setString(2, contenu);
        pstmt->executeUpdate();
    ...

    // 2) CALLBACK : c'est maintenant le serveur qui appelle chaque client
    omni_mutex_lock lock(verrou);
    for (auto it = abonnes.begin(); it != abonnes.end(); ) {
        try {
            (*it)->nouveauMessage(auteur, contenu);   // ← appel C++ → Java
            ++it;
        } catch (const CORBA::Exception& e) {
            it = abonnes.erase(it);                   // client fermé → on l'oublie
        }
    }
}
```

- La partie MySQL est **copiée du modèle 02** (`DataServiceImpl::saveData`) : même connexion, même `PreparedStatement`, seule la requête change (`INSERT` au lieu de `UPDATE`).
- **`(*it)->nouveauMessage(auteur, contenu)`** : c'est LA ligne du callback. Syntaxiquement, c'est exactement comme `file->readMessage()` dans le 02 (un appel sur une référence distante). La seule différence : la référence ne vient pas de l'annuaire, elle vient de `sAbonner`.
- **`try/catch` autour du rappel** : si un client Java a été fermé, sa référence ne répond plus → `CORBA::TRANSIENT` ou `COMM_FAILURE`. Sans le `catch`, le serveur planterait au prochain message. Avec, il retire simplement l'abonné (testé, voir étape 6).

### 5.4 Le `main` C++ — `cpp/src/cpp_peer.cc`

C'est **exactement** le `cpp_peer.cc` du 02, avec deux différences :

1. On active `MessagerieImpl` au lieu de `DataServiceImpl`, et on l'inscrit sous le nom `"Messagerie"`.
2. **Toute la « PARTIE CLIENTE » a disparu** (le `sleep(20)` + `resolve_str("FileService")`). Le C++ ne cherche plus Java : c'est Java qui vient se présenter via `sAbonner`. Le rôle « client » du C++ est désormais caché **dans** `envoyer()`.

```cpp
    // --- PARTIE SERVEUR : j'enregistre ma Messagerie ---
    MessagerieImpl *messImpl = new MessagerieImpl();
    PortableServer::ObjectId_var id = poa->activate_object(messImpl);
    chat::Messagerie_var messRef = messImpl->_this();
    ...
    nc->rebind(messName, messRef);

    orb->run();   // attend les appels : sAbonner, envoyer
```

### 5.5 Le servant Java — `java/src/NotifiableImpl.java`

```java
public class NotifiableImpl extends NotifiablePOA {
    private final String moi;
    public NotifiableImpl(String moi) { this.moi = moi; }

    public void nouveauMessage(String auteur, String contenu) {
        if (auteur.equals(moi)) {
            System.out.println("[Java] (accuse de reception) mon message a ete diffuse");
        } else {
            System.out.println("[Java] >>> Notification : " + auteur + " dit : " + contenu);
        }
    }
}
```

Même principe que `FileServiceImpl extends FileServicePOA` dans le 02. **Personne côté Java n'appelle jamais `nouveauMessage`** : c'est le serveur C++ qui l'appelle à distance. Le serveur rappelle aussi l'auteur lui-même ; ici je l'affiche comme un accusé de réception (preuve que le message a bien fait l'aller-retour).

### 5.6 Le client Java — `java/src/ClientMessagerie.java`

Voici le déroulé dans l'ordre, avec ce que ça correspond dans l'énoncé :

```java
// (a) Démarrer l'ORB et activer le POA          → comme JavaPeer du 02
ORB orb = ORB.init(args, null);
POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
rootpoa.the_POAManager().activate();

// (b) Créer MON objet Notifiable                → étape 2 de l'énoncé
NotifiableImpl notifImpl = new NotifiableImpl(pseudo);
org.omg.CORBA.Object ref = rootpoa.servant_to_reference(notifImpl);
Notifiable monNotifiable = NotifiableHelper.narrow(ref);

// (c) Lancer orb.run() dans un THREAD À PART    → le "piège classique"
Thread threadOrb = new Thread(orb::run);
threadOrb.setDaemon(true);
threadOrb.start();

// (d) Trouver le serveur dans l'annuaire         → comme resolve_str du 02
Messagerie messagerie = MessagerieHelper.narrow(ncRef.resolve_str("Messagerie"));

// (e) M'abonner en donnant MA référence          → étape 3 de l'énoncé
messagerie.sAbonner(monNotifiable);

// (f) Lire le clavier et envoyer                 → étape 5
while ((ligne = clavier.readLine()) != null) {
    messagerie.envoyer(pseudo, ligne);
}
```

Explications :

- **(b)** — `servant_to_reference` transforme l'objet Java ordinaire en **référence CORBA** joignable depuis le réseau. C'est ce qui fait du client un « mini-serveur ». Remarque : **pas de `rebind` dans l'annuaire** (contrairement au 02 et à ce que suggère l'énoncé). Ce n'est pas nécessaire puisque la référence est transmise directement au serveur. Tu *pourrais* l'y inscrire, ça ne casserait rien, mais ça ne servirait à rien.

- **(c)** — **Pourquoi un thread ?** Dans le 02, `orb.run()` était à la fin du `main` : il bloque pour toujours, ce qui était OK car Java n'avait plus rien d'autre à faire. Ici, Java doit faire **deux choses en même temps** :
  - écouter les rappels du serveur (`orb.run()`, bloquant) ;
  - lire le clavier et appeler `envoyer()` (boucle bloquante sur `readLine()`).

  Donc `orb.run()` part dans un thread séparé, et le thread principal garde le clavier. `setDaemon(true)` : ce thread ne doit pas empêcher le programme de se terminer quand on fait Ctrl+D.

  Le démarrer **avant** `sAbonner` garantit que dès que le serveur connaît notre référence, on est prêt à recevoir.

- **(e)** — On passe `monNotifiable`, **un objet**, pas son nom ni une string. L'ORB Java le transforme automatiquement en IOR pour le transport, et l'ORB C++ le reconstruit en `Notifiable_ptr` de l'autre côté.

- **Le pseudo** est pris en premier argument (`ClientMessagerie Alice -ORBInitRef ...`). L'ORB ignore les arguments qu'il ne reconnaît pas, donc `ORB.init(args, null)` n'est pas gêné par `Alice`.

---

## 6. Ce qui se passe pendant UN envoi (chronologie)

Alice tape « bonjour tout le monde ». Bob est aussi abonné.

```
 Alice (Java)                  cpp_peer (C++)                   Bob (Java)
 thread clavier   thread ORB                                     thread ORB
     │                              │                                 │
     │── envoyer("Alice","bonjour") ─▶│                                 │
     │   (bloqué, attend la réponse)  │ INSERT INTO messages ...        │
     │                                │                                 │
     │              ◀── nouveauMessage("Alice","bonjour") ──│           │
     │              │ affiche "accusé de réception"          │           │
     │              │── réponse ───────────────────────────▶│           │
     │                                │── nouveauMessage("Alice","bonjour") ──▶│
     │                                │                                 │ affiche ">>> Notification"
     │                                │◀──────────────────── réponse ───│
     │◀──────────── réponse de envoyer() ─│                             │
     │ (reprend la lecture du clavier)
```

Point subtil : pendant que le thread clavier d'Alice est **bloqué** dans `envoyer()`, le serveur **rappelle Alice**. Ça marche uniquement parce que le rappel est traité par **un autre thread** (le thread ORB). C'est exactement pour ça que le thread de l'étape (c) est indispensable — sans lui on risque un **interblocage** (*deadlock*) : Alice attend C++, qui attend Alice.

---

## 7. Compiler et lancer

### 7.1 Une seule fois : la table MySQL
```bash
cd corba-test/05-messagerie
mysql -u corba_user -pcorba < setup-mysql.sql
```
(La base `corba_demo` et l'utilisateur `corba_user` existent déjà depuis le 02 ; on ajoute juste la table `messages`.)

### 7.2 Compiler
```bash
cd corba-test/05-messagerie

# Java
cd java/src
javac -d ../bin chat/*.java NotifiableImpl.java ClientMessagerie.java
cd ../..

# C++
cd cpp && make && cd ..
```
(Les 2 warnings `IORCheckImpl is internal proprietary API` viennent du code généré par idlj — normaux, déjà vus dans le 02.)

### 7.3 Lancer (3 terminaux, omniNames tourne déjà en service système)
```bash
cd corba-test/05-messagerie

# Terminal A — le serveur C++ (EN PREMIER cette fois)
./cpp/bin/cpp_peer -ORBInitRef NameService=corbaname::localhost:2809

# Terminal B — Alice
java -cp java/bin ClientMessagerie Alice -ORBInitRef NameService=corbaname::localhost:2809

# Terminal C — Bob
java -cp java/bin ClientMessagerie Bob -ORBInitRef NameService=corbaname::localhost:2809
```

Ici, **l'ordre compte** : le serveur C++ doit être lancé avant les clients (sinon `resolve_str("Messagerie")` échoue avec `NotFound`). Plus besoin des `sleep` du 02 : le serveur n'a jamais besoin d'attendre les clients.

Tape des messages dans B et C, puis Ctrl+D pour quitter un client.

### 7.4 Sortie réelle (testée)

```
=== Terminal A (C++)
[C++] Messagerie enregistree, en attente...
[C++] Nouvel abonne (1 au total)
[C++] Nouvel abonne (2 au total)
[C++] Message recu de Alice : bonjour tout le monde
[C++] Message recu de Bob : salut Alice

=== Terminal B (Alice)
[Java] Alice abonne. Tape un message puis Entree (Ctrl+D pour quitter).
bonjour tout le monde
[Java] (accuse de reception) mon message a ete diffuse
[Java] >>> Notification : Bob dit : salut Alice

=== Terminal C (Bob)
[Java] Bob abonne. Tape un message puis Entree (Ctrl+D pour quitter).
[Java] >>> Notification : Alice dit : bonjour tout le monde
salut Alice
[Java] (accuse de reception) mon message a ete diffuse
```

Bob a reçu le message d'Alice **sans rien avoir demandé** : c'est le serveur qui l'a rappelé.

En base :
```bash
mysql -u corba_user -pcorba corba_demo -e "SELECT * FROM messages"
```
```
id  auteur  contenu                date_envoi
1   Alice   bonjour tout le monde  2026-09-22 22:04:43
2   Bob     salut Alice            2026-09-22 22:04:45
```

Test du client fermé (Bob quitte, puis Alice envoie) :
```
[C++] Message recu de Alice : Bob est parti ?
[C++] Abonne injoignable, retire de la liste
```
→ le serveur ne plante pas, il oublie Bob.

---

## 8. Résumé — les 5 choses à savoir expliquer à l'examen

1. **Un callback = le serveur appelle le client.** Pour ça, le client doit **aussi être un serveur** : il a un servant (`NotifiableImpl`), un POA activé, et fait tourner `orb.run()`.
2. **La référence du client voyage en paramètre** (`sAbonner(in Notifiable abonne)`), pas via l'annuaire. Un type `interface` dans l'IDL = une référence d'objet distant.
3. **Côté C++, pour stocker une référence reçue en paramètre → `_duplicate()`** dans un `_var`.
4. **Côté Java, `orb.run()` dans un thread séparé**, démarré avant `sAbonner`, car le thread principal est occupé (clavier) et parce qu'un rappel peut arriver pendant que Java est bloqué dans `envoyer()`.
5. **Un client peut disparaître** : toujours entourer le rappel d'un `try/catch (CORBA::Exception&)` côté serveur.

---

## 9. Pour aller plus loin (variantes possibles à l'examen)

- **`oneway`** : dans l'IDL, `oneway void nouveauMessage(...)` rend l'appel « envoyer et oublier » — le serveur n'attend pas la réponse de chaque client. Plus rapide, mais on ne sait plus si le client a bien reçu (et on ne détecte plus un client mort). Une méthode `oneway` doit renvoyer `void` et n'avoir que des paramètres `in`.
- **Se désabonner** : ajouter `void seDesabonner(in Notifiable abonne);` et, côté C++, retirer la référence dont `abonne->_is_equivalent(*it)` est vrai.
- **Historique** : ajouter `sequence<string> historique();` qui fait un `SELECT` sur la table `messages` (combine l'exercice 2 et celui-ci).
- **Rappeler sans bloquer le verrou** : ici le verrou est tenu pendant les rappels. Si un client, dans `nouveauMessage`, rappelait lui-même `envoyer()`, on aurait un interblocage. La version robuste copie le `vector` sous verrou, puis fait les rappels hors du verrou.
