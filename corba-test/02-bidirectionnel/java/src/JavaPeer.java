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
