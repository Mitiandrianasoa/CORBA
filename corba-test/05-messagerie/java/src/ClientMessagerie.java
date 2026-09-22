import chat.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ClientMessagerie {
    public static void main(String[] args) throws Exception {
        // Le pseudo est le premier argument qui n'est pas une option -ORB...
        String pseudo = (args.length > 0 && !args[0].startsWith("-")) ? args[0] : "Anonyme";

        ORB orb = ORB.init(args, null);

        POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
        rootpoa.the_POAManager().activate();

        // --- PARTIE SERVEUR : je cree MON objet Notifiable ---
        // Pas besoin de l'enregistrer dans l'annuaire : je vais donner sa
        // reference directement au serveur C++ via sAbonner().
        NotifiableImpl notifImpl = new NotifiableImpl(pseudo);
        org.omg.CORBA.Object ref = rootpoa.servant_to_reference(notifImpl);
        Notifiable monNotifiable = NotifiableHelper.narrow(ref);

        // --- PARTIE SERVEUR (2) : mon Moderateur, que C++ appellera ---
        // Lui, on l'inscrit dans l'annuaire : c'est C++ qui va le chercher
        // par son nom (comme FileService dans 02-bidirectionnel).
        ModerateurImpl modImpl = new ModerateurImpl();
        Moderateur modRef = ModerateurHelper.narrow(rootpoa.servant_to_reference(modImpl));

        // orb.run() bloque pour toujours : on le lance dans un thread a part,
        // AVANT sAbonner(), pour pouvoir recevoir les rappels du serveur
        // tout en gardant le thread principal pour lire le clavier.
        Thread threadOrb = new Thread(orb::run);
        threadOrb.setDaemon(true);
        threadOrb.start();

        // --- PARTIE CLIENTE : je trouve la Messagerie (cote C++) ---
        NamingContextExt ncRef = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));
        Messagerie messagerie = MessagerieHelper.narrow(ncRef.resolve_str("Messagerie"));

        // J'inscris mon Moderateur : C++ le retrouvera avec resolve_str("Moderateur")
        ncRef.rebind(ncRef.to_name("Moderateur"), modRef);
        System.out.println("[Java] Moderateur enregistre dans l'annuaire");

        // Je donne au serveur une reference vers MOI (un objet, pas une string)
        messagerie.sAbonner(monNotifiable);
        System.out.println("[Java] " + pseudo + " abonne. Tape un message puis Entree (Ctrl+D pour quitter).");

        BufferedReader clavier = new BufferedReader(new InputStreamReader(System.in));
        String ligne;
        while ((ligne = clavier.readLine()) != null) {
            if (!ligne.isEmpty()) {
                try {
                    messagerie.envoyer(pseudo, ligne);
                } catch (MessageInterdit e) {
                    // Exception levee par C++ : le message n'a ete ni stocke ni diffuse
                    System.out.println("[Java] Message refuse : le mot \"" + e.mot + "\" est interdit");
                }
            }
        }

        orb.shutdown(false);
    }
}
