import modele.*;
import org.omg.CORBA.DoubleHolder;
import org.omg.CORBA.IntHolder;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextPackage.NotFound;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class JavaPeer {

    // Attendre qu'un nom apparaisse dans l'annuaire (l'autre programme n'est pas encore lance)
    static org.omg.CORBA.Object attendre(NamingContextExt nc, String nom) throws Exception {
        for (int essai = 1; essai <= 30; essai++) {
            try {
                return nc.resolve_str(nom);
            } catch (NotFound e) {
                System.out.println("[Java] " + nom + " pas encore la, essai " + essai);
                Thread.sleep(1000);
            }
        }
        throw new Exception(nom + " introuvable dans l'annuaire");
    }

    // Lire un CSV "nom;quantite;prix" (1re ligne = en-tete) -> tableau de struct
    static Produit[] lireCsv(String fichier) throws Exception {
        List<Produit> liste = new ArrayList<>();
        List<String> lignes = Files.readAllLines(Paths.get(fichier), StandardCharsets.UTF_8);
        for (int i = 1; i < lignes.size(); i++) {           // i = 1 : on saute l'en-tete
            String[] c = lignes.get(i).split(";");
            if (c.length < 3) continue;
            liste.add(new Produit(0, c[0].trim(), Integer.parseInt(c[1].trim()),
                    Double.parseDouble(c[2].trim())));
        }
        return liste.toArray(new Produit[0]);
    }

    static void afficher(Produit[] liste) {
        for (Produit p : liste) {
            System.out.println("   #" + p.id + " " + p.nom + " x" + p.quantite + " a " + p.prix);
        }
    }

    public static void main(String[] args) throws Exception {
        ORB orb = ORB.init(args, null);

        POA poa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
        poa.the_POAManager().activate();

        NamingContextExt nc = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));

        // --- SERVEUR 1 : ServiceFichier, publie dans l'annuaire ---
        ServiceFichierImpl fichierImpl = new ServiceFichierImpl();
        ServiceFichier fichierRef = ServiceFichierHelper.narrow(poa.servant_to_reference(fichierImpl));
        nc.rebind(nc.to_name("ServiceFichier"), fichierRef);

        // --- SERVEUR 2 : Notifiable, PAS dans l'annuaire (donne via sAbonner) ---
        Notifiable notifRef = NotifiableHelper.narrow(poa.servant_to_reference(new NotifiableImpl()));

        Thread t = new Thread(orb::run);
        t.setDaemon(true);
        t.start();

        // --- CLIENT : ServiceBase (C++) ---
        ServiceBase base = ServiceBaseHelper.narrow(attendre(nc, "ServiceBase"));
        base.sAbonner(notifRef);

        System.out.println("== ajouter (struct en parametre) ==");
        int id = base.ajouter(new Produit(0, "bonbon", 20, 0.5));
        System.out.println("   id genere : " + id);

        System.out.println("== ajouter refuse par Java (nom absent de autorises.txt) ==");
        try {
            base.ajouter(new Produit(0, "interdit", 1, 1.0));
        } catch (Refuse e) {
            System.out.println("   Refuse : " + e.raison);
        }

        System.out.println("== importer (sequence en parametre, lue dans un CSV) ==");
        System.out.println("   " + base.importer(lireCsv("data/produits.csv")) + " produits importes");

        System.out.println("== lister (sequence en retour) ==");
        afficher(base.lister());

        System.out.println("== chercher \"choc\" ==");
        afficher(base.chercher("choc"));

        System.out.println("== trouver (struct en retour + exception) ==");
        Produit p = base.trouver(id);
        System.out.println("   trouve : " + p.nom + " x" + p.quantite);
        try {
            base.trouver(999999);
        } catch (Introuvable e) {
            System.out.println("   Introuvable : id " + e.id);
        }

        System.out.println("== modifierQuantite (2 exceptions possibles) ==");
        int qte = fichierImpl.lireValeur("data/config.txt", "bonbon");   // appel LOCAL, pas CORBA
        base.modifierQuantite(id, -qte);
        System.out.println("   retire " + qte + ", reste " + base.trouver(id).quantite);
        try {
            base.modifierQuantite(id, -1000);
        } catch (Refuse e) {
            System.out.println("   Refuse : " + e.raison);
        } catch (Introuvable e) {
            System.out.println("   Introuvable : id " + e.id);
        }

        System.out.println("== statistiques (parametres out) ==");
        IntHolder nombre = new IntHolder();
        DoubleHolder total = new DoubleHolder();
        base.statistiques(nombre, total);
        System.out.println("   " + nombre.value + " produits, valeur totale " + total.value);

        System.out.println("== ecrire le resultat dans un fichier ==");
        List<String> sortie = new ArrayList<>();
        for (Produit x : base.lister()) sortie.add(x.nom + ";" + x.quantite);
        Files.write(Paths.get("data/export.txt"), sortie, StandardCharsets.UTF_8);
        System.out.println("   data/export.txt ecrit (" + sortie.size() + " lignes)");

        System.out.println("== supprimer ==");
        System.out.println("   supprime : " + base.supprimer(id) + ", encore : " + base.supprimer(id));

        orb.shutdown(false);
    }
}
