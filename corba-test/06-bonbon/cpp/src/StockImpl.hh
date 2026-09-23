#include "boutique.hh"

class StockImpl : public POA_boutique::Stock
{                                       // je SERS Stock
    CosNaming::NamingContextExt_var nc; // pour retrouver Commande (Java)
public:
    StockImpl(CosNaming::NamingContextExt_ptr annuaire)
        : nc(CosNaming::NamingContextExt::_duplicate(annuaire)) {}
    CORBA::Long acheter(const char *produit);
};