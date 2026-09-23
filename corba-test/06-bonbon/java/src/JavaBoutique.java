import boutique.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.*;
import org.omg.PortableServer.*;

public class JavaBoutique {
    public static void main(String[] args) throws Exception {
        ORB orb = ORB.init(args, null);                                          // 0. ORB

        POA poa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));   // S1. POA
        poa.the_POAManager().activate();

        NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));                  // 0. annuaire

        // --- PARTIE SERVEUR : Commande, que C++ va appeler ---
        CommandeImpl impl = new CommandeImpl();                                  // S2
        Commande cmdRef = CommandeHelper.narrow(poa.servant_to_reference(impl)); // S3
        nc.rebind(nc.to_name("Commande"), cmdRef);                               // S4

        Thread t = new Thread(orb::run);   // S5 dans un thread : le main reste libre pour la partie client
        t.setDaemon(true);
        t.start();

        // --- PARTIE CLIENTE : j'appelle Stock (C++) ---
        Stock stock = StockHelper.narrow(nc.resolve_str("Stock"));               // C1 + C2
        try {
            int restant = stock.acheter("bonbon");                               // C3
            System.out.println("[Java] Achat OK, il reste " + restant + " bonbons");
        } catch (StockInsuffisant e) {
            System.out.println("[Java] Stock insuffisant, seulement " + e.disponible + " disponibles");
        }
        orb.shutdown(false);
    }
}