#include "GestionEtudiantsImpl.hh"

#include <cppconn/driver.h>
#include <cppconn/connection.h>
#include <cppconn/statement.h>
#include <cppconn/prepared_statement.h>
#include <cppconn/resultset.h>
#include <memory>
#include <iostream>

static const char* DB_HOST = "tcp://127.0.0.1:3306";
static const char* DB_USER = "ecole_user";
static const char* DB_PASS = "ecole";
static const char* DB_NAME = "ecole_demo";

static std::unique_ptr<sql::Connection> connecter() {
    sql::Driver* driver = get_driver_instance();
    std::unique_ptr<sql::Connection> con(driver->connect(DB_HOST, DB_USER, DB_PASS));
    con->setSchema(DB_NAME);
    return con;
}

// --- CRUD etudiants ---

CORBA::Long GestionEtudiantsImpl::ajouterEtudiant(const char* nom, const char* prenom, const char* classe) {
    try {
        auto con = connecter();
        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "INSERT INTO etudiants (nom, prenom, classe) VALUES (?, ?, ?)"));
        pstmt->setString(1, nom);
        pstmt->setString(2, prenom);
        pstmt->setString(3, classe);
        pstmt->executeUpdate();

        std::unique_ptr<sql::Statement> stmt(con->createStatement());
        std::unique_ptr<sql::ResultSet> res(stmt->executeQuery("SELECT LAST_INSERT_ID() AS id"));
        res->next();
        CORBA::Long id = res->getInt64("id");
        std::cout << "[C++] Etudiant ajoute : #" << id << " " << nom << " " << prenom << std::endl;
        return id;
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL (ajouterEtudiant) : " << e.what() << std::endl;
        return -1;
    }
}

CORBA::Boolean GestionEtudiantsImpl::modifierEtudiant(CORBA::Long id, const char* nom, const char* prenom, const char* classe) {
    try {
        auto con = connecter();
        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "UPDATE etudiants SET nom=?, prenom=?, classe=? WHERE id=?"));
        pstmt->setString(1, nom);
        pstmt->setString(2, prenom);
        pstmt->setString(3, classe);
        pstmt->setInt64(4, id);
        int lignes = pstmt->executeUpdate();
        return lignes > 0;
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL (modifierEtudiant) : " << e.what() << std::endl;
        return false;
    }
}

CORBA::Boolean GestionEtudiantsImpl::supprimerEtudiant(CORBA::Long id) {
    try {
        auto con = connecter();
        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "DELETE FROM etudiants WHERE id=?"));
        pstmt->setInt64(1, id);
        int lignes = pstmt->executeUpdate();
        return lignes > 0;
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL (supprimerEtudiant) : " << e.what() << std::endl;
        return false;
    }
}

ecole::ListeEtudiants* GestionEtudiantsImpl::listerEtudiants() {
    ecole::ListeEtudiants* liste = new ecole::ListeEtudiants();
    try {
        auto con = connecter();
        std::unique_ptr<sql::Statement> stmt(con->createStatement());
        std::unique_ptr<sql::ResultSet> res(stmt->executeQuery(
            "SELECT id, nom, prenom, classe FROM etudiants ORDER BY id"));

        while (res->next()) {
            CORBA::ULong n = liste->length();
            liste->length(n + 1);
            (*liste)[n].id = res->getInt64("id");
            (*liste)[n].nom = res->getString("nom").c_str();
            (*liste)[n].prenom = res->getString("prenom").c_str();
            (*liste)[n].classe = res->getString("classe").c_str();
        }
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL (listerEtudiants) : " << e.what() << std::endl;
    }
    return liste;
}

ecole::Etudiant* GestionEtudiantsImpl::rechercherEtudiant(CORBA::Long id) {
    try {
        auto con = connecter();
        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "SELECT id, nom, prenom, classe FROM etudiants WHERE id=?"));
        pstmt->setInt64(1, id);
        std::unique_ptr<sql::ResultSet> res(pstmt->executeQuery());

        if (!res->next()) {
            ecole::EtudiantIntrouvable ex;
            ex.id = id;
            throw ex;
        }

        ecole::Etudiant* e = new ecole::Etudiant();
        e->id = res->getInt64("id");
        e->nom = res->getString("nom").c_str();
        e->prenom = res->getString("prenom").c_str();
        e->classe = res->getString("classe").c_str();
        return e;
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL (rechercherEtudiant) : " << e.what() << std::endl;
        ecole::EtudiantIntrouvable ex;
        ex.id = id;
        throw ex;
    }
}

