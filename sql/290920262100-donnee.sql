-- =============================================================================
-- BailTech — Jeu de donnees de test
-- =============================================================================
-- Fichier   : sql/290920262100-donnee.sql
-- Schema    : sql/240920260828-database_init.sql (a executer avant)
-- Objet     : alimenter une base de developpement pourNaviguer l'application
--             sans avoir a saisir de donnees a la main.
--
-- -----------------------------------------------------------------------------
-- MODE D'EMPLOI
-- -----------------------------------------------------------------------------
--   psql -U postgres -d contratbail -f sql/290920262100-donnee.sql
--
-- Le script est REPLAYABLE : il se termine par un TRUNCATE, ce qui permet de
-- le relancer autant de fois que necessaire sans accumuler de doublons ni
-- subir les contraintes d'unicite (CIN, email, un contrat actif par logement).
--
-- -----------------------------------------------------------------------------
-- IDENTIFIANTS DE CONNEXION
-- -----------------------------------------------------------------------------
-- L'authentification est par CIN + mot de passe. Les quatre comptes de
-- demonstration partagent le mot de passe :
--
--     Bailtech2026!
--
-- Le mot de passe n'est PAS stocke en clair : chaque ligne ne contient qu'une
-- empreinte BCrypt de cout 12, avec son sel propre. Les trois empreintes sont
-- differentes pour un mot de passe identique — c'est le fonctionnement normal
-- de BCrypt, et non une incoherence du jeu de donnees.
--
--   BAILLEURS (ont au moins un logement -> role ROLE_BAILLEUR)
--     101234567890  RAZAFINDRAKOTO Jean-Luc
--     199000112233  RAKOTOARISOA Naina
--     188000445566  ANDRIAMAMPIONONA Tojo
--
--   LOCATAIRES (role ROLE_LOCATAIRE)
--     201211334455  SAHONDRA Rakotomalala
--     201054776688  ANDRY Tiana Rakoto
--     115842009977  LOVA Heriniaina
--
-- -----------------------------------------------------------------------------
-- CE QUE CE JEU DE DONNEES EXERCE
-- -----------------------------------------------------------------------------
-- Il est concu pour rendre chaque ecran de BailTech non vide et parlant :
--
--   * Tableau de bord      3 bailleurs, montants varies, retards de paiement
--   * Visionneuse          6 contrats couvrant les 4 statuts possibles
--   * Assistant contrat    1 logement volontairement libre (villa B 15)
--   * Coffre-fort          3 dossiers, un bail signe, deux a regulariser
--   * Calculateur JIRAMA   les 3 modes de compteur : UNIQUE, PARTAGE,
--                          SOUS_COMPTEUR — avec des index de depart
--                          differents pour rendre la repartition verifiable
--   * Echeanciers          echeances payees, partielles et en retard
--
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- -----------------------------------------------------------------------------
-- Purge
-- -----------------------------------------------------------------------------
-- TRUNCATE ... CASCADE emporte les enfants (paiement_loyer) et remet les
-- sequence a zero, ce qui rend les identifiants previsibles d'une execution a
-- l'autre. RESTART IDENTITY est explicite pour que le script soit reproductible
-- sur une base ou le TRUNCATE n'a pas ete precede d'un DROP.
TRUNCATE TABLE paiement_loyer, contrat_de_bail, logement, utilisateur
    RESTART IDENTITY CASCADE;

-- -----------------------------------------------------------------------------
-- 1. UTILISATEURS
-- -----------------------------------------------------------------------------
-- Les trois empreintes BCrypt ci-dessous correspondent toutes au mot de passe
-- « Bailtech2026! ». Elles sont distinctes : BCrypt sale chaque empreinte
-- independamment, deux comptes ayant le meme mot de passe n'ont pas la meme
-- empreinte. C'est attendu, et c'est ce qui rend le hachage resistant aux
-- tables de pre-calcul.
--
-- Les CIN sont des series de 12 chiffres, format impose par la contrainte
-- @CinNational de l'entite Utilisateur : un CIN non conforme ferait echouer
-- l'ecran de saisie avant meme la validation SQL.

INSERT INTO utilisateur
    (nom, prenom, cin_numero, cin_date_delivrance, cin_lieu_delivrance, profession,
     adresse_actuelle, telephone, email, mot_de_passe)
