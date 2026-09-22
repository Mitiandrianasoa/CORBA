import ecole.ValidationServicePOA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ValidationServiceImpl extends ValidationServicePOA {
    private static final Path FICHIER = Paths.get("data", "classes_valides.txt");

    // Appele par C++ (changerClasse) : verifie que nomClasse figure dans data/classes_valides.txt.
    public boolean classeValide(String nomClasse) {
        try {
            if (!Files.exists(FICHIER)) {
                System.out.println("[Java] " + FICHIER + " introuvable, aucune classe consideree valide.");
                return false;
            }
            for (String ligne : Files.readAllLines(FICHIER, StandardCharsets.UTF_8)) {
                if (ligne.trim().equalsIgnoreCase(nomClasse.trim())) {
                    System.out.println("[Java] Classe \"" + nomClasse + "\" trouvee dans " + FICHIER + " : valide.");
                    return true;
                }
            }
            System.out.println("[Java] Classe \"" + nomClasse + "\" absente de " + FICHIER + " : invalide.");
            return false;
        } catch (IOException e) {
            System.err.println("[Java] Erreur de lecture de " + FICHIER + " : " + e.getMessage());
            return false;
        }
    }
}
