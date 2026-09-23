#include "ServiceBaseImpl.hh"

#include <cppconn/driver.h>
#include <cppconn/connection.h>
#include <cppconn/statement.h>
#include <cppconn/prepared_statement.h>
#include <cppconn/resultset.h>
#include <memory>
#include <iostream>
#include <fstream>
#include <string>

static const char* DB_HOST = "tcp://127.0.0.1:3306";
static const char* DB_USER = "corba_user";
static const char* DB_PASS = "corba";
static const char* DB_NAME = "corba_demo";

static std::unique_ptr<sql::Connection> connecter() {
    sql::Driver* driver = get_driver_instance();
    std::unique_ptr<sql::Connection> con(driver->connect(DB_HOST, DB_USER, DB_PASS));
    con->setSchema(DB_NAME);
    return con;
}

// Remplit une struct IDL a partir de la ligne courante d'un ResultSet
static void remplir(modele::Produit& p, sql::ResultSet* rs) {
    p.id       = rs->getInt("id");
    p.nom      = rs->getString("nom").c_str();   // String_member : copie automatique
    p.quantite = rs->getInt("quantite");
    p.prix     = rs->getDouble("prix");
}

// Ajoute une ligne a la fin d'un fichier texte (cree le fichier s'il n'existe pas)
static void journaliser(const std::string& ligne) {
    std::ofstream f("data/journal_cpp.txt", std::ios::app);
    f << ligne << "\n";
}

ServiceBaseImpl::ServiceBaseImpl(CosNaming::NamingContextExt_ptr annuaire)
    : nc(CosNaming::NamingContextExt::_duplicate(annuaire)) {}

// ---------- INSERT + appel C++ -> Java avant d'ecrire ----------
CORBA::Long ServiceBaseImpl::ajouter(const modele::Produit& p) {
    // 1) Demander a Java si le nom est autorise (lu dans data/autorises.txt)
    CORBA::Boolean autorise = true;
    try {
        CORBA::Object_var obj = nc->resolve_str("ServiceFichier");
        modele::ServiceFichier_var fichier = modele::ServiceFichier::_narrow(obj);
        autorise = fichier->contient("data/autorises.txt", p.nom);
    } catch (const CosNaming::NamingContext::NotFound&) {
        std::cout << "[C++] ServiceFichier jamais enregistre, pas de verification" << std::endl;
    } catch (const CORBA::SystemException& e) {
        // TRANSIENT / COMM_FAILURE : le nom existe mais le programme Java est ferme
        std::cout << "[C++] ServiceFichier injoignable (" << e._name() << "), pas de verification" << std::endl;
    }
    if (!autorise)
        throw modele::Refuse("produit non autorise");   // HORS du try : sinon on l'attraperait nous-memes

    // 2) INSERT puis recuperer l'id genere
    try {
        auto con = connecter();
        std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
            "INSERT INTO produits (nom, quantite, prix) VALUES (?, ?, ?)"));
        ps->setString(1, (const char*)p.nom);
        ps->setInt(2, p.quantite);
        ps->setDouble(3, p.prix);
        ps->executeUpdate();

        std::unique_ptr<sql::Statement> st(con->createStatement());
        std::unique_ptr<sql::ResultSet> rs(st->executeQuery("SELECT LAST_INSERT_ID() AS id"));
        rs->next();
        CORBA::Long id = rs->getInt("id");

        journaliser("ajout " + std::string(p.nom) + " id=" + std::to_string(id));
        notifierTous(("ajout de " + std::string(p.nom)).c_str());
        return id;
    } catch (sql::SQLException& e) {
        std::cerr << "[C++] Erreur MySQL : " << e.what() << std::endl;
        throw modele::Refuse(e.what());
    }
}

// ---------- SELECT une ligne -> struct, ou exception ----------
modele::Produit* ServiceBaseImpl::trouver(CORBA::Long id) {
    auto con = connecter();
    std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
        "SELECT id, nom, quantite, prix FROM produits WHERE id = ?"));
    ps->setInt(1, id);
    std::unique_ptr<sql::ResultSet> rs(ps->executeQuery());

    if (!rs->next())
        throw modele::Introuvable(id);

    modele::Produit* p = new modele::Produit();   // "new" : c'est l'ORB qui liberera
    remplir(*p, rs.get());
    return p;
}

