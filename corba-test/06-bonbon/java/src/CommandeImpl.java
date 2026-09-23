import boutique.CommandePOA;
import java.nio.file.*;

public class CommandeImpl extends CommandePOA {      // je SERS Commande
    public int quantiteDemandee(String produit) {    // IDL "long" = Java "int"
        try {
            for (String ligne : Files.readAllLines(Paths.get("data", "commande.txt"))) {
                String[] p = ligne.split("=");       // "bonbon=5"
                if (p.length == 2 && p[0].trim().equals(produit))
                    return Integer.parseInt(p[1].trim());
            }
        } catch (Exception e) {
            System.err.println("[Java] Lecture commande.txt impossible : " + e.getMessage());
        }
        return 0;
    }
}