VALUES
    -- BAILLEUR 1 : parc le plus fourni, sert de compte de démonstration principal
    ('RAZAFINDRAKOTO', 'Jean-Luc', '101234567890', DATE '2014-03-12', 'Antananarivo',
     'Ingénieur informaticien', 'Lot IVX 45 bis, Ivandry, Antananarivo',
     '+261 34 12 345 67', 'razafindrakoto@bailtech.mg',
     '$2a$12$GbfMfnxaNNQ4K5NZj1w/ZOHHEIyhqxy6fQLbthPFDCfY/nhoFdvUK'),

    -- BAILLEUR 2 : un seul bien en cours — sert a verifier l'isolation entre
    -- parc lors du test des controles d'acces (un bailleur ne doit pas voir les
    -- contrats d'un autre).
    ('RAKOTOARISOA', 'Naina', '199000112233', DATE '1999-06-02', 'Fianarantsoa',
     'Pharmacienne', 'Villa Hasina, Ambohipo, Antananarivo',
     '+261 32 88 41 09', 'rakotoarisoa@bailtech.mg',
     '$2a$12$bSEFOIeeaWe/rpxZdoIGTeZ6a/Lj9aptazxCb174J9PiFsvFNFKY6'),

    -- BAILLEUR 3 : proprietaire d'un immeuble de rapport
    ('ANDRIAMAMPIONONA', 'Tojo', '188000445566', DATE '1988-11-20', 'Mahajanga',
     'Commerçant', 'Boulevard Joffre, Mahajanga',
     '+261 38 77 12 45', 'andriamampionona@bailtech.mg',
     '$2a$12$J.Qqo3Pl46Z5QZ/ZPG42JOXkQMrjztokJz7m9LkGlTAhlAq0W3mvq'),

    -- LOCATAIRES
    ('SAHONDRA', 'Rakotomalala', '201211334455', DATE '2021-05-08', 'Fianarantsoa',
     'Comptable', 'Villa Soafaniry, Ambatobe, Antananarivo',
     '+261 32 45 678 90', 'sahondra.rakotomalala@email.mg',
     '$2a$12$GbfMfnxaNNQ4K5NZj1w/ZOHHEIyhqxy6fQLbthPFDCfY/nhoFdvUK'),

    ('ANDRY', 'Tiana Rakoto', '201054776688', DATE '2010-09-17', 'Toamasina',
     'Enseignant', 'Immeuble Fiaro, Ankorondrano, Antananarivo',
     '+261 33 11 222 33', 'andry.tiana@email.mg',
     '$2a$12$bSEFOIeeaWe/rpxZdoIGTeZ6a/Lj9aptazxCb174J9PiFsvFNFKY6'),

    ('LOVA', 'Heriniaina', '115842009977', DATE '2015-01-30', 'Mahajanga',
     'Infirmière', 'Rue Rainandriamampandry 67 ha, Antananarivo',
     '+261 38 44 555 66', 'lova.heriniaina@email.mg',
     '$2a$12$J.Qqo3Pl46Z5QZ/ZPG42JOXkQMrjztokJz7m9LkGlTAhlAq0W3mvq');

-- -----------------------------------------------------------------------------
-- 2. LOGEMENTS
-- -----------------------------------------------------------------------------
-- Les index de depart des compteurs JIRAMA sont volontairement distincts d'un
-- bien a l'autre : le calculateur compare l'index releve a l'index de depart
-- enregistre, et des valeurs egales partout masqueraient un defaut de calcul.
--
-- La colonne jirama_methode_repartition n'est renseignee que pour les deux
-- modes qui en ont besoin (PARTAGE et SOUS_COMPTEUR). Le contrat de bail
-- imprime alors la regle de repartition ; pour un compteur UNIQUE, elle reste
-- nulle et le contrat indique que la consommation est imputee en totalite au
-- locataire — c'est le traitement attendu par ContratPdfService.

INSERT INTO logement
    (adresse_lot, quartier_fokontany, ville, nombre_pieces_principales,
     description_consistance, compteur_eau_numero, compteur_eau_index_depart,
     compteur_electricite_numero, compteur_electricite_index_depart,
     jirama_type_gestion, jirama_methode_repartition, id_proprietaire)
