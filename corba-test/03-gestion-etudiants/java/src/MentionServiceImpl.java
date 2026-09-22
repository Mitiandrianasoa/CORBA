import ecole.MentionServicePOA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MentionServiceImpl extends MentionServicePOA {
    private static final Path BAREME = Paths.get("data", "bareme_mentions.txt");

    // Calcule la mention en lisant le bareme depuis le fichier a chaque appel
    // (permet de changer le fichier sans recompiler/redemarrer).
    public String calculerMention(double moyenne) {
        List<Double> valeurs = new ArrayList<>();
        List<String> libelles = new ArrayList<>();

        try {
            for (String ligne : Files.readAllLines(BAREME, StandardCharsets.UTF_8)) {
                ligne = ligne.trim();
                if (ligne.isEmpty()) continue;
                String[] parts = ligne.split(":", 2);
                valeurs.add(Double.parseDouble(parts[0].trim()));
                libelles.add(parts[1].trim());
            }
        } catch (IOException e) {
            System.err.println("[Java] Impossible de lire " + BAREME + " : " + e.getMessage());
            return "(bareme indisponible)";
        }

        // Le fichier est ecrit du seuil le plus haut au plus bas : on prend
        // le premier seuil que la moyenne atteint ou depasse.
        for (int i = 0; i < valeurs.size(); i++) {
            if (moyenne >= valeurs.get(i)) {
                return libelles.get(i);
            }
        }
        return "Insuffisant";
    }
}
