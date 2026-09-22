#include "app.hh"
#include "MessagerieImpl.hh"
#include <omniORB4/CORBA.h>
#include <iostream>

int main(int argc, char **argv)
{
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);

    CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");
    PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
    PortableServer::POAManager_var pman = poa->the_POAManager();
    pman->activate();

    // --- PARTIE SERVEUR : j'enregistre ma Messagerie ---
    MessagerieImpl *messImpl = new MessagerieImpl();
    PortableServer::ObjectId_var id = poa->activate_object(messImpl);
    chat::Messagerie_var messRef = messImpl->_this();

    CORBA::Object_var nsObj = orb->resolve_initial_references("NameService");
    CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);
    CosNaming::Name_var messName = nc->to_name("Messagerie");
    nc->rebind(messName, messRef);
    std::cout << "[C++] Messagerie enregistree, en attente..." << std::endl;

    // --- PARTIE CLIENTE : pas de sleep ni de resolve_str ici ! ---
    // Contrairement a 02-bidirectionnel, le C++ ne cherche pas le client
    // dans l'annuaire : c'est le client qui lui donne sa reference via
    // sAbonner(), et MessagerieImpl::envoyer() le rappelle.

    orb->run();
    return 0;
}