VALUES
    -- Propriete de Jean-Luc. Compteur unique : pas de repartition.
    ('IVG 22', 'Ivandry', 'Antananarivo', 3,
     'Appartement T3 avec salon, deux chambres, cuisine et salle d''eau. '
     'Cour commune clôturée, portail rue côté Ivandry.',
     'JIRAMA-EAU-7781', 450, 'JIRAMA-ELEC-4411', 1250,
     'UNIQUE', NULL, 1),

    -- Compteur propre au locataire : chaque occupant est releve separement.
    ('A12', 'Ankorondrano', 'Antananarivo', 1,
     'Studio meublé, eau courante, accès à l''escalier B. '
     'Meuble par le propriétaire : lits, armoire, frigo.',
     'JIRAMA-EAU-3311', 210, 'JIRAMA-ELEC-9022', 640,
     'SOUS_COMPTEUR',
     'Répartition au prorata de la consommation relevée sur chaque sous-compteur.', 1),

    -- Bien volontairement SANS contrat actif : il doit apparaitre dans la liste
    -- « biens disponibles » de l'assistant de contrat. Sa villa n'est donc pas
    -- dans la liste des biens du formulaire tant qu'aucun bail n'est signe.
    ('B 15', 'Ambatobe', 'Antananarivo', 5,
     'Villa Bassine avec jardin clôturé, dépendance et garage. '
     'Cuisine équipée, véranda, mur d''enceinte.',
     'JIRAMA-EAU-5540', 860, 'JIRAMA-ELEC-1187', 3120,
     'PARTAGE',
     'Répartition au prorata de la consommation relevée sur chaque sous-compteur.', 1),

    -- Local commercial du bailleur 2.
    ('BC 3', 'Antaninarenina', 'Antananarivo', 2,
     'Local commercial en rez-de-chaussée, vitrine sur rue, WC et réserve. '
     'Loyer nu, non aménagé.',
     'JIRAMA-EAU-2210', 540, 'JIRAMA-ELEC-7712', 2450,
     'UNIQUE', NULL, 2),

    -- Appartement du bailleur 3.
    ('ZA 7', 'Antsirabe', 'Antsirabe', 4,
     'Duplex avec terrasse, 4 chambres, garage. Immeuble de rapport de 2015, '
     'ascenseur et groupe électrogène.',
     'JIRAMA-EAU-9071', 1180, 'JIRAMA-ELEC-3325', 2760,
     'PARTAGE',
     'Répartition au prorata de la consommation relevée sur chaque sous-compteur.', 3),

    -- Second bien du bailleur 3, en attente de signature.
    ('ZB 2', 'Antsirabe', 'Antsirabe', 3,
     'Appartement familial avec jardin, 3 chambres, cuisine et salle d''eau. '
     'Prévoir peinture à la prise de possession.',
     'JIRAMA-EAU-9072', 1320, 'JIRAMA-ELEC-3326', 3010,
     'UNIQUE', NULL, 3);

-- -----------------------------------------------------------------------------
-- 3. CONTRATS DE BAIL
-- -----------------------------------------------------------------------------
-- ATTENTION AUX IDENTIFIANTS. Les id ci-dessous ne sont PAS des numero de
-- telephone : ce sont les id_utilisateur produits par l'insertion precedente,
-- dans l'ordre ou elle a ete ecrite.
--
--     1 = RAZAFINDRAKOTO Jean-Luc   (bailleur)
--     2 = RAKOTOARISOA Naina        (bailleur)
--     3 = ANDRIAMAMPIONONA Tojo     (bailleur)
--     4 = SAHONDRA Rakotomalala     (locataire)
--     5 = ANDRY Tiana Rakoto        (locataire)
--     6 = LOVA Heriniaina           (locataire)
--
-- Un bailleur ne peut pas etre son propre locataire : le mettre dans la colonne
-- id_locataire produirait un contrat dont les deux parties sont la meme
-- personne, et un PDF ou le prenom du bailleur remplace celui du locataire.
-- Verifiable par la requete de controle en fin de script.
--
-- Rappel de la regle critique du modele : l'index partiel
-- uq_un_seul_contrat_actif_par_logement autorise UN SEUL contrat EN_COURS par
-- logement. Les insert ci-dessous respectent cette contrainte : les logements
-- 1, 2 et 4 ont chacun un unique bail actif, le logement 3 (villa B 15) et le
-- logement 5 (ZA 7) n'en ont aucun, et le logement 6 n'a qu'un contrat en
-- attente de signature.
--
-- Les dates sont figees et non calculees sur CURRENT_DATE : un jeu de test
-- doit donner le meme resultat aujourd'hui et dans six mois, faute de quoi les
-- echeances « du mois en cours » glisseraient avec le temps et la contrainte
-- CHECK (periode_annee >= 2026) finirait par bloquer une insertion.
--
--   Reference : septembre 2026.

