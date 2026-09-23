#include "boutique.hh"
#include "StockImpl.hh"
#include <iostream>

int main(int argc, char **argv)
{
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv); // 0. ORB

    CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA"); // S1. POA
    PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
    poa->the_POAManager()->activate();

    CORBA::Object_var nsObj = orb->resolve_initial_references("NameService"); // 0. annuaire
    CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);

    StockImpl *impl = new StockImpl(nc); // S2. servant
    PortableServer::ObjectId_var id = poa->activate_object(impl);
    boutique::Stock_var ref = impl->_this(); // S3. reference
    nc->rebind(nc->to_name("Stock"), ref);   // S4. publier
    std::cout << "[C++] Stock enregistre" << std::endl;

    orb->run(); // S5. ecouter
    return 0;
}