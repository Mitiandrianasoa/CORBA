#include "app.hh"
#include "DataServiceImpl.hh"
#include <omniORB4/CORBA.h>
#include <iostream>
#include <unistd.h>

int main(int argc, char **argv)
{
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);

    CORBA::Object_var poaObj = orb->resolve_initial_references("RootPOA");
    PortableServer::POA_var poa = PortableServer::POA::_narrow(poaObj);
    PortableServer::POAManager_var pman = poa->the_POAManager();
    pman->activate();

    // --- PARTIE SERVEUR : j'enregistre mon DataService ---
    DataServiceImpl *dataImpl = new DataServiceImpl();
    PortableServer::ObjectId_var id = poa->activate_object(dataImpl);
    app::DataService_var dataRef = dataImpl->_this();

    CORBA::Object_var nsObj = orb->resolve_initial_references("NameService");
    CosNaming::NamingContextExt_var nc = CosNaming::NamingContextExt::_narrow(nsObj);
    CosNaming::Name_var dataName = nc->to_name("DataService");
    nc->rebind(dataName, dataRef);
    std::cout << "[C++] DataService enregistre, en attente..." << std::endl;

    // --- PARTIE CLIENTE : j'appelle FileService (cote Java) ---
    sleep(20);
    try
    {
        CORBA::Object_var obj = nc->resolve_str("FileService");
        app::FileService_var file = app::FileService::_narrow(obj);
        CORBA::String_var msg = file->readMessage();
        std::cout << "[C++] Reponse de FileService (Java) : " << msg << std::endl;
        file->writeMessage(msg);
    }
    catch (const CORBA::Exception &e)
    {
        std::cout << "[C++] FileService pas encore disponible" << std::endl;
    }

    orb->run();
    return 0;
}