INSERT INTO contrat_de_bail
    (date_debut, date_fin, montant_loyer_mga, montant_caution_mga,
     jour_paiement_mensuel, duree_preavis_mois, statut_actuel,
     id_logement, id_locataire)
VALUES
    -- Contrat 1 : actif, locataire SAHONDRA (4). Caution de 2 mois.
    (DATE '2026-03-01', DATE '2027-02-28', 1200000.00, 2400000.00,
     5, 3, 'EN_COURS', 1, 4),

    -- Contrat 2 : actif, locataire ANDRY (5), preavis de 6 mois (valeur
    -- atypique, pour verifier qu'elle apparait bien a l'article 2 du contrat).
    (DATE '2026-06-01', DATE '2027-12-31', 650000.00, 650000.00,
     10, 6, 'EN_COURS', 2, 5),

    -- Contrat 3 : actif, locataire LOVA (6). Caution de 2 mois.
    (DATE '2026-08-01', DATE '2027-07-31', 800000.00, 1600000.00,
     5, 3, 'EN_COURS', 4, 6),

    -- Contrat 4 : termine, sans impaye. Loue par SAHONDRA (4) sur le meme
    -- logement que le contrat 1 : la coexistence de deux baux successifs sur
    -- un bien est le cas normal, et le second etant termine il ne viole pas
    -- l'index « un seul contrat actif par logement ».
    (DATE '2025-01-01', DATE '2025-12-31', 550000.00, 550000.00,
     1, 3, 'TERMINE', 1, 4),

    -- Contrat 5 : resilie en cours de bail, locataire ANDRY (5). Ses impayes
    -- subsistent apres la resiliation : une dette ne disparait pas avec le
    -- contrat, elle doit rester visible.
    (DATE '2025-06-01', DATE '2026-05-31', 700000.00, 700000.00,
     15, 3, 'RESILIE', 4, 5),

    -- Contrat 6 : en attente de signature, locataire LOVA (6). Le logement 6
    -- (ZB 2) n'est donc pas dans la liste des biens disponibles tant que ce
    -- bail n'est pas active.
    (DATE '2026-10-01', DATE '2027-09-30', 900000.00, 1800000.00,
     5, 3, 'EN_ATTENTE_SIGNATURE', 6, 6);

-- -----------------------------------------------------------------------------
-- 4. ECHEANCES DE LOYER
-- -----------------------------------------------------------------------------
-- Rappel des contraintes :
--   * CHECK (periode_mois BETWEEN 1 AND 12) et CHECK (periode_annee >= 2026) ;
--   * UNIQUE (id_contrat, periode_mois, periode_annee) — une echeance par
--     contrat et par periode, d'ou l'absence volontaire de doublon ;
--   * montant_paye n'a pas de contrainte, mais un statut PAYE ou PARTIEL avec
--     un montant paye nul serait incoherent pour l'affichage : les lignes
--     « payees » portent donc toujours une date de reglement.
--
-- Les echeances ne sont creees que pour les contrats EN_COURS ou TERMINE :
-- un bail non signe n'a pas de dette, et un bail resilie conserve les siennes.
--
-- Contrat 1 (1 200 000 Ar/mois) : deux mois soldes, un mois partiellement paye
-- (400 000 sur 1 200 000) — c'est ce qui alimente la carte « echeances non
-- soldees » du tableau de bord.
INSERT INTO paiement_loyer
    (id_contrat, periode_mois, periode_annee, montant_attendu, montant_paye,
     date_paiement_effectif, mode_paiement, statut, recu_quittance_genere)
VALUES
    (1, 7, 2026, 1200000.00, 1200000.00, DATE '2026-07-03', 'MOBILE_MONEY', 'PAYE', TRUE),
    (1, 8, 2026, 1200000.00, 1200000.00, DATE '2026-08-04', 'ESPECES',      'PAYE', TRUE),
    (1, 9, 2026, 1200000.00,  400000.00, NULL,                  'MOBILE_MONEY', 'PARTIEL', FALSE);

