import ecole.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class JavaPeer {

    public static void main(String[] args) throws Exception {
        ORB orb = ORB.init(args, null);

        NamingContextExt ncRef = NamingContextExtHelper.narrow(
                orb.resolve_initial_references("NameService"));

        GestionEtudiants service =
                GestionEtudiantsHelper.narrow(ncRef.resolve_str("GestionEtudiants"));

        // --- PARTIE SERVEUR : Java sert MentionService, pour que C++ puisse le rappeler ---
        POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
        rootpoa.the_POAManager().activate();

        MentionServiceImpl mentionImpl = new MentionServiceImpl();
        org.omg.CORBA.Object mentionRef = rootpoa.servant_to_reference(mentionImpl);
        MentionService mentionService = MentionServiceHelper.narrow(mentionRef);

        service.enregistrerMentionService(mentionService);
        System.out.println("MentionService (Java) enregistre aupres de C++.");

        // --- PARTIE SERVEUR : Java sert aussi FicheService et ValidationService ---
        FicheServiceImpl ficheImpl = new FicheServiceImpl();
        org.omg.CORBA.Object ficheRef = rootpoa.servant_to_reference(ficheImpl);
        FicheService ficheService = FicheServiceHelper.narrow(ficheRef);
        service.enregistrerFicheService(ficheService);
        System.out.println("FicheService (Java) enregistre aupres de C++.");

        ValidationServiceImpl validationImpl = new ValidationServiceImpl();
        org.omg.CORBA.Object validationRef = rootpoa.servant_to_reference(validationImpl);
        ValidationService validationService = ValidationServiceHelper.narrow(validationRef);
        service.enregistrerValidationService(validationService);
        System.out.println("ValidationService (Java) enregistre aupres de C++.");

        PenaliteServiceImpl penaliteImpl = new PenaliteServiceImpl();
        org.omg.CORBA.Object penaliteRef = rootpoa.servant_to_reference(penaliteImpl);
        PenaliteService penaliteService = PenaliteServiceHelper.narrow(penaliteRef);
        service.enregistrerPenaliteService(penaliteService);
        System.out.println("PenaliteService (Java) enregistre aupres de C++.");
        System.out.println();

        System.out.println("=== 1. Import des etudiants depuis data/etudiants.csv ===");
        Map<String, Long> idsParNom = importerEtudiants(service, Paths.get("data", "etudiants.csv"));

        System.out.println();
        System.out.println("=== 2. Import des notes depuis data/notes.csv ===");
        importerNotes(service, Paths.get("data", "notes.csv"), idsParNom);

        System.out.println();
        System.out.println("=== 2b. Calcul de note : Java calcule depuis data/notes_brutes.csv, insere via C++ ===");
        calculerEtInsererNotes(service, Paths.get("data", "notes_brutes.csv"), idsParNom);

        System.out.println();
        System.out.println("=== 3. Liste des etudiants avec moyenne (calculee cote C++/MySQL) ===");
        afficherEtudiantsAvecMoyenne(service);

        System.out.println();
        System.out.println("=== 4. Moyenne d'un etudiant precis, ecrite dans un fichier ===");
        ecrireMoyenneDansFichier(service, "Rakoto", "Jean", idsParNom, Paths.get("data", "moyenne_etudiant.txt"));

        System.out.println();
        System.out.println("=== 5. Noms des etudiants ecrits dans un fichier ===");
        ecrireNomsDansFichier(service, Paths.get("data", "liste_etudiants.txt"));

        System.out.println();
        System.out.println("=== 6. Suppression : Java lit data/delete.txt et appelle supprimerEtudiant (C++) ===");
        supprimerDepuisFichier(service, Paths.get("data", "delete.txt"), idsParNom);

        System.out.println();
        System.out.println("=== 7. Fiches : C++ recherche l'etudiant (rechercherEtudiant) puis demande a Java d'ecrire sa fiche ===");
        genererFiches(service);

        System.out.println();
        System.out.println("=== 8. Changement de classe : C++ valide aupres de Java avant d'appeler modifierEtudiant ===");
        changerClasseDemo(service, idsParNom);

        System.out.println();
        System.out.println("=== 9. Penalite : C++ demande la valeur a Java (fichier), puis l'applique en MySQL ===");
        appliquerPenaliteDemo(service, idsParNom);
    }

    // --- Etape "creation d'etudiant depuis un fichier" ---
    // Format attendu par ligne : nom;prenom;classe
    private static Map<String, Long> importerEtudiants(GestionEtudiants service, Path fichier) throws IOException {
        Map<String, Long> ids = new HashMap<>();
        if (!Files.exists(fichier)) {
            System.out.println("Fichier " + fichier + " introuvable, aucun import.");
            return ids;
        }
        for (String ligne : lireLignes(fichier)) {
            if (ligne.trim().isEmpty()) continue;
            String[] champs = ligne.split(";");
            String nom = champs[0].trim();
            String prenom = champs[1].trim();
            String classe = champs[2].trim();

            long id = service.ajouterEtudiant(nom, prenom, classe);
            ids.put(cle(nom, prenom), id);
            System.out.println("  -> " + nom + " " + prenom + " (" + classe + ") enregistre avec id=" + id);
        }
        return ids;
    }

    // Format attendu par ligne : nom;prenom;matiere;note
    private static void importerNotes(GestionEtudiants service, Path fichier, Map<String, Long> idsParNom) throws IOException {
        if (!Files.exists(fichier)) {
            System.out.println("Fichier " + fichier + " introuvable, aucune note importee.");
            return;
        }
        for (String ligne : lireLignes(fichier)) {
            if (ligne.trim().isEmpty()) continue;
            String[] champs = ligne.split(";");
            String nom = champs[0].trim();
            String prenom = champs[1].trim();
            String matiere = champs[2].trim();
            double note = Double.parseDouble(champs[3].trim());

            Long id = idsParNom.get(cle(nom, prenom));
            if (id == null) {
                System.out.println("  ! Etudiant inconnu pour la note : " + nom + " " + prenom);
                continue;
            }
            service.ajouterNote(id.intValue(), matiere, note);
            System.out.println("  -> " + nom + " " + prenom + " : " + matiere + " = " + note);
        }
    }

    // --- Etape "Java -> C++ : Java calcule une note pour une matiere, puis l'insere via ajouterNote (C++/MySQL)" ---
    // Format attendu par ligne : nom;prenom;matiere;note1;note2;note3
    private static void calculerEtInsererNotes(GestionEtudiants service, Path fichier, Map<String, Long> idsParNom) throws IOException {
        if (!Files.exists(fichier)) {
            System.out.println("Fichier " + fichier + " introuvable, aucune note calculee.");
            return;
        }
        for (String ligne : lireLignes(fichier)) {
            if (ligne.trim().isEmpty()) continue;
            String[] champs = ligne.split(";");
            String nom = champs[0].trim();
            String prenom = champs[1].trim();
            String matiere = champs[2].trim();

            // Le calcul se fait ICI, cote Java : moyenne des notes brutes lues dans le fichier.
            double somme = 0;
            int nb = champs.length - 3;
            for (int i = 3; i < champs.length; i++) {
                somme += Double.parseDouble(champs[i].trim());
            }
            double noteCalculee = somme / nb;

            Long id = idsParNom.get(cle(nom, prenom));
            if (id == null) {
                System.out.println("  ! Etudiant inconnu : " + nom + " " + prenom);
                continue;
            }
            service.ajouterNote(id.intValue(), matiere, noteCalculee); // Java -> C++ : insertion MySQL
            System.out.printf("  -> %s %s : %s calculee = %.2f (moyenne de %d notes brutes), inseree via C++%n",
                    nom, prenom, matiere, noteCalculee, nb);
        }
    }

    // --- Etape "affichage depuis Java, moyenne calculee depuis C++, mention calculee par Java (rappelee par C++)" ---
    private static void afficherEtudiantsAvecMoyenne(GestionEtudiants service) {
        Etudiant[] etudiants = service.listerEtudiants();
        for (Etudiant e : etudiants) {
            try {
                double moyenne = service.calculerMoyenne(e.id);
                String mention = service.obtenirMention(e.id); // C++ rappelle Java en interne ici
                String alerte = (moyenne < 12) ? "  <-- EN DIFFICULTE (moyenne < 12)" : "";
                System.out.printf("  #%d %-10s %-10s (%s) - moyenne : %.2f (%s)%s%n",
                        e.id, e.nom, e.prenom, e.classe, moyenne, mention, alerte);
            } catch (EtudiantIntrouvable ex) {
                System.out.println("  #" + e.id + " " + e.nom + " " + e.prenom + " - erreur : etudiant introuvable cote serveur");
            }
        }
    }

    // --- Etape "Java appelle calculerMoyenne pour UNE personne et ecrit le resultat dans un fichier" ---
    private static void ecrireMoyenneDansFichier(GestionEtudiants service, String nom, String prenom,
                                                  Map<String, Long> idsParNom, Path fichier) throws IOException {
        Long id = idsParNom.get(cle(nom, prenom));
        if (id == null) {
            System.out.println("  ! Etudiant inconnu : " + nom + " " + prenom + " (rien ecrit)");
            return;
        }

        String ligne;
        try {
            double moyenne = service.calculerMoyenne(id.intValue());
            ligne = String.format("%s %s : moyenne = %.2f", nom, prenom, moyenne);
        } catch (EtudiantIntrouvable ex) {
            ligne = String.format("%s %s : etudiant introuvable cote serveur", nom, prenom);
        }

        Files.write(fichier, ligne.getBytes(StandardCharsets.UTF_8));
        System.out.println("  -> Ecrit dans " + fichier + " : " + ligne);
    }

    // --- Etape "les noms des etudiants ecrits dans un fichier" ---
    private static void ecrireNomsDansFichier(GestionEtudiants service, Path fichier) throws IOException {
        Etudiant[] etudiants = service.listerEtudiants();
        StringBuilder contenu = new StringBuilder();
        for (Etudiant e : etudiants) {
            contenu.append(e.nom).append(" ").append(e.prenom).append(System.lineSeparator());
        }
        Files.write(fichier, contenu.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("  -> " + etudiants.length + " nom(s) ecrit(s) dans " + fichier);
    }

    // --- Etape "Java lit delete.txt et appelle lui-meme la fonction delete cote C++" ---
    // Format attendu par ligne : nom;prenom
    private static void supprimerDepuisFichier(GestionEtudiants service, Path fichier, Map<String, Long> idsParNom) throws IOException {
        if (!Files.exists(fichier)) {
            System.out.println("Fichier " + fichier + " introuvable, rien a supprimer.");
            return;
        }
        int nbSupprimes = 0;
        for (String ligne : lireLignes(fichier)) {
            if (ligne.trim().isEmpty()) continue;
            String[] champs = ligne.split(";");
            String nom = champs[0].trim();
            String prenom = champs[1].trim();

            Long id = idsParNom.get(cle(nom, prenom));
            if (id == null) {
                System.out.println("  ! Etudiant inconnu : " + nom + " " + prenom + " (rien supprime)");
                continue;
            }
            boolean supprime = service.supprimerEtudiant(id.intValue());
            System.out.println("  -> " + nom + " " + prenom + " (id=" + id + ") : "
                    + (supprime ? "supprime (C++/MySQL)" : "echec"));
            if (supprime) nbSupprimes++;
        }
        System.out.println("  -> Total : " + nbSupprimes + " etudiant(s) supprime(s).");
    }

    // --- Etape "C++ recherche l'etudiant puis demande a Java d'ecrire sa fiche" ---
    private static void genererFiches(GestionEtudiants service) {
        Etudiant[] etudiants = service.listerEtudiants();
        for (Etudiant e : etudiants) {
            try {
                service.genererFiche(e.id); // declenche rechercherEtudiant + ecrireFiche cote C++
                System.out.println("  -> Fiche demandee pour " + e.nom + " " + e.prenom);
            } catch (EtudiantIntrouvable ex) {
                System.out.println("  ! Etudiant #" + e.id + " introuvable au moment de la fiche");
            }
        }
    }

    // --- Etape "C++ demande a Java si la classe est valide avant de modifier (modifierEtudiant)" ---
    private static void changerClasseDemo(GestionEtudiants service, Map<String, Long> idsParNom) {
        essayerChangerClasse(service, "Rakoto", "Jean", "Terminale D", idsParNom);   // invalide, hors liste
        essayerChangerClasse(service, "Ravao", "Sophie", "Terminale L", idsParNom);  // valide
    }

    private static void essayerChangerClasse(GestionEtudiants service, String nom, String prenom,
                                              String nouvelleClasse, Map<String, Long> idsParNom) {
        Long id = idsParNom.get(cle(nom, prenom));
        if (id == null) {
            System.out.println("  ! Etudiant inconnu : " + nom + " " + prenom);
            return;
        }
        try {
            boolean ok = service.changerClasse(id.intValue(), nouvelleClasse);
            if (ok) {
                System.out.println("  -> " + nom + " " + prenom + " change vers \"" + nouvelleClasse + "\" : OK");
            } else {
                System.out.println("  -> " + nom + " " + prenom + " vers \"" + nouvelleClasse
                        + "\" : ERREUR, classe non autorisee (voir data/classes_valides.txt)");
            }
        } catch (EtudiantIntrouvable ex) {
            System.out.println("  ! Etudiant #" + id + " introuvable");
        }
    }

    // --- Etape "C++ demande a Java la penalite (fichier), puis l'applique en MySQL" ---
    private static void appliquerPenaliteDemo(GestionEtudiants service, Map<String, Long> idsParNom) {
        essayerPenalite(service, "Rakoto", "Jean", "Maths", idsParNom);      // penalite definie -> appliquee
        essayerPenalite(service, "Rabe", "Marie", "Physique", idsParNom);   // aucune penalite -> refusee
    }

    private static void essayerPenalite(GestionEtudiants service, String nom, String prenom,
                                         String matiere, Map<String, Long> idsParNom) {
        Long id = idsParNom.get(cle(nom, prenom));
        if (id == null) {
            System.out.println("  ! Etudiant inconnu : " + nom + " " + prenom);
            return;
        }
        try {
            boolean appliquee = service.appliquerPenalite(id.intValue(), matiere);
            System.out.println("  -> Penalite " + matiere + " pour " + nom + " " + prenom + " : "
                    + (appliquee ? "appliquee (voir MySQL)" : "non appliquee (pas de regle ou pas de note)"));
        } catch (EtudiantIntrouvable ex) {
            System.out.println("  ! Etudiant #" + id + " introuvable");
        }
    }

    private static String cle(String nom, String prenom) {
        return nom.toLowerCase() + "|" + prenom.toLowerCase();
    }

    private static java.util.List<String> lireLignes(Path fichier) throws IOException {
        byte[] contenu = Files.readAllBytes(fichier);
        String texte = new String(contenu, StandardCharsets.UTF_8);
        return java.util.Arrays.asList(texte.split("\\r?\\n"));
    }
}
