
------------------------------
## Documentation de la Base de Données : LegalTech Bail Madagascar
Ce document présente la structure physique de la base de données relationnelle PostgreSQL pour l'application de gestion des baux d'habitation.
## 1. Dictionnaires des Types Personnalisés (ENUMs)## statut_contrat
Sert à définir l'état civil et juridique du bail d'habitation.

* EN_ATTENTE_SIGNATURE : Le document est généré mais les parties n'ont pas encore signé.
* EN_COURS : Le bail est actif (un seul contrat actif autorisé simultanément par logement).
* TERMINE : Le contrat est arrivé à son terme légal.
* RESILIE : Le contrat a été rompu de manière anticipée ou par clause résolutoire.

## statut_paiement
Assure le suivi financier mensuel des quittances.

* A_PAYER : Le mois a débuté, la facture est émise.
* PAYE : Le bailleur a validé la réception des fonds.
* EN_RETARD : La date limite fixée au contrat est dépassée.
* PARTIEL : Le locataire a versé une avance mais ne s'est pas acquitté de la totalité.

## type_compteur_jirama
Définit la configuration technique de l'approvisionnement en eau et électricité.

* UNIQUE : Compteur propre et indépendant pour le logement.
* PARTAGE : Compteur commun à plusieurs habitations de la propriété.
* SOUS_COMPTEUR : Décompte interne installé pour mesurer la consommation exacte.

------------------------------
## 2. Structure des Tables## Table utilisateur
Stocke les données d'identité du bailleur (propriétaire) et du locataire.

| Nom du Champ | Type de Données | Contraintes | Description |
|---|---|---|---|
| id_utilisateur | SERIAL | PK | Identifiant unique de l'utilisateur. |
| nom | VARCHAR(100) | NOT NULL | Nom de famille officiel. |
| prenom | VARCHAR(100) | - | Prénoms (optionnel). |
| cin_numero | VARCHAR(12) | NOT NULL, UNIQUE | Numéro de la Carte d'Identité Nationale (12 chiffres). |
| cin_date_delivrance | DATE | NOT NULL | Date d'établissement de la CIN. |
| cin_lieu_delivrance | VARCHAR(100) | NOT NULL | Commissariat ou District émetteur. |
| profession | VARCHAR(100) | - | Profession déclarée au contrat. |
| adresse_actuelle | TEXT | NOT NULL | Domicile actuel légal. |
| telephone | VARCHAR(20) | NOT NULL | Numéro de contact. |
| email | VARCHAR(150) | NOT NULL, UNIQUE | Email d'authentification à la plateforme. |
| mot_de_passe | VARCHAR(255) | NOT NULL | Empreinte du mot de passe haché (BCrypt). |

## Table logement
Représente le bien immobilier mis en location.

| Nom du Champ | Type de Données | Contraintes | Description |
|---|---|---|---|
| id_logement | SERIAL | PK | Identifiant unique du bien. |
| adresse_lot | VARCHAR(50) | NOT NULL | Numéro de lot (ex: Lot IVG 22). |
| quartier_fokontany | VARCHAR(100) | NOT NULL | Fokontany de rattachement administratif. |
| ville | VARCHAR(100) | NOT NULL | Ville (ex: Antsirabe, Mahajanga). |
| nombre_pieces_principales | INT | NOT NULL, > 0 | Nombre de pièces à vivre. |
| description_consistance | TEXT | - | Descriptif de la cour, dépendances, sanitaires. |
| compteur_eau_numero | VARCHAR(50) | - | Numéro gravé sur le compteur d'eau JIRAMA. |
| compteur_eau_index_depart | INT | DEFAULT 0 | Relève d'eau initial à l'état des lieux d'entrée. |
| compteur_electricite_numero | VARCHAR(50) | - | Numéro gravé sur le compteur électrique. |
| compteur_electricite_index_depart | INT | DEFAULT 0 | Relève d'électricité initial. |
| jirama_type_gestion | type_compteur_jirama | NOT NULL | Politique de gestion du compteur. |
| jirama_methode_repartition | TEXT | - | Règles de calcul si le compteur est partagé. |
| id_proprietaire | INT | FK, NOT NULL | Référence au Bailleur (utilisateur). |