// --- Notes ---

void GestionEtudiantsImpl::ajouterNote(CORBA::Long etudiantId, const char* matiere, CORBA::Double note) {
    try {
        auto con = connecter();
        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "INSERT INTO notes (etudiant_id, matiere, note) VALUES (?, ?, ?)"));
        pstmt->setInt64(1, etudiantId);
        pstmt->setString(2, matiere);
        pstmt->setDouble(3, note);
        pstmt->executeUpdate();
        std::cout << "[C++] Note ajoutee : etudiant #" << etudiantId << " " << matiere << " = " << note << std::endl;
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL (ajouterNote) : " << e.what() << std::endl;
    }
}

// --- Calcul ---

// --- Bidirectionnel : Java s'enregistre, puis C++ le rappelle pour la mention ---

void GestionEtudiantsImpl::enregistrerMentionService(ecole::MentionService_ptr svc) {
    // Le parametre "svc" n'est prete que le temps de cet appel : on le duplique
    // pour garder une reference valide (le _var liberera l'ancienne s'il y en avait une).
    mentionService_ = ecole::MentionService::_duplicate(svc);
    std::cout << "[C++] MentionService (Java) enregistre." << std::endl;
}

char* GestionEtudiantsImpl::obtenirMention(CORBA::Long etudiantId) {
    // Reutilise calculerMoyenne : leve deja EtudiantIntrouvable si besoin.
    CORBA::Double moyenne = calculerMoyenne(etudiantId);

    if (CORBA::is_nil(mentionService_)) {
        return CORBA::string_dup("(MentionService non enregistre)");
    }

    std::cout << "[C++] Appel de Java pour la mention (moyenne=" << moyenne << ")" << std::endl;
    CORBA::String_var mention = mentionService_->calculerMention(moyenne);
    return CORBA::string_dup(mention.in());
}

CORBA::Double GestionEtudiantsImpl::calculerMoyenne(CORBA::Long etudiantId) {
    try {
        auto con = connecter();

        std::unique_ptr<sql::PreparedStatement> verif(con->prepareStatement(
            "SELECT id FROM etudiants WHERE id=?"));
        verif->setInt64(1, etudiantId);
        std::unique_ptr<sql::ResultSet> resVerif(verif->executeQuery());
        if (!resVerif->next()) {
            ecole::EtudiantIntrouvable ex;
            ex.id = etudiantId;
            throw ex;
        }

        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "SELECT AVG(note) AS moyenne FROM notes WHERE etudiant_id=?"));
        pstmt->setInt64(1, etudiantId);
        std::unique_ptr<sql::ResultSet> res(pstmt->executeQuery());

        if (res->next() && !res->isNull("moyenne")) {
            return res->getDouble("moyenne");
        }
        return 0.0; // pas encore de notes
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL (calculerMoyenne) : " << e.what() << std::endl;
        ecole::EtudiantIntrouvable ex;
        ex.id = etudiantId;
        throw ex;
    }
}

// --- Bidirectionnel : C++ recherche l'etudiant (rechercherEtudiant), puis demande a Java d'ecrire sa fiche ---

void GestionEtudiantsImpl::enregistrerFicheService(ecole::FicheService_ptr svc) {
    ficheService_ = ecole::FicheService::_duplicate(svc);
    std::cout << "[C++] FicheService (Java) enregistre." << std::endl;
}

