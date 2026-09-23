cd corba-test/06-bonbon

# 1. Une seule fois : la table MySQL (script.sql n'a pas de "USE", donc on donne la base)
mysql -u corba_user -pcorba corba_demo < script.sql

# 2. Seulement si boutique.idl change : régénérer les deux côtés
(cd java/src && idlj -fall ../../boutique.idl)
(cd cpp/src && omniidl -bcxx ../../boutique.idl)

# 3. Compiler Java
mkdir -p java/bin
(cd java/src && javac -d ../bin boutique/*.java CommandeImpl.java JavaBoutique.java)

# 4. Compiler C++
(cd cpp && make)

# 5. Lancer, depuis 06-bonbon (Java lit data/commande.txt en chemin relatif)
./cpp/bin/cpp_peer -ORBInitRef NameService=corbaname::localhost:2809                    # terminal A, en premier
java -cp java/bin JavaBoutique -ORBInitRef NameService=corbaname::localhost:2809        # terminal B
