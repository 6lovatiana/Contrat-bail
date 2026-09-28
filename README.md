# Contrat-bail

Application de gestion des contrats de bail avec Spring Boot, Thymeleaf et PostgreSQL.

## Lancer avec Docker

Prérequis : Docker et Docker Compose.

Depuis la racine du projet :

```bash
docker compose up --build
```

Puis ouvrir [http://localhost:8080/](http://localhost:8080/).

Pour arrêter les conteneurs :

```bash
docker compose down
```

Pour supprimer aussi les données PostgreSQL :

```bash
docker compose down -v
```

Le script `sql/240920260828-database_init.sql` est exécuté automatiquement lors de la première création du volume PostgreSQL.

## Parcours MVP

- `GET /contrats/create-wizard` : ouvre le générateur de contrat.
- `POST /enregistrer` : valide et enregistre un contrat. Les dates incohérentes sont refusées.
- `GET /contrats/{id}` : affiche la visionneuse du contrat et ses échéances.
- `GET /contrats/{id}/pdf` : télécharge le contrat PDF de deux pages.
- `GET /dashboard` : affiche le tableau de bord.

Lorsqu'un contrat est enregistré avec le statut `EN_COURS`, une échéance `A_PAYER` est créée pour chaque mois compris entre les dates de début et de fin. La génération est idempotente : les échéances existantes ne sont pas dupliquées.