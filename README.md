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
| `BAILTECH_BCRYPT_COUT` | `12` | coût du hachage BCrypt |
| `BAILTECH_COFFRE` | `./coffre-securise` | racine du coffre chiffré des pièces |
| `BAILTECH_CLE_CHIFFREMENT` | *(vide)* | clé AES-256 en Base64 ; **requis** pour utiliser le coffre |

> `spring.jpa.hibernate.ddl-auto=validate` : le schéma SQL fait autorité et le
> mapping Hibernate est vérifié au démarrage. Toute divergence entre
> `sql/…-database_init.sql` et les entités `@Entity` empêche le démarrage.

---

## 3. Pages disponibles

| Route | Méthode | Gabarit / effet | Rôle |
|---|---|---|---|
| `/`, `/contrats/create` | GET | `contract/contract.html` | assistant de création (formulaire relié à la base) |
| `/contrats/create-wizard` | GET | idem | alias de l'assistant (cahier des charges) |
| `/contrats` | GET | `contract/contrats.html` | visionneuse : liste des contrats |
| `/contrats` | POST | **PDF** | enregistre la saisie et télécharge le contrat |
| `/enregistrer` | POST | **PDF** | idem, alias du cahier des charges |
| `/contrats/{id}` | GET | `contract/contrat-fiche.html` | fiche du contrat et de son échéancier |
| `/contrats/{id}/pdf` | GET | **PDF inline** | affichage dans le navigateur |
| `/contrats/{id}/pdf/telecharger` | GET | **PDF joint** | téléchargement |
| `/contrats/{id}/signature` | POST | redirection | le bail passe en `EN_COURS`, les échéances sont générées |
| `/dashboard` | GET | `dashboard/dashboard.html` | synthèse du parc et contrats récents |
| `/locataires` | GET | `documents/documents.html` | dossiers des locataires (CIN masquées) |
| `/jirama` | GET | `jirama/jirama.html` | compteurs JIRAMA et sous-compteurs |
| `/jirama/calculer` | POST | `jirama/jirama.html` | **simulation** de la répartition, n'écrit rien |
| `/jirama/appliquer` | POST | `jirama/jirama.html` | impute la part JIRAMA sur les échéances |

