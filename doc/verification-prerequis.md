# Vérification des prérequis — Projet CORBA (C++ / Java / MySQL)

Lance chaque bloc dans un terminal et compare avec le résultat attendu.

## 1. Java (JDK 8 — requis pour CORBA intégré `org.omg.CORBA`)

```bash
java -version
javac -version
which idlj
```
Attendu : `1.8.0_xxx` pour `java`/`javac`, et un chemin pour `idlj` (`/usr/bin/idlj`).

Si `java -version` affiche autre chose que du 1.8, force le JDK 8 par défaut :
```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
```

## 2. C++ / compilateur

```bash
g++ --version
gcc --version
make --version
```
Attendu : g++/gcc 13.x, GNU Make présent.

## 3. ORB C++ — omniORB

```bash
which omniidl
dpkg -l | grep -i omniorb
```
Attendu : `omniidl`, `libomniorb4-dev`, `omniorb-nameserver` listés en `ii` (installés).

## 4. MySQL Server

```bash
mysql --version
systemctl is-active mysql
sudo systemctl status mysql --no-pager
```
Attendu : version `8.0.x`, statut `active`.

Test de connexion (mot de passe root à définir si pas encore fait) :
```bash
sudo mysql -u root -e "SELECT VERSION();"
```

## 5. MySQL Connector/C++ (pour le code C++)

```bash
dpkg -l | grep -i libmysqlcppconn
ls /usr/include/mysql_connection.h /usr/include/mysql_driver.h /usr/include/mysql_error.h
```
Attendu : `libmysqlcppconn-dev` et `libmysqlcppconn7t64` en `ii`, les 3 headers présents.

Test de compilation minimal (fichier `test_mysql.cpp`) :
```cpp
#include <mysql_driver.h>
#include <mysql_connection.h>
int main() {
    sql::mysql::MySQL_Driver *driver = sql::mysql::get_mysql_driver_instance();
    return 0;
}
```
```bash
g++ test_mysql.cpp -o test_mysql -lmysqlcppconn
./test_mysql && echo "OK: connector C++ fonctionnel"
```

## 6. Python3 (requis par omniidl)

```bash
python3 --version
```

## 7. Récapitulatif rapide (tout-en-un)

```bash
echo "Java:" && java -version 2>&1 | head -1
echo "idlj:" && which idlj
echo "g++:" && g++ --version | head -1
echo "make:" && make --version | head -1
echo "omniidl:" && which omniidl
echo "MySQL:" && mysql --version
echo "MySQL service:" && systemctl is-active mysql
echo "Connector C++:" && dpkg -l | grep -c libmysqlcppconn-dev
echo "Python3:" && python3 --version
```

Si chaque ligne retourne une valeur (pas d'erreur "commande introuvable"), l'environnement est prêt pour :
- compiler des IDL en C++ (`omniidl`) et en Java (`idlj`)
- faire communiquer un client/serveur CORBA C++ ↔ Java
- connecter le C++ à MySQL via `libmysqlcppconn`
- lire/écrire des fichiers côté Java (aucune dépendance externe nécessaire)