void GestionEtudiantsImpl::genererFiche(CORBA::Long etudiantId) {
    // Reutilise rechercherEtudiant (enfin appelee !) : leve EtudiantIntrouvable si besoin.
    ecole::Etudiant_var e = rechercherEtudiant(etudiantId);
    CORBA::Double moyenne = calculerMoyenne(etudiantId);

    if (CORBA::is_nil(ficheService_)) {
        std::cout << "[C++] FicheService non enregistre, fiche non generee." << std::endl;
        return;
    }

    CORBA::String_var mention;
    if (!CORBA::is_nil(mentionService_)) {
        mention = mentionService_->calculerMention(moyenne);
    } else {
        mention = CORBA::string_dup("(mention indisponible)");
    }

    std::cout << "[C++] Appel de Java pour ecrire la fiche de " << e->nom.in() << " " << e->prenom.in() << std::endl;
    ficheService_->ecrireFiche(e.in(), moyenne, mention.in());
}

// --- Bidirectionnel : C++ demande a Java si la classe est valide, puis modifie (modifierEtudiant) ---

void GestionEtudiantsImpl::enregistrerValidationService(ecole::ValidationService_ptr svc) {
    validationService_ = ecole::ValidationService::_duplicate(svc);
    std::cout << "[C++] ValidationService (Java) enregistre." << std::endl;
}

CORBA::Boolean GestionEtudiantsImpl::changerClasse(CORBA::Long etudiantId, const char* nouvelleClasse) {
    // Reutilise rechercherEtudiant : leve EtudiantIntrouvable si l'etudiant n'existe pas.
    ecole::Etudiant_var e = rechercherEtudiant(etudiantId);

    if (CORBA::is_nil(validationService_)) {
        std::cout << "[C++] ValidationService non enregistre, changement refuse." << std::endl;
        return false;
    }

    std::cout << "[C++] Appel de Java pour valider la classe \"" << nouvelleClasse << "\"..." << std::endl;
    CORBA::Boolean valide = validationService_->classeValide(nouvelleClasse);

    if (!valide) {
        std::cout << "[C++] Classe \"" << nouvelleClasse << "\" refusee par Java (non presente dans classes_valides.txt)." << std::endl;
        return false;
    }

    // Enfin appelee : modifierEtudiant, avec le nom/prenom inchanges et la nouvelle classe.
    return modifierEtudiant(etudiantId, e->nom.in(), e->prenom.in(), nouvelleClasse);
}

// --- Bidirectionnel : C++ demande a Java la penalite (lue dans un fichier), puis l'applique en MySQL ---

void GestionEtudiantsImpl::enregistrerPenaliteService(ecole::PenaliteService_ptr svc) {
    penaliteService_ = ecole::PenaliteService::_duplicate(svc);
    std::cout << "[C++] PenaliteService (Java) enregistre." << std::endl;
}

CORBA::Boolean GestionEtudiantsImpl::appliquerPenalite(CORBA::Long etudiantId, const char* matiere) {
    // Verifie que l'etudiant existe (leve EtudiantIntrouvable sinon).
    ecole::Etudiant_var e = rechercherEtudiant(etudiantId);

    if (CORBA::is_nil(penaliteService_)) {
        std::cout << "[C++] PenaliteService non enregistre, penalite non appliquee." << std::endl;
        return false;
    }

    std::cout << "[C++] Appel de Java pour la penalite de \"" << matiere << "\"..." << std::endl;
    CORBA::Double penalite = penaliteService_->obtenirPenalite(matiere);

    if (penalite <= 0.0) {
        std::cout << "[C++] Aucune penalite definie pour \"" << matiere << "\"." << std::endl;
        return false;
    }

    try {
        auto con = connecter();
        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "UPDATE notes SET note = GREATEST(note - ?, 0) WHERE etudiant_id = ? AND matiere = ?"));
        pstmt->setDouble(1, penalite);
        pstmt->setInt64(2, etudiantId);
        pstmt->setString(3, matiere);
        int lignes = pstmt->executeUpdate();

        if (lignes > 0) {
            std::cout << "[C++] Penalite de " << penalite << " point(s) appliquee sur " << matiere
                       << " pour " << e->nom.in() << " " << e->prenom.in() << std::endl;
            return true;
        }
        std::cout << "[C++] " << e->nom.in() << " " << e->prenom.in()
                   << " n'a pas de note en \"" << matiere << "\", rien a penaliser." << std::endl;
        return false;
    } catch (sql::SQLException& ex) {
        std::cerr << "Erreur MySQL (appliquerPenalite) : " << ex.what() << std::endl;
        return false;
    }
}

