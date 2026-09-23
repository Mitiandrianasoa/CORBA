#include "StockImpl.hh"
#include <cppconn/driver.h>
#include <cppconn/connection.h>
#include <cppconn/prepared_statement.h>
#include <cppconn/resultset.h>
#include <memory>

CORBA::Long StockImpl::acheter(const char *produit)
{
    // --- PARTIE CLIENTE : j'appelle Java pour connaitre la quantite ---
    CORBA::Object_var obj = nc->resolve_str("Commande");
    boutique::Commande_var commande = boutique::Commande::_narrow(obj);
    CORBA::Long qte = commande->quantiteDemandee(produit);

    // --- Travail local : MySQL ---
    sql::Driver *driver = get_driver_instance();
    std::unique_ptr<sql::Connection> con(driver->connect("tcp://127.0.0.1:3306", "corba_user", "corba"));
    con->setSchema("corba_demo");

    // Verifier ET decrementer en une seule requete : si le stock est insuffisant, 0 ligne modifiee
    std::unique_ptr<sql::PreparedStatement> maj(con->prepareStatement(
        "UPDATE stock SET quantite = quantite - ? WHERE produit = ? AND quantite >= ?"));
    maj->setInt(1, qte);
    maj->setString(2, produit);
    maj->setInt(3, qte);
    int modifiees = maj->executeUpdate();

    // Lire le stock actuel (pour le renvoyer ou le mettre dans l'exception)
    std::unique_ptr<sql::PreparedStatement> sel(con->prepareStatement(
        "SELECT quantite FROM stock WHERE produit = ?"));
    sel->setString(1, produit);
    std::unique_ptr<sql::ResultSet> rs(sel->executeQuery());
    CORBA::Long restant = rs->next() ? rs->getInt(1) : 0;

    if (modifiees == 0)
        throw boutique::StockInsuffisant(restant); // traverse le reseau jusqu'au catch Java
    return restant;
}