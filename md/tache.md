
------------------------------
## 👤 Mandresy : Base de Données & Liaison de Données (Binding)
Son rôle est d'injecter la structure PostgreSQL dans les formulaires statiques déjà présents dans templates. [2] 

* Tâches Backend (Spring Data JPA) :
* Générer les entités @Entity Hibernate (Utilisateur, Logement, ContratDeBail, PaiementLoyer) en respectant strictement l'index partiel PostgreSQL (EN_COURS).
   * Écrire les Spring Data Repositories avec les requêtes de recherche (ex: trouver un utilisateur par sa CIN ou récupérer les contrats actifs d'un bailleur).
* Tâches Front-end (Câblage Thymeleaf) :
* Prendre les fichiers HTML statiques du dossier templates et y ajouter les attributs de liaison Thymeleaf (th:object, th:field, th:action).
   * Injecter dynamiquement les listes dans les balises de sélection via th:each (ex: charger la liste des logements immatriculés et des locataires disponibles).

------------------------------
## 👤 Narindra : Contrôleurs & Moteurs Documentaires (.PDF)
Son rôle est de gérer la navigation, le traitement des requêtes et la génération des livrables juridiques du MVP.

* Tâches Backend (Moteurs de compilation) :
* Développer un service de conversion de documents (ex: en utilisant OpenPDF ou Flying Saucer qui convertit directement du HTML/Thymeleaf en PDF).
   * Coder la logique de génération automatique des échéances mensuelles de loyer dès qu'un contrat passe au statut EN_COURS.
* Tâches Web (Spring MVC Controller) :
* Concevoir les @Controller pour gérer le routage entre le Tableau de bord, le générateur (/contrats/create-wizard) et la visionneuse. [1] 
   * Créer la méthode @PostMapping("/enregistrer") pour intercepter les données soumises par le formulaire, lever les exceptions en cas d'incohérence de dates, et déclencher le téléchargement du PDF conforme de 2 pages.

------------------------------
## 👤 Tohavina : Moteur de Calcul (JIRAMA) & Sécurité des Identités
Son rôle est de gérer l'intelligence algorithmique locale (calculs, conversion) et la sécurisation des données sensibles. [2] 

* Tâches JIRAMA & Algorithmes (Back + Front) :
* Câbler le module Calculateur JIRAMA : récupérer les index saisis dans le template front, appliquer la formule de répartition (compteur unique, partagé ou sous-compteur) et mettre à jour l'entité PaiementLoyer correspondante.
   * Écrire l'algorithme backend (ou script d'appui frontend) de traduction automatique du montant numérique du loyer en texte complet (ex: 400 000 → "Quatre cent mille Ariary").
* Tâches Gestion des Identités & Sécurité :
* Implémenter la validation stricte des formats spécifiques (bloquer la validation si le champ CIN ne fait pas exactement 12 chiffres).
   * Mettre en place la logique de chiffrement des données d'authentification (mots de passe) et sécuriser le stockage des fichiers/scans administratifs liés au profil.

