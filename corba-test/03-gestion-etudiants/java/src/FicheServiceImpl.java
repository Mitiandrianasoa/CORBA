import ecole.Etudiant;
import ecole.FicheServicePOA;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FicheServiceImpl extends FicheServicePOA {

    // Appele par C++ (genererFiche) : ecrit la fiche complete d'un etudiant dans un fichier.
    public void ecrireFiche(Etudiant e, double moyenne, String mention) {
        Path dossier = Paths.get("data", "fiches");
        Path fichier = dossier.resolve(e.nom + "_" + e.prenom + ".txt");

        String contenu = String.format(
                "Fiche etudiant%n" +
                "===============%n" +
                "Nom     : %s%n" +
                "Prenom  : %s%n" +
                "Classe  : %s%n" +
                "Moyenne : %.2f%n" +
                "Mention : %s%n",
                e.nom, e.prenom, e.classe, moyenne, mention);

        try {
            Files.createDirectories(dossier);
            Files.write(fichier, contenu.getBytes(StandardCharsets.UTF_8));
            System.out.println("[Java] Fiche ecrite : " + fichier);
        } catch (IOException ex) {
            System.err.println("[Java] Erreur d'ecriture de la fiche : " + ex.getMessage());
        }
    }
}
