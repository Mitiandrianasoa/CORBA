-- Table utilisee par MessagerieImpl.cc (cote C++)
-- A executer une seule fois avec : mysql -u corba_user -pcorba < setup-mysql.sql
-- (la base corba_demo et l'utilisateur corba_user ont deja ete crees par 02-bidirectionnel)

USE corba_demo;

CREATE TABLE IF NOT EXISTS messages (
  id INT AUTO_INCREMENT PRIMARY KEY,
  auteur VARCHAR(100) NOT NULL,
  contenu VARCHAR(500) NOT NULL,
  date_envoi TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

SELECT * FROM messages;
