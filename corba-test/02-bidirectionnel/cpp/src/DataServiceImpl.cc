#include "DataServiceImpl.hh"

#include <cppconn/driver.h>
#include <cppconn/connection.h>
#include <cppconn/statement.h>
#include <cppconn/prepared_statement.h>
#include <cppconn/resultset.h>
#include <memory>
#include <iostream>

static const char* DB_HOST = "tcp://127.0.0.1:3306";
static const char* DB_USER = "corba_user";
static const char* DB_PASS = "corba";
static const char* DB_NAME = "corba_demo";

char* DataServiceImpl::getData() {
    try {
        sql::Driver* driver = get_driver_instance();
        std::unique_ptr<sql::Connection> con(driver->connect(DB_HOST, DB_USER, DB_PASS));
        con->setSchema(DB_NAME);

        std::unique_ptr<sql::Statement> stmt(con->createStatement());
        std::unique_ptr<sql::ResultSet> res(stmt->executeQuery(
            "SELECT valeur FROM donnees WHERE id = 1"));

        std::string valeur = "(aucune ligne en base)";
        if (res->next()) {
            valeur = res->getString("valeur");
        }
        return CORBA::string_dup(valeur.c_str());
    } catch (sql::SQLException& e) {
        std::string msg = std::string("Erreur MySQL : ") + e.what();
        std::cerr << msg << std::endl;
        return CORBA::string_dup(msg.c_str());
    }
}

void DataServiceImpl::saveData(const char* value) {
    try {
        sql::Driver* driver = get_driver_instance();
        std::unique_ptr<sql::Connection> con(driver->connect(DB_HOST, DB_USER, DB_PASS));
        con->setSchema(DB_NAME);

        std::unique_ptr<sql::PreparedStatement> pstmt(con->prepareStatement(
            "UPDATE donnees SET valeur = ? WHERE id = 1"));
        pstmt->setString(1, value);
        pstmt->executeUpdate();
    } catch (sql::SQLException& e) {
        std::cerr << "Erreur MySQL : " << e.what() << std::endl;
    }
}