Le paramètre `?q=` filtre la recherche sur le tableau de bord et les dossiers ;
`?bailleurId=` et `?logementId=` sélectionnent un contexte (à remplacer par la
session dès le module d'authentification).

---

## 4. Moteur documentaire (PDF) et cycle de vie du bail

### 4.1 Génération du PDF

`service.ContratPdfService` produit le contrat en deux pages :

1. `web.dto.ContratPdfModel` est assemblé **en transaction** — les associations
   paresseuses sont lues à ce moment, jamais pendant le rendu, ce qui permet de
   garder `spring.jpa.open-in-view=false` ;
2. le gabarit `template/contrat/contrat-pdf.html` est rendu par un **moteur
   Thymeleaf dédié** (`config.ConfigurationImpression`) ;
3. **Flying Saucer** (backend **OpenPDF**) convertit ce XHTML en PDF.

| Point | Choix et raison |
|---|---|
| Bibliothèque | `org.xhtmlrenderer:flying-saucer-pdf` — OpenPDF est le successeur LGPL d'iText, sans restriction de licence |
| Moteur Thymeleaf | Un second moteur en mode `XML` (Thymeleaf 3.1 a fusionné l'ancien mode `XHTML`). Le moteur du web est en mode `HTML5`, ses sorties ne sont pas analysables par le parseur SAX du moteur PDF |
| Resolveur | Construit **hors du contexte de beans** : Spring Boot ajoute au moteur du web tous les beans `ITemplateResolver` trouvés, un bean XHTML y entrerait en concurrence sur les fichiers `.html` du site |
| Polices | Times et Helvetica, polices PDF standard : aucun fichier de police à embarquer, le conteneur Docker n'installant aucune police. Leur jeu est Latin-1, d'où les entités numériques (`&#176;`, `&#183;`, `&#8212;`) dans le gabarit |
| Pagination | Un saut de page explicite (`page-break-after`) : page 1 = parties + articles 1 à 4, page 2 = articles 5 à 7, échéancier, signatures, visa du Fokontany |
| Montant en lettres | L'article 3 exige le loyer en chiffres **et** en toutes lettres, cette seconde forme faisant foi en cas de contestation. `service.ConversionMontant` produit « Quatre cent mille Ariary » à partir de `contrat.montant_loyer_mga` (§ 4.4) |

### 4.2 Génération des échéances

`service.EcheanceService` génère le plan de facturation dès le passage en
`EN_COURS`, et uniquement à ce moment-là (un contrat en attente de signature n'a
pas de loyer dû).

| Règle | Mise en œuvre |
|---|---|
| Étendue | une échéance par mois entamé, du mois de `date_debut` au mois en cours — on ne facture pas d'avance |
| Plancher de la base | aucune période antérieure à `PaiementLoyer.PERIODE_MIN` (2026-01) : la contrainte `CHECK (periode_annee >= 2026)` rejetterait l'insertion et interromprait le démarrage. Les périodes exclues sont comptées et journalisées |
| Idempotence | une période déjà présente est ignorée (`uq_paiement_periode`) : l'opération peut être rejouée sans doublon |
| Statut initial | `EN_RETARD` si la date limite de paiement du mois est dépassée, `A_PAYER` sinon. Le passage à `PAYE` relève de la validation du règlement par le bailleur |
| Cas du 31 | un jour de paiement au 31 sur un mois de 30 jours retombe sur le dernier jour du mois au lieu de lever une exception |

### 4.3 Cycle de vie

```
EN_ATTENTE_SIGNATURE ──POST /contrats/{id}/signature──▶ EN_COURS  (+ échéances)
        │                                                        │
        └────────────── generation du PDF (2 pages) ──────────────┘
```

La signature est refusée si un autre bail du même logement est déjà `EN_COURS`
(contrainte de l'index unique partiel) ou si le contrat n'appartient pas au
bailleur connecté.

### 4.4 Conversion d'un montant en toutes lettres

`service.ConversionMontant` transforme un `BigDecimal` d'Ariary en texte complet,
sans dépendance externe. Les règles d'orthographe appliquées :

| Cas | Exemple | Sortie |
|---|---|---|
| Dizaines construites sur une base 20 | 71 | Soixante et onze |
| Pluriel de quatre-vingts | 80 | Quatre-vingts |
| Cent sans élément suivant | 200 | Deux cents |
| Cent suivi d'un élément | 201 | Deux cent un |
| « et » après 20 à 60, pas après 80 ni 90 | 21, 81 | Vingt et un ; quatre-vingt-un |
| **Mille** invariable, sans « un » devant | 1 000 ; 400 000 | Mille ; quatre cent mille |
| Cent garde son « s » devant million | 200 000 000 | Deux cents millions |
| Centimes | 1 200 000,50 | Un million deux cent mille Ariary et cinquante centimes |

Les chiffres sont vérifiés caractère par caractère, et non par la classe regex
`\d` de Java qui inclut les chiffres Unicode (arabo-indis, pleine largeur) qu'un
CIN malgache n'utilise pas. Voir aussi § 5.3 pour la validation de la CIN.

### 4.5 Calculateur de charges JIRAMA

Le calculateur relie trois couches, volontairement séparées :

```
saisie (gabarit)  →  algorithme pur  →  écriture (échéance)
   JiramaForm        MoteurRepartitionJirama      JiramaService
```

`service.MoteurRepartitionJirama` est une classe **pure**, sans Spring ni base :
elle est couverte par des tests exhaustifs (arrondis, écarts négatifs, cas sans
consommation). `service.JiramaService` fait le lien avec les repositories.

#### Formule

Chaque locataire est relevé sur un sous-compteur ; sa consommation est la
différence entre l'index lu et l'index de départ de l'état des lieux
(`logement.compteur_*_index_depart`).

```
base_i        = conso_i x prix_unitaire            (par énergie)
ecart         = conso_principale x prix_unitaire - somme(base_i)
part_i        = base_i + partEcart_i
partEcart_i   = ecart x poids_i / somme(poids)     (PRORATA)
              = ecart / nombre_occupants          (EGALE)
              = 0                                 (BAILLEUR)
part_i        = part_i x facteur   si une facture JIRAMA est déclarée
```

L'écart entre le compteur principal et la somme des sous-compteurs représente
l'éclairage des parties communes, les pertes de ligne et les taxes : il n'est
imputable à personne en particulier, d'où les trois traitements offered.

| Décision | Choix et raison |
|---|---|
| **Calculer ≠ appliquer** | `POST /jirama/calculer` est une simulation, `POST /jirama/appliquer` écrit. Une erreur de relevé ne peut pas se traduire par un montant débité au locataire |
| **Type de compteur** | Relu **en base** à chaque calcul, jamais dans le formulaire : un compteur `UNIQUE` ne dessert qu'un occupant, et l'écart lui revient intégralement. Un `SOUS_COMPTEUR` ou un `PARTAGE` applique le mode choisi |
| **Énergies séparées** | L'écart est calculé puis réparti indépendamment pour l'électricité et l'eau : les deux tarifs n'ont pas le même comportement de perte |
| **Facture déclarée** | Si le bailleur saisit le montant réellement facturé (taxes, frais), les parts sont ramenées dessus par un facteur, et la somme vaut exactement la facture |
| **Arrondi** | Méthode du plus grand reste : chaque part est arrondie à l'ariary entier, le reliquat est redistribué à l'unité sur les parts les plus lourdes. La somme finale est **exactement** égale à la facture — aucun centime perdu, aucun créé |
| **Relevés incohérents** | Si la somme des sous-compteurs dépasse le compteur principal, l'écran le signale ; les parts restent positives |
| **Index de départ** | Réinjectés depuis la base à chaque calcul : un client qui gonflerait son index de départ gonflerait sa propre facture |
| **Périmètre** | Un relevé n'est retenu que s'il désigne un contrat `EN_COURS` réellement présent : un identifiant étranger ou un bail résilié est écarté |

#### Où est enregistrée la part JIRAMA

Le modèle v1 réserve `paiement_loyer` à une échéance unique par contrat et par
période (`uq_paiement_periode`) : il n'existe aucune ligne distincte pour les
charges. La part JIRAMA est donc **affectée** — et non ajoutée — à
`montant_attendu` :

```
montant_attendu = contrat.montant_loyer_mga + part JIRAMA du locataire
```

Le loyer du contrat étant la source unique de vérité, cette affectation est
**idempotente** : relancer le calcul remplace la valeur précédente au lieu de la
cumuler. Le loyer seul reste déductible, il suffit de retrancher la part
affichée par le dernier calcul. Une table `charge_jirama` dédiée serait le
correctif structurel (cf. § 7).

| Garde-fou | Effet |
|---|---|
| Échéance `PAYE` | Jamais réécrite : on ne réécrit pas un montant encaissé. L'écriture est ignorée et signalée dans le journal affiché |
| Échec partiel | L'échec sur un locataire n'interrompt pas les autres : chacun est imputé indépendamment |
| Période antérieure à 2026-01 | Refusée : la contrainte `CHECK (periode_annee >= 2026)` la rejetterait |
| Période antérieure au début du bail | Ignorée, avec le motif affiché |
| `montant_paye` et `statut` | Jamais touchés : seul le montant attendu est réécrit |


---

## 5. Couche Base de données & liaison (Spring Data JPA)

### Entités — `mg.bailtech.model`

| Entité | Table | Points d'attention |
|---|---|---|
| `Utilisateur` | `utilisateur` | CIN à 12 chiffres contrainte en Java **et** en base ; `email` unique ; locator/bailleur distingués par leurs relations, le modèle v1 n'ayant pas de colonne de rôle |
| `Logement` | `logement` | FK `id_proprietaire` en `ON DELETE RESTRICT` ; compteurs JIRAMA et index de départ ; `type_compteur_jirama` en énumération nommée ; les index de départ des compteurs servent de référence à la répartition des charges (§ 4.5) |
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

### 5.3 Validation stricte des identités

L'exigence « bloquer la validation si le champ CIN ne fait pas exactement 12
chiffres » est portée par la contrainte **`web.validation.CinNational`**, appliquée
à `Utilisateur.cinNumero` et à `ContratForm.cinNumero`.

| Caractéristique | Choix et raison |
|---|---|
| Vérification caractère par caractère | La classe regex `\d` de Java inclut les chiffres Unicode (arabo-indis, pleine largeur) qu'un CIN malgache n'utilise pas, et certains de ces points de code sont invisibles à l'écran. Seuls `0`-`9` ASCII sont acceptés |
| Champ vide **valide** | L'obligation est portée par `@NotBlank`. Empiler `@NotBlank` sur ce champ rendrait impossible la sélection d'un locataire déjà enregistré dans l'assistant de contrat |
| Défense en profondeur | `ContratService.rechercherOuCreerLocataire(...)` revérifie le format avant d'écrire : un import ou un script de reprise ne passe pas par les annotations |
| Le reste des identités | Nom, adresse, téléphone et email restent soumis à `@NotBlank`, `@Size` et `@Email` ; seul le CIN fait l'objet d'une contrainte dédiée, parce que c'est le seul champ à format fixe du modèle |

---

## 5 bis. Sécurité des identités et des documents

### 5.4 Hachage des mots de passe

`service.AuthentificationService` fournit le hachage et la vérification BCrypt,
comme l'exige `Conception_base.md` (« empreinte du mot de passe haché (BCrypt) »).

| Point | Choix et raison |
|---|---|
| Dépendance | `org.springframework.security:spring-security-crypto` **seule**. Contrairement à `spring-boot-starter-security`, ce module n'enclenche aucune auto-configuration, ne filtre aucune requête et n'exige aucun `UserDetailsService`. Ajouter le starter aurait fermé l'application sans qu'aucune page de connexion ne soit prête |
| Coût 12 | ~250 ms de hachage, le compromis habituel entre sécurité et temps de réponse d'une connexion. Configurable par `bailtech.securite.cout-bcrypt` |
| Sel par mot de passe | Deux troubles de la même valeur n'ont pas la même empreinte : les tables de pré-calculs (rainbow tables) sont inopérantes |
| Locataire créé par l'assistant | L'assistant enregistre un locataire, il ne lui ouvre pas d'accès et ne peut donc pas lui demander de choisir un mot de passe. L'empreinte posée est celle d'un **secret aléatoire** : elle n'ouvre rien, mais ce n'est plus une chaîne devinable en base (contre le placeholder `{compte-sans-authentification}` précédent) |
| Jeu de démonstration | Les fiches de démo reçoivent l'empreinte BCrypt de `Bailtech2026!` ; la valeur en clair n'existe plus dans la base, seulement sous forme de hachage à sel variable |
| Renouvellement | `changerMotDePasse(...)` vérifie l'ancien mot de passe avant d'écrire, et la longueur du nouveau est contrôlée avant tout contact avec la base |
| Journalisation | Un mot de passe n'est ni journalisé ni renvoyé par l'API. `empreinteAudit(...)` produit une trace SHA-256 tronquée, non réversible, permettant de corréler deux échecs d'authentification |

### 5.5 Coffre chiffré des pièces administratives

`service.StockageSecuriseService` chiffre les pièces du profil (CIN numérisée,
justificatif de domicile) en **AES-256-GCM** avant qu'elles n'atteignent le disque.

| Propriété | Mise en œuvre |
|---|---|
| Confidentialité au repos | Chiffrement effectué en mémoire sur le contenu reçu : rien n'est jamais écrit en clair, même temporairement |
| Intégrité | GCM est un mode authentifié. Un bit modifié — sauvegarde, retouche, altération volontaire — fait **échouer** la lecture au lieu de restituer une pièce fausse |
| IV unique par écriture | 96 bits tirés au hasard à chaque dépôt et préfixés au fichier. Réutiliser un IV sous une même clé est la faute classique qui casse GCM ; ici deux pièces identiques n'ont pas la même empreinte |
| Clé hors du dépôt | Configuration uniquement (`bailtech.securite.cle-chiffrement`, 256 bits en Base64). Aucune clé en dur ; à défaut de configuration, le service **refuse** d'écrire plutôt que de produire un fichier que l'on ne saura pas rouvrir |
| Anti-traversée | Le nom est assaini, puis la clé est comparée à la racine après résolution canonique. `../../webapps/ROOT` ne peut pas écrire hors du coffre |
| Liste blanche d'extensions | Images et PDF uniquement. Un exécutable ou une page HTML stockés dans le coffre pourraient être servis tels quels si le coffre devenait accessible par inadvertance |
| Taille maximale | 10 Mo, largement supérieur à une CIN numérisée |
| Écriture non destructive | `CREATE_NEW` refuse d'écraser, ce qui ferme la porte à une collision de clé comme à un lien symbolique préparé en amont |

```bash
# Générer une clé (à placer dans une variable d'environnement, jamais dans Git)
# puis : export BAILTECH_CLE_CHIFFREMENT=<clé>
```

Le dépôt `bailtech.securite.coffre` par défaut (`./coffre-securise`) est hors de
la racine servie et du dossier des gabarits, donc jamais exposé par une requête
HTTP. En développement, laisser la clé vide fait journaliser un avertissement au
démarrage et le coffre refuse toute écriture.

---

## 6. Câblage Thymeleaf

Les sept gabarits du dossier `template/` sont reliés aux données, dont
`jirama/jirama.html` pour le calculateur de charges (§ 4.5) :

| Attribut | Emploi |
|---|---|
| `th:object="${contratForm}"` | objet de formulaire de l'assistant de contrat |
| `th:field="*{…}"` | 17 champs liés aux colonnes de `contrat_de_bail` et `utilisateur` |
| `th:action` / `th:method` | soumission vers `POST /contrats`, formulaires de recherche en `GET`, signature vers `POST /contrats/{id}/signature` |
| `th:each` | listes déroulantes (logements disponibles, locataires disponibles, compteurs) et tableaux (contrats, échéancier, dossiers, sous-compteurs) |
| `th:errors` | 17 messages de validation rattachés à leur champ |
| `th:text` | affichage des agrégats, des libellés d'énumération et de l'aperçu du contrat |
| `th:href` | navigation entre les pages, liens vers le PDF et son téléchargement |
| `th:classappend` | pastilles de statut sans perdre les classes Tailwind |

Les listes de sélection ne sont jamais figées dans le gabarit : elles proviennent
des repositories, si bien qu'un logement déjà sous contrat `EN_COURS` n'apparaît
jamais dans la liste déroulante.

Les gabarits du site sont en mode `HTML` ; `contrat/contrat-pdf.html` est le seul
en mode `XML`, réservé au moteur d'impression (voir § 4.1).

### Jeu de démonstration

Au démarrage, si la table `utilisateur` est vide, `config.DonneesDemo` injecte
4 utilisateurs, 4 logements, 4 contrats (dont un `EN_ATTENTE_SIGNATURE`) et
6 échéances, afin que les listes `th:each` et les agrégats du tableau de bord
aient des données. Les périodes générées ne redescendent jamais avant 2026-01,
conformément à la contrainte SQL. Les mots de passe sont stockés sous forme
d'empreintes BCrypt, jamais en clair (§ 5.4). Désactivation :
`BAILTECH_DEMO=false`.

---

## 7. Limites connues du MVP

* **Authentification absente** : le hachage des mots de passe est en place
  (§ 5.4) mais **aucune page de connexion n'existe**. Le bailleur courant est
  déduit du premier propriétaire enregistré, ou du paramètre `?bailleurId=`.
  `ContratService.utilisateurCourant(...)` est le point à remplacer par la
  lecture de la session. `AuthentificationService.authentifier(cin, motDePasse)`
  est prêt à être appelé par ce futur contrôleur.
* **Part JIRAMA confondue avec le loyer** : faute de table dédiée,
  `montant_attendu` porte « loyer + charges JIRAMA » pour les périodes calculées
  (§ 4.5). L'affectation est idempotente et le loyer seul reste déductible, mais
  une table `charge_jirama` serait le correctif structurel.
* **Relevés JIRAMA non persistés** : les index saisis sont recalculables mais
  pas archivés. Un historique des factures imputées manque pour l'audit.
* **Pas d'inventaire des pièces justificatives** : le coffre chiffré (§ 5.5) est
  opérationnel, mais le modèle v1 n'a **aucune table de pièces jointes** — les
  métadonnées (type, propriétaire, date de dépôt) n'ont pas d'endroit où être
  enregistrées, et aucun point de terminaison de téléversement n'est exposé. La
  brique de stockage est prête à être branchée par le module « documents ».
* **Quittances de paiement** : le PDF du bail est produit, pas encore celui des
  quittances mensuelles (à déclencher sur validation d'un règlement).
* **Polices du PDF limitées à Latin-1** : les identités en alphabets non latins
  ne s'afficheraient pas correctement. Un embarquement de police TrueType est
  nécessaire pour les couvrir.