// ---------- SELECT plusieurs lignes -> sequence ----------
modele::ListeProduits* ServiceBaseImpl::lister() {
    modele::ListeProduits* liste = new modele::ListeProduits();
    auto con = connecter();
    std::unique_ptr<sql::Statement> st(con->createStatement());
    std::unique_ptr<sql::ResultSet> rs(st->executeQuery(
        "SELECT id, nom, quantite, prix FROM produits ORDER BY id"));

    while (rs->next()) {
        CORBA::ULong n = liste->length();
        liste->length(n + 1);          // agrandir de 1
        remplir((*liste)[n], rs.get());
    }
    return liste;
}

// ---------- SELECT avec LIKE ----------
modele::ListeProduits* ServiceBaseImpl::chercher(const char* motif) {
    modele::ListeProduits* liste = new modele::ListeProduits();
    auto con = connecter();
    std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
        "SELECT id, nom, quantite, prix FROM produits WHERE nom LIKE ? ORDER BY id"));
    ps->setString(1, std::string("%") + motif + "%");
    std::unique_ptr<sql::ResultSet> rs(ps->executeQuery());

    while (rs->next()) {
        CORBA::ULong n = liste->length();
        liste->length(n + 1);
        remplir((*liste)[n], rs.get());
    }
    return liste;
}

// ---------- UPDATE conditionnel (verifier ET modifier en 1 requete) ----------
void ServiceBaseImpl::modifierQuantite(CORBA::Long id, CORBA::Long delta) {
    auto con = connecter();
    std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
        "UPDATE produits SET quantite = quantite + ? WHERE id = ? AND quantite + ? >= 0"));
    ps->setInt(1, delta);
    ps->setInt(2, id);
    ps->setInt(3, delta);
    if (ps->executeUpdate() > 0) {
        notifierTous(("quantite modifiee pour id " + std::to_string(id)).c_str());
        return;
    }

    // 0 ligne modifiee : soit l'id n'existe pas, soit la quantite deviendrait negative
    std::unique_ptr<modele::Produit> p(trouver(id));   // leve Introuvable si absent
    throw modele::Refuse(("stock insuffisant : " + std::to_string(p->quantite)).c_str());
}

// ---------- DELETE ----------
CORBA::Boolean ServiceBaseImpl::supprimer(CORBA::Long id) {
    auto con = connecter();
    std::unique_ptr<sql::PreparedStatement> ps(con->prepareStatement(
        "DELETE FROM produits WHERE id = ?"));
    ps->setInt(1, id);
    return ps->executeUpdate() > 0;
}

// ---------- recevoir une sequence en parametre ----------
CORBA::Long ServiceBaseImpl::importer(const modele::ListeProduits& liste) {
    CORBA::Long ajoutes = 0;
    for (CORBA::ULong i = 0; i < liste.length(); i++) {
        try {
            ajouter(liste[i]);
            ajoutes++;
        } catch (const modele::Refuse& e) {
            std::cout << "[C++] Ignore " << liste[i].nom << " : " << e.raison << std::endl;
        }
    }
    return ajoutes;
}

// ---------- parametres out ----------
void ServiceBaseImpl::statistiques(CORBA::Long& nombre, CORBA::Double& valeurTotale) {
    auto con = connecter();
    std::unique_ptr<sql::Statement> st(con->createStatement());
    std::unique_ptr<sql::ResultSet> rs(st->executeQuery(
        "SELECT COUNT(*) AS n, COALESCE(SUM(quantite * prix), 0) AS total FROM produits"));
    rs->next();
    nombre       = rs->getInt("n");
    valeurTotale = rs->getDouble("total");
}

// ---------- callback : garder la reference du client ----------
void ServiceBaseImpl::sAbonner(modele::Notifiable_ptr abonne) {
    omni_mutex_lock lock(verrou);
    abonnes.push_back(modele::Notifiable::_duplicate(abonne));
}

void ServiceBaseImpl::notifierTous(const char* evenement) {
    omni_mutex_lock lock(verrou);
    for (auto it = abonnes.begin(); it != abonnes.end(); ) {
        try {
            (*it)->notifier(evenement);
            ++it;
        } catch (const CORBA::Exception&) {
            it = abonnes.erase(it);   // client ferme : on l'oublie
        }
    }
}
