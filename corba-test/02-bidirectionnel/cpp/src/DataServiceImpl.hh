#ifndef DATASERVICEIMPL_HH
#define DATASERVICEIMPL_HH

#include "app.hh"

class DataServiceImpl : public POA_app::DataService
{
public:
    char *getData();
    void saveData(const char *value);
};

#endif
