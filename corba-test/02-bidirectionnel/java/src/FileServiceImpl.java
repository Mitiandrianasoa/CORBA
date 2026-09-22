import app.FileServicePOA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileServiceImpl extends FileServicePOA {
    // Fichier relatif au dossier depuis lequel JavaPeer est lance (02-bidirectionnel/)
    private static final Path FICHIER = Paths.get("data", "message.txt");

    public String readMessage() {
        try {
            if (!Files.exists(FICHIER)) {
                return "(fichier pas encore cree)";
            }
            byte[] contenu = Files.readAllBytes(FICHIER);
            return new String(contenu, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Erreur de lecture du fichier : " + e.getMessage();
        }
    }

    public void writeMessage(String msg) {
        try {
            Files.createDirectories(FICHIER.getParent());
            Files.write(FICHIER, msg.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Erreur d'ecriture du fichier : " + e.getMessage());
        }
    }
}
