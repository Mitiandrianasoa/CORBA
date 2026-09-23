import modele.Refuse;
import modele.ServiceFichierPOA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

// Servant Java : tout ce qui touche aux fichiers (chemins relatifs au dossier de lancement)
public class ServiceFichierImpl extends ServiceFichierPOA {

    // Lire toutes les lignes (non vides) d'un fichier -> sequence<string>
    public String[] lireLignes(String fichier) {
        List<String> resultat = new ArrayList<>();
        try {
            for (String ligne : Files.readAllLines(Paths.get(fichier), StandardCharsets.UTF_8)) {
                if (!ligne.trim().isEmpty()) resultat.add(ligne.trim());
            }
        } catch (IOException e) {
            System.err.println("[Java] Lecture impossible " + fichier + " : " + e.getMessage());
        }
        return resultat.toArray(new String[0]);
    }

    // Ajouter une ligne a la fin (cree le fichier s'il n'existe pas)
    public void ajouterLigne(String fichier, String ligne) {
        try {
            Files.write(Paths.get(fichier), (ligne + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[Java] Ecriture impossible " + fichier + " : " + e.getMessage());
        }
    }

    // La valeur est-elle une ligne du fichier ? (insensible a la casse)
    public boolean contient(String fichier, String valeur) {
        for (String ligne : lireLignes(fichier)) {
            if (ligne.equalsIgnoreCase(valeur.trim())) return true;
        }
        return false;
    }

    // Fichier "cle=valeur" -> valeur entiere, ou exception si la cle est absente
    public int lireValeur(String fichier, String cle) throws Refuse {
        for (String ligne : lireLignes(fichier)) {
            String[] p = ligne.split("=", 2);
            if (p.length == 2 && p[0].trim().equals(cle)) {
                return Integer.parseInt(p[1].trim());
            }
        }
        throw new Refuse("cle absente : " + cle);
    }
}
