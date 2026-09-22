-- Mise en place de la base utilisee par GestionEtudiantsImpl.cc (cote C++)
-- A executer une seule fois avec : sudo mysql -u root < setup-mysql.sql

CREATE DATABASE IF NOT EXISTS ecole_demo;

CREATE USER IF NOT EXISTS 'ecole_user'@'localhost' IDENTIFIED BY 'ecole';
GRANT ALL PRIVILEGES ON ecole_demo.* TO 'ecole_user'@'localhost';
FLUSH PRIVILEGES;

USE ecole_demo;

CREATE TABLE IF NOT EXISTS etudiants (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nom VARCHAR(100) NOT NULL,
  prenom VARCHAR(100) NOT NULL,
  classe VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS notes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  etudiant_id INT NOT NULL,
  matiere VARCHAR(100) NOT NULL,
  note DOUBLE NOT NULL,
  FOREIGN KEY (etudiant_id) REFERENCES etudiants(id) ON DELETE CASCADE
);

SELECT 'Base ecole_demo prete.' AS statut;