-- Contrat 2 (650 000 Ar/mois) : un mois en retard, le mois courant impaye.
-- L'echeance en retard fait passer le contrat en rose sur la visionneuse.
INSERT INTO paiement_loyer
    (id_contrat, periode_mois, periode_annee, montant_attendu, montant_paye,
     date_paiement_effectif, mode_paiement, statut, recu_quittance_genere)
VALUES
    (2, 8, 2026,  650000.00, 0.00, NULL, NULL, 'EN_RETARD', FALSE),
    (2, 9, 2026,  650000.00, 0.00, NULL, NULL, 'A_PAYER',   FALSE);

-- Contrat 3 (800 000 Ar/mois) : le mois precedent est solde, le mois courant
-- reste a payer.
INSERT INTO paiement_loyer
    (id_contrat, periode_mois, periode_annee, montant_attendu, montant_paye,
     date_paiement_effectif, mode_paiement, statut, recu_quittance_genere)
VALUES
    (3, 8, 2026, 800000.00, 800000.00, DATE '2026-08-02', 'VIREMENT', 'PAYE', TRUE),
    (3, 9, 2026, 800000.00,      0.00, NULL,            NULL,       'A_PAYER', FALSE);

-- Contrat 4 (termine, 550 000 Ar/mois) : annee complete, tout est solde.
-- Ces lignes prouvent que l'historique d'un locataire est conserve apres la
-- fin du bail.
INSERT INTO paiement_loyer
    (id_contrat, periode_mois, periode_annee, montant_attendu, montant_paye,
     date_paiement_effectif, mode_paiement, statut, recu_quittance_genere)
VALUES
    (4, 1, 2026, 550000.00, 550000.00, DATE '2026-01-02', 'VIREMENT', 'PAYE', TRUE),
    (4, 2, 2026, 550000.00, 550000.00, DATE '2026-02-03', 'VIREMENT', 'PAYE', TRUE),
    (4, 3, 2026, 550000.00, 550000.00, DATE '2026-03-02', 'VIREMENT', 'PAYE', TRUE);

-- Contrat 5 (resilie) : deux mois de retard subsistent apres la resiliation.
-- Ils doivent rester visibles : une dette ne disparait pas avec le contrat.
INSERT INTO paiement_loyer
    (id_contrat, periode_mois, periode_annee, montant_attendu, montant_paye,
     date_paiement_effectif, mode_paiement, statut, recu_quittance_genere)
VALUES
    (5, 4, 2026, 700000.00, 0.00, NULL, NULL, 'EN_RETARD', FALSE),
    (5, 5, 2026, 700000.00, 0.00, NULL, NULL, 'EN_RETARD', FALSE);

