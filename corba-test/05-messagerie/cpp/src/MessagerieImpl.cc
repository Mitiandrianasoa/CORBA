#include "MessagerieImpl.hh"

#include <cppconn/driver.h>
#include <cppconn/connection.h>
#include <cppconn/prepared_statement.h>
#include <memory>
#include <iostream>
#include <cstring>

static const char* DB_HOST = "tcp://127.0.0.1:3306";
static const char* DB_USER = "corba_user";
static const char* DB_PASS = "corba";
static const char* DB_NAME = "corba_demo";

MessagerieImpl::MessagerieImpl(CosNaming::NamingContextExt_ptr annuaire)
    : nc(CosNaming::NamingContextExt::_duplicate(annuaire)) {}

void MessagerieImpl::sAbonner(chat::Notifiable_ptr abonne) {
    // "abonne" appartient a l'appelant (parametre in) : _duplicate() pour
    // garder notre propre copie de la reference apres la fin de l'appel.
    omni_mutex_lock lock(verrou);
    abonnes.push_back(chat::Notifiable::_duplicate(abonne));
    std::cout << "[C++] Nouvel abonne (" << abonnes.size() << " au total)" << std::endl;
}

void MessagerieImpl::envoyer(const char* auteur, const char* contenu) {
    std::cout << "[C++] Message recu de " << auteur << " : " << contenu << std::endl;

    // 0) Demander a Java si le message contient un mot tabou (appel C++ -> Java)
    CORBA::String_var motTabou;
    try {
        CORBA::Object_var obj = nc->resolve_str("Moderateur");
        chat::Moderateur_var moderateur = chat::Moderateur::_narrow(obj);
        motTabou = moderateur->chercherMotTabou(contenu);
    } catch (const CORBA::Exception& e) {
        // Aucun client Java n'a inscrit de Moderateur (ou il a ete ferme)
        std::cout << "[C++] Moderateur indisponible, message accepte sans verification" << std::endl;
        motTabou = CORBA::string_dup("");
    }

    if (strlen(motTabou) > 0) {
        std::cout << "[C++] Message refuse (mot tabou : " << motTabou << ")" << std::endl;
        // L'exception IDL traverse le reseau : Java la recoit dans son catch
        throw chat::MessageInterdit(motTabou);
    }

    // 1) Enregistrer le message en MySQL
    try {
        sql::Driver* driver = get_driver_instance();
        std::unique_ptr<sql::Connection> con(driver->connect(DB_HOST, DB_USER, DB_PASS));
        con->setSchema(DB_NAME);

        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "INSERT INTO messages (auteur, contenu) VALUES (?, ?)"));
        pstmt->setString(1, auteur);
        pstmt->setString(2, contenu);
        pstmt->executeUpdate();
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL : " << e.what() << std::endl;
    }

    // 2) CALLBACK : c'est maintenant le serveur qui appelle chaque client
    omni_mutex_lock lock(verrou);
    for (auto it = abonnes.begin(); it != abonnes.end(); ) {
        try {
            (*it)->nouveauMessage(auteur, contenu);
            ++it;
        } catch (const CORBA::Exception& e) {
            // Le client Java a ete ferme : sa reference ne repond plus,
            // on l'oublie pour ne pas reessayer a chaque message.
            std::cout << "[C++] Abonne injoignable, retire de la liste" << std::endl;
            it = abonnes.erase(it);
        }
    }
}
