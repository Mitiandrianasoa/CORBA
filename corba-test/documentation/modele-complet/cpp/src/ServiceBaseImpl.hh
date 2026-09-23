#ifndef SERVICEBASEIMPL_HH
#define SERVICEBASEIMPL_HH

#include "modele.hh"
#include <omnithread.h>
#include <vector>

class ServiceBaseImpl : public POA_modele::ServiceBase
{
    CosNaming::NamingContextExt_var nc;            // pour retrouver les services Java
    std::vector<modele::Notifiable_var> abonnes;   // callbacks
    omni_mutex verrou;                             // protege "abonnes"

    void notifierTous(const char* evenement);

public:
    ServiceBaseImpl(CosNaming::NamingContextExt_ptr annuaire);

    CORBA::Long            ajouter(const modele::Produit& p);
    modele::Produit*       trouver(CORBA::Long id);
    modele::ListeProduits* lister();
    modele::ListeProduits* chercher(const char* motif);
    void                   modifierQuantite(CORBA::Long id, CORBA::Long delta);
    CORBA::Boolean         supprimer(CORBA::Long id);
    CORBA::Long            importer(const modele::ListeProduits& liste);
    void                   statistiques(CORBA::Long& nombre, CORBA::Double& valeurTotale);
    void                   sAbonner(modele::Notifiable_ptr abonne);
};

#endif
