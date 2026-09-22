Voici la liste complète de tout ce qu'il faut télécharger et installer, avec les commandes de vérification pour chaque composant.

### 1. Java 8 (JDK) — Obligatoire pour CORBA

CORBA a été retiré de Java à partir de la version 11 . Java 8 est la dernière version qui inclut `idlj` (compilateur IDL) et `tnameserv` (service de nommage).

**Commande d'installation :**
```bash
sudo apt update
sudo apt install openjdk-8-jdk -y
```

**Commande de vérification :**
```bash
java -version
# Attendu : openjdk version "1.8.0_xxx"

which idlj
# Attendu : /usr/bin/idlj

idlj -version
# Doit afficher la version
```

Si le paquet n'est pas trouvé, ajoutez le PPA :
```bash
sudo add-apt-repository ppa:openjdk-r/ppa -y
sudo apt update
sudo apt install openjdk-8-jdk -y
```

### 2. omniORB (Implémentation CORBA pour C++)

omniORB fournit la bibliothèque CORBA C++ et `omniidl` (compilateur IDL pour C++) .

**Commande d'installation :**
```bash
sudo apt install omniorb omniidl -y
```

**Commande de vérification :**
```bash
which omniNames
# Attendu : /usr/bin/omniNames

which omniidl
# Attendu : /usr/bin/omniidl

omniidl -bcxx -v 2>&1 | head -5
# Doit afficher des informations de version
```

### 3. MySQL Server (Le serveur de base de données)

Le serveur MySQL lui-même doit être installé séparément du connecteur C++.

**Commande d'installation :**
```bash
sudo apt install mysql-server -y
```

**Commande de vérification :**
```bash
mysql --version
# Attendu : mysql  Ver 8.0.xx

sudo systemctl status mysql
# Attendu : Active: active (running)
```

### 4. MySQL Connector/C++ (Bibliothèque de connexion C++)

Permet à votre code C++ de se connecter à MySQL .

**Commande d'installation :**
```bash
sudo apt install libmysqlcppconn-dev -y
```

**Commande de vérification :**
```bash
ls /usr/include/mysqlcppconn/
# Doit lister des fichiers .h (mysql_connection.h, etc.)

ls /usr/lib/x86_64-linux-gnu/ | grep mysqlcppconn
# Doit lister libmysqlcppconn.so
```

### 5. Configuration de l'environnement

**Créer le fichier de configuration omniORB :**
```bash
cat > ~/omniORB.cfg << 'EOF'
InitRef = NameService=corbaname::localhost:2809
supportBootstrapAgent = 1
EOF
```

**Ajouter les variables d'environnement (persistant dans ~/.bashrc) :**
```bash
cat >> ~/.bashrc << 'EOF'

# --- Environnement CORBA ---
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export OMNIORB_CONFIG=$HOME/omniORB.cfg
export LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu:$LD_LIBRARY_PATH
EOF

source ~/.bashrc
```

**Vérification des variables :**
```bash
echo "JAVA_HOME=$JAVA_HOME"
echo "OMNIORB_CONFIG=$OMNIORB_CONFIG"
echo "PATH=$PATH" | grep -o "java-8-openjdk-amd64"
```

### 6. Vérification finale complète

Exécutez cette séquence pour valider toute l'installation :

```bash
echo "=== 1. JAVA ==="
java -version 2>&1
javac -version 2>&1
which idlj
which tnameserv

echo ""
echo "=== 2. omniORB ==="
which omniNames
which omniidl

echo ""
echo "=== 3. MySQL Server ==="
mysql --version
sudo systemctl is-active mysql

echo ""
echo "=== 4. MySQL Connector/C++ ==="
ls /usr/include/mysqlcppconn/ 2>/dev/null | head -3 || echo "NON TROUVÉ"
ls /usr/lib/x86_64-linux-gnu/libmysqlcppconn* 2>/dev/null || echo "NON TROUVÉ"

echo ""
echo "=== 5. Variables d'environnement ==="
echo "JAVA_HOME=$JAVA_HOME"
echo "OMNIORB_CONFIG=$OMNIORB_CONFIG"

echo ""
echo "=== 6. Test tnameserv ==="
timeout 3 tnameserv -ORBInitialPort 2809 2>&1 | head -3
```

### Résumé des paquets à installer

| Composant | Paquet APT | Rôle |
|-----------|-----------|------|
| **Java 8 JDK** | `openjdk-8-jdk` | `idlj`, `tnameserv`, runtime Java |
| **omniORB** | `omniorb omniidl` | CORBA C++, `omniNames`, `omniidl` |
| **MySQL Server** | `mysql-server` | Base de données |
| **MySQL Connector/C++** | `libmysqlcppconn-dev` | Connexion MySQL depuis C++ |

Une fois toutes ces vérifications passées sans erreur, votre environnement est prêt pour le développement CORBA bidirectionnel Java ↔ C++.