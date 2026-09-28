# Contrat-bail

Application de gestion des contrats de bail d'habitation (Madagascar) avec
**Spring Boot 3.5**, **Spring Data JPA**, **Thymeleaf** et **PostgreSQL 16**.

---

## 1. Démarrage avec Docker

Prérequis : Docker et Docker Compose.

```bash
docker compose up --build
```

Puis ouvrir [http://localhost:8080/](http://localhost:8080/).

| Commande | Effet |
|---|---|
| `docker compose down` | arrête les conteneurs |
| `docker compose down -v` | arrête et supprime le volume PostgreSQL |

Le script `sql/240920260828-database_init.sql` (types énumérés, tables, clés
étrangères, **index unique partiel**) est exécuté automatiquement lors de la
première création du volume. Un jeu de démonstration est ensuite injecté au
démarrage de l'application (voir § 5).

---

## 2. Démarrage en local

```bash
# 1. Créer la base puis appliquer le schéma de référence
createdb contratbail
psql -U postgres -d contratbail -f sql/240920260828-database_init.sql

# 2. Lancer l'application
mvn spring-boot:run
```

Les variables d'environnement ci-dessous surcharge `application.properties` :

| Variable | Défaut | Rôle |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/contratbail` | URL JDBC |
| `DATABASE_USERNAME` | `postgres` | utilisateur PostgreSQL |
| `DATABASE_PASSWORD` | `1234` | mot de passe |
| `SPRING_PROFILES_ACTIVE` | `dev` | profil Spring |
| `THYMELEAF_PREFIX` | `file:./template/` | dossier des gabarits |
| `THYMELEAF_CACHE` | `false` | mise en cache des gabarits |
| `BAILTECH_DEMO` | `true` | jeu de données de démonstration |
| `JPA_SHOW_SQL` | `false` | affichage des requêtes SQL |

> `spring.jpa.hibernate.ddl-auto=validate` : le schéma SQL fait autorité et le
> mapping Hibernate est vérifié au démarrage. Toute divergence entre
> `sql/…-database_init.sql` et les entités `@Entity` empêche le démarrage.

---

## 3. Pages disponibles

| Route | Gabarit | Rôle |
|---|---|---|
| `/`, `/contrats`, `/contrats/create` | `template/contract/contract.html` | assistant de création d'un contrat (formulaire relié à la base) |
| `POST /contrats` | — | enregistrement du contrat saisi |
| `/dashboard` | `template/dashboard/dashboard.html` | synthèse du parc, contrats récents, recherche |
| `/locataires` | `template/documents/documents.html` | dossiers des locataires (CIN masquées) |
| `/jirama` | `template/jirama/jirama.html` | compteurs JIRAMA des biens et sous-compteurs des baux en cours |

Le paramètre `?q=` filtre la recherche (locataire, CIN, bien) sur le tableau de
bord et sur les dossiers ; `?bailleurId=` et `?logementId=` permettent de
sélectionner un contexte (à remplacer par la session une fois le module
d'authentification en place).

---

## 4. Couche Base de données & liaison (Spring Data JPA)

### Entités — `mg.bailtech.model`

| Entité | Table | Points d'attention |
|---|---|---|
| `Utilisateur` | `utilisateur` | CIN à 12 chiffres contrainte en Java **et** en base ; `email` unique ; locator/bailleur distingués par leurs relations, le modèle v1 n'ayant pas de colonne de rôle |
| `Logement` | `logement` | FK `id_proprietaire` en `ON DELETE RESTRICT` ; compteurs JIRAMA et index de départ ; `type_compteur_jirama` en énumération nommée |
| `ContratDeBail` | `contrat_de_bail` | `chk_dates_coherentes` reproduite par `@Check` **et** par `@AssertTrue` ; `isLocationActive()` reproduit le prédicat de l'index partiel |
| `PaiementLoyer` | `paiement_loyer` | unicité `(id_contrat, periode_mois, periode_annee)` ; reste dû et statut calculés |

**Index unique partiel.** La règle métier critique du modèle est
« un seul contrat `EN_COURS` par logement » :

```sql
CREATE UNIQUE INDEX uq_un_seul_contrat_actif_par_logement
    ON contrat_de_bail (id_logement) WHERE statut_actuel = 'EN_COURS';
```

Cette contrainte n'est pas exprimable en annotations JPA. Elle est donc
appliquée à trois niveaux :

1. **requête** — `LogementRepository.findDisponiblesPourProprietaire(...)` et
   `UtilisateurRepository.findLocatairesDisponibles(...)` n'offrent que des
   logements / locataires dont aucun contrat n'est `EN_COURS` ;
2. **service** — `ContratService.enregistrer(...)` refuse l'enregistrement via
   `ContratDeBailRepository.existsContratActifParLogement(...)` et renvoie un
   message exploitable ;
3. **base** — l'index reste l'arbitre final (protection contre les insertions
   concurrentes) ; le contrôleur intercepte la `DataIntegrityViolationException`
   qui en découle.

### Repositories — `mg.bailtech.repository`

| Méthode | Usage |
|---|---|
| `UtilisateurRepository.findByCinNumeroNormalise` | « trouver un utilisateur par sa CIN » (saisie du locataire) |
| `UtilisateurRepository.findLocatairesDisponibles` | locataires libres de tout bail en cours |
| `UtilisateurRepository.rechercher` / `findBailleurs` / `findLocataires` | barre de recherche, en-tête, coffre-fort |
| `LogementRepository.findDisponiblesPourProprietaire` | « logements immatriculés » du formulaire |
| `LogementRepository.findByIdAvecProprietaire` | lecture du propriétaire hors session |
| `ContratDeBailRepository.findContratsActifsDuBailleur` | « contrats actifs d'un bailleur » |
| `ContratDeBailRepository.findContratActifParLogement` (SQL natif) | application de l'index partiel |
| `ContratDeBailRepository.rechercherContratsDuBailleur` | recherche du tableau de bord |
| `PaiementLoyerRepository.findByContratIdOrderByPeriodeAnneeDescPeriodeMoisDesc` | historique d'un bail |
| `PaiementLoyerRepository.findPaiementsDuBailleur` / `sumResteDuPeriode` | relances et agrégats |

Les méthodes destinées au rendu sont annotées `@EntityGraph` : avec
`spring.jpa.open-in-view=false`, toute association paresseuse non préchargée
provoquerait une `LazyInitializationException` pendant le rendu Thymeleaf.

### Formulaire — `web.dto.ContratForm`

Le formulaire est un **objet de liaison dédié**, et non l'entité
`ContratDeBail` : les listes déroulantes renvoient des identifiants
(`logementId`, `locataireId`), la saisie peut créer un locataire inconnu, et
l'aperçu du contrat a besoin de valeurs déjà résolues (nom du bailleur, adresse
du bien, mode de comptage) sans initialisation paresseuse.

### Messages de validation

`src/main/resources/ValidationMessages.properties` alimente à la fois
l'interpolation JSR-380 (`@NotBlank(message = "{contrat.logement.notNull}")`) et
les blocs `th:errors` des gabarits, en français.

---

## 5. Câblage Thymeleaf

Les quatre gabarits statiques du dossier `template/` sont reliés aux données :

| Attribut | Emploi |
|---|---|
| `th:object="${contratForm}"` | objet de formulaire de l'assistant de contrat |
| `th:field="*{…}"` | 17 champs liés aux colonnes de `contrat_de_bail` et `utilisateur` |
| `th:action` / `th:method` | soumission vers `POST /contrats`, formulaires de recherche en `GET` |
| `th:each` | listes déroulantes (logements disponibles, locataires disponibles, compteurs) et tableaux (contrats récents, dossiers, sous-compteurs) |
| `th:errors` | 17 messages de validation rattachés à leur champ |
| `th:text` | affichage des agrégats, des libellés d'énumération et de l'aperçu du contrat |
| `th:href` | navigation entre les pages |
| `th:classappend` | pastilles de statut sans perdre les classes Tailwind |

Les listes de sélection ne sont jamais figées dans le gabarit : elles proviennent
des repositories, si bien qu'un logement déjà sous contrat `EN_COURS` n'apparaît
jamais dans la liste déroulante.

### Jeu de démonstration

Au démarrage, si la table `utilisateur` est vide, `config.DonneesDemo` injecte
4 utilisateurs, 4 logements, 4 contrats (dont un `EN_ATTENTE_SIGNATURE`) et
6 échéances, afin que les listes `th:each` et les agrégats du tableau de bord
aient des données. Désactivation : `BAILTECH_DEMO=false`.

---

## 6. Limites connues du MVP

* **Authentification absente** : le bailleur courant est déduit du premier
  propriétaire enregistré, ou du paramètre `?bailleurId=`. `ContratService.utilisateurCourant(...)`
  est le point à remplacer par la lecture de la session.
* **Pas de table de pièces justificatives** : le coffre-fort documentaire
  affiche l'identité réelle des locataires ; l'inventaire des scans dépend du
  module de stockage chiffré.
* **Pas de table de relevés JIRAMA** : le calculateur affiche les compteurs et
  index de départ des biens ; la formule de répartition (compteur unique /
  partagé / sous-compteur) reste à implémenter.
* **Génération du PDF et échéances automatiques** : hors périmètre de cette
  couche, prises en charge par le moteur documentaire.