-- ------------------------------------------------------------------- Verif
-- Les compteurs ci-dessous sont releves par la vue de controle, pas par
-- l'application : ils servent a confirmer d'un coup d'oeil que le jeu est
-- complet et qu'aucune contrainte n'a ete violee.
--
-- Attendu :
--   utilisateurs ......... 6   (3 bailleurs + 3 locataires)
--   logements ............ 6
--   contrats ............. 6   (3 EN_COURS, 1 TERMINE, 1 RESILIE, 1 en attente)
--   paiements ............ 12  (3 + 2 + 2 + 3 + 2)
--   contrats actifs ...... 3   (un par logement, conformement a l'index partiel)
--   echeances impayees ... 4   (1 PARTIEL + 3 EN_RETARD)
--   montant impaye ....... 2 850 000 Ar

-- ------------------------------------------------------------------- Verif
-- Les contrôles ci-dessous ne sont pas décoratifs : ils ont déjà détecté une
-- erreur réelle de ce fichier (un bailleur donné comme son propre locataire).
-- Ils sont donc exécutés AVANT le COMMIT, et utilisent RAISE EXCEPTION pour
-- interrompre le script : un jeu de test livré avec de mauvaises données
-- échouerait silencieusement, et le défaut n'apparaîtrait qu'à la lecture
-- d'un contrat PDF où le prenom du bailleur remplace celui du locataire.
--
-- Les compteurs affichés servent de confirmation rapide que le jeu est complet.
--
-- Attendu :
--   utilisateurs ......... 6   (3 bailleurs + 3 locataires)
--   logements ............ 6
--   contrats ............. 6   (3 EN_COURS, 1 TERMINE, 1 RESILIE, 1 en attente)
--   paiements ............ 12  (3 + 2 + 2 + 3 + 2)
--   contrats actifs ...... 3   (un par logement, conformement a l'index partiel)
--   echeances impayees ... 4   (1 PARTIEL + 3 EN_RETARD)
--   montant impaye ....... 2 850 000 Ar

DO $$
DECLARE
    fautif TEXT;
BEGIN
    -- Un bailleur ne doit pas etre son propre locataire.
    SELECT string_agg('contrat ' || c.id_contrat, ', ')
      INTO fautif
      FROM contrat_de_bail c
      JOIN logement l ON l.id_logement = c.id_logement
     WHERE c.id_locataire = l.id_proprietaire;
    IF fautif IS NOT NULL THEN
        RAISE EXCEPTION 'Bailleur aussi locataire : %', fautif;
    END IF;

    -- L'index partiel ne garantit qu'un contrat EN_COURS par logement ; ce
    -- controle le verifie independamment, sans faire confiance a l'index.
    SELECT string_agg('logement ' || id_logement, ', ')
      INTO fautif
      FROM (SELECT id_logement FROM contrat_de_bail
             WHERE statut_actuel = 'EN_COURS'
             GROUP BY id_logement HAVING COUNT(*) > 1) d;
    IF fautif IS NOT NULL THEN
        RAISE EXCEPTION 'Plusieurs contrats EN_COURS sur : %', fautif;
    END IF;

    -- Le plancher de periode est une contrainte CHECK : on verifie qu'aucune
    -- echeance ne s'en est approchee, sans quoi une insertion future echouerait.
    IF EXISTS (SELECT 1 FROM paiement_loyer WHERE periode_annee < 2026) THEN
        RAISE EXCEPTION 'Echeance anterieure a 2026 : refusee par CHECK';
    END IF;

    -- Une echeance PAYE sans date de reglement ferait un recu muet.
    IF EXISTS (SELECT 1 FROM paiement_loyer
                WHERE statut = 'PAYE' AND date_paiement_effectif IS NULL) THEN
        RAISE EXCEPTION 'Echeance PAYE sans date de paiement effectif';
    END IF;

    -- Les CIN doivent tous tenir sur 12 chiffres (controle applicatif @CinNational).
    IF EXISTS (SELECT 1 FROM utilisateur WHERE cin_numero !~ '^[0-9]{12}$') THEN
        RAISE EXCEPTION 'CIN ne respectant pas les 12 chiffres';
    END IF;

    -- Les mots de passe doivent etre des empreintes BCrypt exploitables, sinon
    -- la connexion par CIN echoue. Le prefixe identifie BCrypt (cout 12) ; la
    -- colonne est NOT NULL, mais rien n'en garantit le format a l'insertion.
    -- Le motif est ecrit avec chr() : un '$' litteral dans un bloc dollar-quote
    -- serait interprete comme un debut de substitution de variable.
    IF EXISTS (SELECT 1 FROM utilisateur
                WHERE left(mot_de_passe, 4) <> chr(36) || '2a' || chr(36)) THEN
        RAISE EXCEPTION 'Mot de passe hors format BCrypt : connexion impossible';
    END IF;
END $$;

COMMIT;

-- Affiche un récapitulatif ; la transaction ci-dessus est deja validée, un échec
-- de cette section ne remet donc rien en cause.
SELECT 'utilisateurs' AS entite, COUNT(*) AS nombre FROM utilisateur
UNION ALL SELECT 'logements',             COUNT(*) FROM logement
UNION ALL SELECT 'contrats',              COUNT(*) FROM contrat_de_bail
UNION ALL SELECT 'paiements',             COUNT(*) FROM paiement_loyer
UNION ALL SELECT 'contrats EN_COURS',     COUNT(*) FROM contrat_de_bail WHERE statut_actuel = 'EN_COURS'
UNION ALL SELECT 'echeances impayees',    COUNT(*) FROM paiement_loyer
                                     WHERE statut IN ('EN_RETARD', 'PARTIEL');

-- =============================================================================
-- Fin du script
-- =============================================================================
