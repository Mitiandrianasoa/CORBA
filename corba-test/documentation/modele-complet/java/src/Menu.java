import modele.*;
import java.util.Scanner;

public class Menu {
    static void menu(ServiceBase base) {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n1) Lister  2) Ajouter  3) Retirer du stock  4) Supprimer  0) Quitter");
            System.out.print("Choix : ");
            if (!sc.hasNextLine()) return;              // Ctrl+D
            String choix = sc.nextLine().trim();
            try {
                switch (choix) {
                    case "1":
                        for (Produit p : base.lister())
                            System.out.println("#" + p.id + " " + p.nom + " x" + p.quantite);
                        break;
                    case "2":
                        System.out.print("Nom : ");      String nom = sc.nextLine().trim();
                        System.out.print("Quantite : "); int q = Integer.parseInt(sc.nextLine().trim());
                        System.out.print("Prix : ");     double prix = Double.parseDouble(sc.nextLine().trim());
                        System.out.println("Ajoute, id = " + base.ajouter(new Produit(0, nom, q, prix)));
                        break;
                    case "3":
                        System.out.print("Id : ");       int id = Integer.parseInt(sc.nextLine().trim());
                        System.out.print("Combien : ");  int n = Integer.parseInt(sc.nextLine().trim());
                        base.modifierQuantite(id, -n);
                        System.out.println("OK");
                        break;
                    case "4":
                        System.out.print("Id : ");
                        System.out.println(base.supprimer(Integer.parseInt(sc.nextLine().trim())) ? "Supprime" : "Id inconnu");
                        break;
                    case "0":
                        return;
                    default:
                        System.out.println("Choix invalide");
                }
            } catch (Refuse e) {
                System.out.println("Refuse : " + e.raison);
            } catch (Introuvable e) {
                System.out.println("Introuvable : id " + e.id);
            } catch (NumberFormatException e) {
                System.out.println("Nombre attendu");
            } catch (org.omg.CORBA.SystemException e) {
                System.out.println("Serveur C++ injoignable : " + e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        org.omg.CORBA.ORB orb = org.omg.CORBA.ORB.init(args, null);
        org.omg.CosNaming.NamingContextExt nc = org.omg.CosNaming.NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));
        menu(ServiceBaseHelper.narrow(nc.resolve_str("ServiceBase")));
    }
}
