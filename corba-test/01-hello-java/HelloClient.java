import helloapp.Hello;
import helloapp.HelloHelper;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

public class HelloClient {
    public static void main(String[] args) {
        try {
            ORB orb = ORB.init(args, null);

            // Rechercher le service de nommage
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            // Trouver l'objet distant "Hello" par son nom
            Hello hello = HelloHelper.narrow(ncRef.resolve_str("Hello"));

            // Appeler la methode distante comme un appel local
            System.out.println("Reponse : " + hello.sayHello());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
