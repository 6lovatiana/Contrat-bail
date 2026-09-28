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