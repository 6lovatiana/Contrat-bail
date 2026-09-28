-- =============================================================================
-- SCRIPT POSTGRESQL OPTIMISÉ POUR SPRING BOOT & THEMELEAF
-- =============================================================================

-- create database ContratBail;

CREATE TYPE statut_contrat AS ENUM ('EN_ATTENTE_SIGNATURE', 'EN_COURS', 'TERMINE', 'RESILIE');
CREATE TYPE statut_paiement AS ENUM ('A_PAYER', 'PAYE', 'EN_RETARD', 'PARTIEL');
CREATE TYPE type_compteur_jirama AS ENUM ('UNIQUE', 'PARTAGE', 'SOUS_COMPTEUR');

-- 1. UTILISATEUR
CREATE TABLE utilisateur (
    id_utilisateur SERIAL PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100),
    cin_numero VARCHAR(12) NOT NULL UNIQUE, -- 12 chiffres (Madagascar)
    cin_date_delivrance DATE NOT NULL,
    cin_lieu_delivrance VARCHAR(100) NOT NULL,
    profession VARCHAR(100),
    adresse_actuelle TEXT NOT NULL,
    telephone VARCHAR(20) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    mot_de_passe VARCHAR(255) NOT NULL
);

-- 2. LOGEMENT (Relié au BAILLEUR via id_proprietaire)
CREATE TABLE logement (
    id_logement SERIAL PRIMARY KEY,
    adresse_lot VARCHAR(50) NOT NULL,
    quartier_fokontany VARCHAR(100) NOT NULL,
    ville VARCHAR(100) NOT NULL,
    nombre_pieces_principales INT NOT NULL CHECK (nombre_pieces_principales > 0),
    description_consistance TEXT,
    
    compteur_eau_numero VARCHAR(50),
    compteur_eau_index_depart INT DEFAULT 0,
    compteur_electricite_numero VARCHAR(50),
    compteur_electricite_index_depart INT DEFAULT 0,
    jirama_type_gestion type_compteur_jirama NOT NULL DEFAULT 'UNIQUE',
    jirama_methode_repartition TEXT,
    
    id_proprietaire INT NOT NULL, -- Représente le BAILLEUR
    CONSTRAINT fk_logement_proprietaire FOREIGN KEY (id_proprietaire) 
        REFERENCES utilisateur(id_utilisateur) ON DELETE RESTRICT
);

-- 3. CONTRAT_DE_BAIL (Relié au LOCATAIRE et au LOGEMENT)
CREATE TABLE contrat_de_bail (
    id_contrat SERIAL PRIMARY KEY,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    montant_loyer_mga DECIMAL(12, 2) NOT NULL CHECK (montant_loyer_mga > 0),
    montant_caution_mga DECIMAL(12, 2) NOT NULL CHECK (montant_caution_mga >= 0),
    jour_paiement_mensuel INT NOT NULL CHECK (jour_paiement_mensuel BETWEEN 1 AND 31),
    duree_preavis_mois INT NOT NULL DEFAULT 3,
    statut_actuel statut_contrat NOT NULL DEFAULT 'EN_ATTENTE_SIGNATURE',
    
    id_logement INT NOT NULL, -- Clé étrangère vers le bien
    id_locataire INT NOT NULL, -- Clé étrangère vers l'UTILISATEUR qui loue
    
    CONSTRAINT fk_contrat_logement FOREIGN KEY (id_logement) 
        REFERENCES logement(id_logement) ON DELETE RESTRICT,
    CONSTRAINT fk_contrat_locataire FOREIGN KEY (id_locataire) 
        REFERENCES utilisateur(id_utilisateur) ON DELETE RESTRICT,
    CONSTRAINT chk_dates_coherentes CHECK (date_fin > date_debut)
);

-- CORRECTION LOGIQUE : Un seul contrat actif ('EN_COURS') à la fois par logement
CREATE UNIQUE INDEX uq_un_seul_contrat_actif_par_logement 
ON contrat_de_bail (id_logement) 
WHERE statut_actuel = 'EN_COURS';


-- 4. PAIEMENT_LOYER
CREATE TABLE paiement_loyer (
    id_paiement SERIAL PRIMARY KEY,
    id_contrat INT NOT NULL,
    periode_mois INT NOT NULL CHECK (periode_mois BETWEEN 1 AND 12),
    periode_annee INT NOT NULL CHECK (periode_annee >= 2026),
    montant_attendu DECIMAL(12, 2) NOT NULL,
    montant_paye DECIMAL(12, 2) DEFAULT 0.00,
    date_paiement_effectif DATE,
    mode_paiement VARCHAR(50),
    statut statut_paiement NOT NULL DEFAULT 'A_PAYER',
    recu_quittance_genere BOOLEAN DEFAULT FALSE,
    
    CONSTRAINT fk_paiement_contrat FOREIGN KEY (id_contrat) 
        REFERENCES contrat_de_bail(id_contrat) ON DELETE CASCADE,
    CONSTRAINT uq_paiement_periode UNIQUE (id_contrat, periode_mois, periode_annee)
);
