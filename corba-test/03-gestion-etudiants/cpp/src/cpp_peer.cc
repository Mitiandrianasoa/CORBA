#include "ecole.hh"
#include "GestionEtudiantsImpl.hh"
#include <omniORB4/CORBA.h>
#include <iostream>

int main(int argc, char** argv) {
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);

    CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");
    PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
    poa->the_POAManager()->activate();

    GestionEtudiantsImpl* impl = new GestionEtudiantsImpl();
    poa->activate_object(impl);
    ecole::GestionEtudiants_var ref = impl->_this();

    CORBA::Object_var nsObj = orb->resolve_initial_references("NameService");
    CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);
    CosNaming::Name_var name = nc->to_name("GestionEtudiants");
    nc->rebind(name, ref);

    std::cout << "[C++] GestionEtudiants enregistre, en attente..." << std::endl;
    orb->run();
    return 0;
}
