#include "modele.hh"
#include "ServiceBaseImpl.hh"
#include <iostream>

// Attendre qu'un nom apparaisse dans l'annuaire (l'autre programme n'est pas encore lance)
static CORBA::Object_ptr attendre(CosNaming::NamingContextExt_ptr nc, const char* nom) {
    for (int essai = 1; essai <= 30; essai++) {
        try {
            return nc->resolve_str(nom);
        } catch (const CosNaming::NamingContext::NotFound&) {
            std::cout << "[C++] " << nom << " pas encore la, essai " << essai << std::endl;
            omni_thread::sleep(1);
        }
    }
    throw CORBA::TRANSIENT();
}

int main(int argc, char** argv)
{
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);

    CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");
    PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
    poa->the_POAManager()->activate();

    CORBA::Object_var nsObj = orb->resolve_initial_references("NameService");
    CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);

    // --- SERVEUR : publier ServiceBase ---
    ServiceBaseImpl* impl = new ServiceBaseImpl(nc);
    PortableServer::ObjectId_var id = poa->activate_object(impl);
    modele::ServiceBase_var ref = impl->_this();
    CosNaming::Name_var nom = nc->to_name("ServiceBase");
    nc->rebind(nom, ref);
    std::cout << "[C++] ServiceBase enregistre" << std::endl;

    // --- CLIENT (optionnel) : appeler Java une fois au demarrage ---
    if (argc > 1 && std::string(argv[1]) == "--attendre-java") {
        try {
            CORBA::Object_var obj = attendre(nc, "ServiceFichier");
            modele::ServiceFichier_var fichier = modele::ServiceFichier::_narrow(obj);

            modele::ListeTextes_var lignes = fichier->lireLignes("data/autorises.txt");
            for (CORBA::ULong i = 0; i < lignes->length(); i++)
                std::cout << "[C++] autorise : " << lignes[i] << std::endl;

            CORBA::Long v = fichier->lireValeur("data/config.txt", "bonbon");
            std::cout << "[C++] config bonbon = " << v << std::endl;
            fichier->lireValeur("data/config.txt", "absent");   // leve Refuse
        } catch (const modele::Refuse& e) {
            std::cout << "[C++] Refuse recu de Java : " << e.raison << std::endl;
        } catch (const CORBA::Exception& e) {
            // TRANSIENT / COMM_FAILURE : Java ferme (nom perime dans l'annuaire)
            std::cout << "[C++] Java injoignable : " << e._name() << std::endl;
        }
    }

    orb->run();
    return 0;
}
