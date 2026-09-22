import ecole.PenaliteServicePOA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PenaliteServiceImpl extends PenaliteServicePOA {
    private static final Path FICHIER = Paths.get("data", "penalites.txt");

    // Appele par C++ (appliquerPenalite) : lit data/penalites.txt (format "matiere:points")
    // et renvoie la penalite pour cette matiere, ou 0 si aucune regle definie.
    public double obtenirPenalite(String matiere) {
        try {
            if (!Files.exists(FICHIER)) {
                System.out.println("[Java] " + FICHIER + " introuvable, aucune penalite.");
                return 0.0;
            }
            for (String ligne : Files.readAllLines(FICHIER, StandardCharsets.UTF_8)) {
                ligne = ligne.trim();
                if (ligne.isEmpty()) continue;
                String[] parts = ligne.split(":", 2);
                if (parts[0].trim().equalsIgnoreCase(matiere.trim())) {
                    double valeur = Double.parseDouble(parts[1].trim());
                    System.out.println("[Java] Penalite trouvee pour \"" + matiere + "\" : " + valeur + " point(s)");
                    return valeur;
                }
            }
            System.out.println("[Java] Aucune penalite definie pour \"" + matiere + "\" dans " + FICHIER);
            return 0.0;
        } catch (IOException e) {
            System.err.println("[Java] Erreur de lecture de " + FICHIER + " : " + e.getMessage());
            return 0.0;
        }
    }
}
