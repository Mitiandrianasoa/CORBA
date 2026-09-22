import helloapp.Hello;
import helloapp.HelloHelper;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

public class HelloServer {
    public static void main(String[] args) {
        try {
            // 1. Initialiser l'ORB
            ORB orb = ORB.init(args, null);

            // 2. Recuperer et activer le POA racine (gere le cycle de vie des objets distants)
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            // 3. Creer l'objet servant (l'implementation concrete)
            HelloImpl helloImpl = new HelloImpl();

            // 4. Obtenir la reference CORBA associee au servant
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(helloImpl);
            Hello href = HelloHelper.narrow(ref);

            // 5. Enregistrer l'objet dans le service de nommage sous le nom "Hello"
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            NameComponent[] path = ncRef.to_name("Hello");
            ncRef.rebind(path, href);

            System.out.println("Serveur pret, en attente de requetes...");
            orb.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
