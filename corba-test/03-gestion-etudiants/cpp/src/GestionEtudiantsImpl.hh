#ifndef GESTIONETUDIANTSIMPL_HH
#define GESTIONETUDIANTSIMPL_HH

#include "ecole.hh"

class GestionEtudiantsImpl : public POA_ecole::GestionEtudiants
{
    // References vers les services Java enregistres. _var gere automatiquement la memoire.
    ecole::MentionService_var mentionService_;
    ecole::FicheService_var ficheService_;
    ecole::ValidationService_var validationService_;
    ecole::PenaliteService_var penaliteService_;

public:
    CORBA::Long ajouterEtudiant(const char* nom, const char* prenom, const char* classe);
    CORBA::Boolean modifierEtudiant(CORBA::Long id, const char* nom, const char* prenom, const char* classe);
    CORBA::Boolean supprimerEtudiant(CORBA::Long id);
    ecole::ListeEtudiants* listerEtudiants();
    ecole::Etudiant* rechercherEtudiant(CORBA::Long id);
    void ajouterNote(CORBA::Long etudiantId, const char* matiere, CORBA::Double note);
    CORBA::Double calculerMoyenne(CORBA::Long etudiantId);

    void enregistrerMentionService(ecole::MentionService_ptr svc);
    char* obtenirMention(CORBA::Long etudiantId);

    void enregistrerFicheService(ecole::FicheService_ptr svc);
    void genererFiche(CORBA::Long etudiantId);

    void enregistrerValidationService(ecole::ValidationService_ptr svc);
    CORBA::Boolean changerClasse(CORBA::Long etudiantId, const char* nouvelleClasse);

    void enregistrerPenaliteService(ecole::PenaliteService_ptr svc);
    CORBA::Boolean appliquerPenalite(CORBA::Long etudiantId, const char* matiere);
};

#endif
