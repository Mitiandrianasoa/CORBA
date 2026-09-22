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



Voici la version Windows de la liste complète. Attention : l'installation sur Windows est plus manuelle que sous Linux, notamment pour omniORB et la configuration de Visual Studio.

### 1. Java 8 (JDK) — Obligatoire pour CORBA

CORBA a été retiré à partir de Java 11. Il faut donc **Java 8**. Sur Windows, le plus simple est de passer par **Adoptium (Temurin)**.

**Installation :**
1. Va sur le site d'Adoptium : `https://adoptium.net/temurin/releases/?version=8`
2. Télécharge l'installeur **`.msi` (x64)** pour Windows.
3. Lance l'installeur et suis les étapes .

**Vérification (dans l'invite de commandes) :**
```cmd
java -version
REM Attendu : openjdk version "1.8.0_xxx"

where idlj
REM Attendu : C:\Program Files\Eclipse Adoptium\jdk-8...\bin\idlj.exe

idlj -version
REM Doit afficher la version
```

### 2. omniORB (Implémentation CORBA pour C++)

Sur Windows, il n'y a pas d'installeur automatique. Il faut télécharger une archive **binaire** compilée pour Visual Studio .

**Installation :**
1. Va sur la page de téléchargement d'omniORB sur OpenRTM.org ou SourceForge : `https://openrtm.org/pub/omniORB/win32/`
2. Choisis la version correspondant à ton Visual Studio. Par exemple `omniORB-4.3.2-x64-vc16.zip` (vc16 = Visual Studio 2019/2022) .
3. **Décompresse le fichier ZIP** dans un dossier simple, par exemple `C:\omniORB`.
4. **Ajoute le dossier `bin` à la variable d'environnement `Path`** :
   - `C:\omniORB\bin\x86_win32` (pour une build 32 bits) ou `C:\omniORB\bin\x64_win32` (pour 64 bits) .

**Vérification (dans une NOUVELLE invite de commandes) :**
```cmd
where omniidl
REM Attendu : C:\omniORB\bin\...\omniidl.exe

where omniNames
REM Attendu : C:\omniORB\bin\...\omniNames.exe

omniidl -bcxx -v
REM Doit afficher des informations de version
```

### 3. MySQL Server (Le serveur de base de données)

**Installation :**
1. Télécharge **MySQL Installer** sur le site officiel : `https://dev.mysql.com/downloads/installer/` .
2. Lance l'installeur et choisis **"Server Only"** ou **"Developer Default"** (qui inclut les outils) .
3. Suis les étapes de configuration. **Note bien le mot de passe root** que tu définis.
4. À la fin, MySQL est installé comme un service Windows et démarrera automatiquement .

**Vérification :**
```cmd
mysql --version
REM Attendu : mysql Ver 8.0.xx

REM Ouvre une invite de commandes en tant qu'Administrateur
sc query MySQL80
REM Attendu : STATE : 4 RUNNING (ou un nom similaire)
```

### 4. MySQL Connector/C++ (Bibliothèque de connexion C++)

Pour que ton code C++ puisse parler à MySQL.

**Installation :**
1. Va sur la page des téléchargements MySQL Connector/C++ : `https://dev.mysql.com/downloads/connector/cpp/`
2. Télécharge l'installeur **MSI** pour Windows .
3. Lance l'installeur. **Important** : choisis **"Custom"** et installe **le "Developer component"** en plus du "DLL component". C'est le Developer component qui contient les fichiers `.h` et `.lib` nécessaires à la compilation .
4. Installe aussi le **Visual C++ Redistributable** si demandé .

**Vérification :**
Ouvre l'explorateur de fichiers et navigue vers le dossier d'installation (souvent `C:\Program Files\MySQL\MySQL Connector C++ 8.0\`).
Tu dois voir les dossiers `include` et `lib`.

### 5. Configuration de l'environnement (Variables & omniORB.cfg)

**Créer le fichier de configuration omniORB :**
Crée un fichier `C:\omniORB\omniORB.cfg` (ou ailleurs) avec ce contenu :
```ini
InitRef = NameService=corbaname::localhost:2809
supportBootstrapAgent = 1
```
Puis définis la variable d'environnement système `OMNIORB_CONFIG` pointant vers ce fichier (ex: `C:\omniORB\omniORB.cfg`).

**Variables d'environnement à ajouter (Panneau de configuration > Système > Paramètres système avancés > Variables d'environnement) :**
- `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-8.0.xxx` (le chemin de ton JDK 8)
- `OMNIORB_CONFIG` = `C:\omniORB\omniORB.cfg`

**Vérification :**
Ferme et rouvre l'invite de commandes, puis :
```cmd
echo %JAVA_HOME%
echo %OMNIORB_CONFIG%
```

### 6. Vérification finale complète

```cmd
echo === 1. JAVA ===
java -version 2>&1
where idlj
where tnameserv

echo === 2. omniORB ===
where omniNames
where omniidl

echo === 3. MySQL Server ===
mysql --version
sc query MySQL80 | findstr STATE

echo === 4. MySQL Connector/C++ ===
dir "C:\Program Files\MySQL\MySQL Connector C++ 8.0\include" /b 2>nul | findstr /i "mysql_connection" || echo NON TROUVE
dir "C:\Program Files\MySQL\MySQL Connector C++ 8.0\lib" /b 2>nul | findstr /i "mysqlcppconn" || echo NON TROUVE

echo === 5. Variables d'environnement ===
echo JAVA_HOME=%JAVA_HOME%
echo OMNIORB_CONFIG=%OMNIORB_CONFIG%
```

### Résumé des paquets à installer (Windows)

| Composant | Source | Rôle |
|-----------|-----------|-----------|
| **Java 8 JDK** | Adoptium (Temurin) | `idlj`, `tnameserv`, runtime Java |
| **omniORB** | OpenRTM.org / SourceForge (fichier ZIP) | CORBA C++, `omniNames`, `omniidl` |
| **MySQL Server** | MySQL Installer (MSI) | Base de données |
| **MySQL Connector/C++** | MySQL (MSI) | Connexion MySQL depuis C++ |

### ⚠️ Note importante pour la compilation C++ sous Windows

Pour compiler un projet C++ qui utilise omniORB et MySQL Connector, tu **dois** utiliser la **"Developer Command Prompt for VS"** de Visual Studio. C'est le seul moyen d'avoir les bons chemins vers le compilateur `cl.exe` et les bibliothèques.

Dans les propriétés de ton projet Visual Studio (ou VS Code), tu devras ajouter manuellement :
- **Include directories** : `C:\omniORB\include` et `C:\Program Files\MySQL\MySQL Connector C++ 8.0\include`
- **Library directories** : `C:\omniORB\lib\x64_win32` et `C:\Program Files\MySQL\MySQL Connector C++ 8.0\lib64\vs14` (le nom `vs14` peut changer selon ta version de Connector)
- **Librairies à l'édition de liens** : `omniORB4.lib`, `omnithread.lib`, `ws2_32.lib`, `advapi32.lib`, `mysqlcppconn.lib` 
- **Macros de préprocesseur** : `__WIN32__`, `__x86__` (ou `__x64__`), `__NT__`, `__OSVERSION__=4` 

C'est la partie la plus délicate de la configuration Windows.