## Table contrat_de_bail
Assure la liaison contractuelle entre le bien, le propriétaire (Bailleur) et le preneur (Locataire).

| Nom du Champ | Type de Données | Contraintes | Description |
|---|---|---|---|
| id_contrat | SERIAL | PK | Identifiant unique du contrat de bail. |
| date_debut | DATE | NOT NULL | Date de prise d'effet officielle. |
| date_fin | DATE | NOT NULL | Date d'échéance du bail. |
| montant_loyer_mga | DECIMAL(12, 2) | NOT NULL, > 0 | Loyer mensuel brut stipulé en Ariary. |
| montant_caution_mga | DECIMAL(12, 2) | NOT NULL, >= 0 | Dépôt de garantie versé à la signature. |
| jour_paiement_mensuel | INT | NOT NULL, 1 to 31 | Date limite de paiement (ex: avant le 5). |
| duree_preavis_mois | INT | NOT NULL, DEFAULT 3 | Délai de congé réglementaire (3 mois). |
| statut_actuel | statut_contrat | NOT NULL | État actuel du cycle de vie du bail. |
| id_logement | INT | FK, NOT NULL | Référence au bien immobilier loué. |
| id_locataire | INT | FK, NOT NULL | Référence au Locataire (utilisateur). |

## Table paiement_loyer
Historique d'exécution financière du contrat.

| Nom du Champ | Type de Données | Contraintes | Description |
|---|---|---|---|
| id_paiement | SERIAL | PK | Identifiant unique de la transaction. |
| id_contrat | INT | FK, NOT NULL | Référence au contrat parent. |
| periode_mois | INT | NOT NULL, 1 to 12 | Mois concerné par l'échéance. |
| periode_annee | INT | NOT NULL, >= 2026 | Année concernée par l'échéance. |
| montant_attendu | DECIMAL(12, 2) | NOT NULL | Loyer théorique exigible (copie du contrat). |
| montant_paye | DECIMAL(12, 2) | DEFAULT 0.00 | Cumul des sommes effectivement versées. |
| date_paiement_effectif | DATE | - | Date de régularisation enregistrée. |
| mode_paiement | VARCHAR(50) | - | Canal utilisé (ESPECES, MOBILE_MONEY, etc.). |
| statut | statut_paiement | NOT NULL | État comptable de l'échéance mensuelle. |
| recu_quittance_genere | BOOLEAN | DEFAULT FALSE | Indique si le PDF de la quittance est disponible. |

------------------------------
## 3. Contraintes d'Intégrité Spécifiques## Règles de Clés Étrangères (FK)

* fk_logement_proprietaire : ON DELETE RESTRICT — Interdit la suppression d'un utilisateur si des biens immobiliers lui sont toujours rattachés en base.
* fk_contrat_logement : ON DELETE RESTRICT — Un logement lié à un historique de contrat ne peut pas être supprimé par inadvertance.
* fk_paiement_contrat : ON DELETE CASCADE — Si un projet de contrat de bail en attente est supprimé, toutes ses lignes de facturation prévisionnelles associées sont nettoyées automatiquement.

## Index Unique Partiel (Règle Métier Critique)
Pour empêcher qu'un logement ne possède plusieurs locataires actifs en même temps, un index partiel applique l'unicité uniquement sur les lignes dont le bail est en cours :

CREATE UNIQUE INDEX uq_un_seul_contrat_actif_par_logement ON contrat_de_bail (id_logement) WHERE statut_actuel = 'EN_COURS';

(Cette méthode autorise un historique infini de contrats TERMINE ou RESILIE sur le même id_logement, mais bloque toute tentative de double location active).
## Contrainte de Période Financière
Empêche les doublons de quittances pour un même mois :

CONSTRAINT uq_paiement_periode UNIQUE (id_contrat, periode_mois, periode_annee)

------------------------------


