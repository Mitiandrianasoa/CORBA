-- Mise en place de la base utilisee par DataServiceImpl.cc (cote C++)
-- A executer une seule fois avec : sudo mysql -u root < setup-mysql.sql

CREATE DATABASE IF NOT EXISTS corba_demo;

CREATE USER IF NOT EXISTS 'corba_user'@'localhost' IDENTIFIED BY 'corba';
GRANT ALL PRIVILEGES ON corba_demo.* TO 'corba_user'@'localhost';
FLUSH PRIVILEGES;

USE corba_demo;

CREATE TABLE IF NOT EXISTS donnees (
  id INT PRIMARY KEY,
  valeur VARCHAR(255) NOT NULL
);

INSERT IGNORE INTO donnees (id, valeur) VALUES (1, 'test base!!!!');

SELECT * FROM donnees;
