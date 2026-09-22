import chat.ModerateurPOA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

// Servant Java appele par le serveur C++ (comme FileServiceImpl dans 02).
// Il lit la liste des mots interdits dans data/tabou.txt.
public class ModerateurImpl extends ModerateurPOA {
    // Fichier relatif au dossier depuis lequel on lance le client (05-messagerie/)
    private static final Path FICHIER = Paths.get("data", "tabou.txt");

    public String chercherMotTabou(String contenu) {
        List<String> tabous;
        try {
            // Relu a chaque appel : on peut modifier tabou.txt sans relancer
            tabous = Files.readAllLines(FICHIER, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Java] Impossible de lire " + FICHIER + " : " + e.getMessage());
            return "";
        }

        // On decoupe le message en mots (tout ce qui n'est pas une lettre separe)
        for (String mot : contenu.toLowerCase().split("[^\\p{L}]+")) {
            for (String tabou : tabous) {
                if (!tabou.trim().isEmpty() && mot.equals(tabou.trim().toLowerCase())) {
                    System.out.println("[Java] Moderateur : mot tabou \"" + mot + "\" detecte");
                    return mot;
                }
            }
        }
        return "";
    }
}
